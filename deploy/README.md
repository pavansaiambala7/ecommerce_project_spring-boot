# Deploying to AWS with Jenkins

This is the one-time setup for a Jenkins-driven pipeline that builds this app, pushes it to Amazon ECR, and deploys it to a single EC2 instance running the existing `docker-compose.yml` pattern.

**Shape of the system:**

```
GitHub push
    │
    ▼
Jenkins EC2 ──build & test (mvn)──▶ docker build ──▶ push ──▶ Amazon ECR
    │                                                              │
    └──ssh────────────────────────────────────────────────────────┘
                                                                    ▼
                                                    App EC2 pulls image, restarts,
                                                    smoke-tests, rolls back on failure
                                                                    │
                                                                    ▼
                                                        App EC2 ◀──▶ Postgres+pgvector
                                                        (same box, docker-compose)
```

Two EC2 instances, one ECR repository, three SSM parameters, two IAM roles. No RDS, no ALB, no NAT gateway — kept to what a `t3.micro`/`t3.small` and the always-free or near-free tier covers.

Everything below is AWS Console + CLI steps you run once. I can't execute any of this myself — there's no AWS CLI or credentials in the environment I work from — so this is the plan to run, not something already applied.

---

## 1. Networking (default VPC is fine)

Most accounts still have a default VPC; this doesn't need a custom one.

Create two security groups:

**`jenkins-sg`**
| Type | Port | Source |
|---|---|---|
| SSH | 22 | your IP only |
| Custom TCP | 8080 | your IP only |

**`app-sg`**
| Type | Port | Source |
|---|---|---|
| HTTP | 80 | `0.0.0.0/0` (it's the public storefront) |
| SSH | 22 | `jenkins-sg` (reference the security group, not an IP) |

The app's Postgres container is never exposed — `docker-compose.prod.yml` publishes no port for `db`, so there's nothing to open for it.

## 2. IAM roles

Two roles, each scoped to only what its instance needs — see the policy JSON in this directory.

```bash
# Jenkins role: can push to this one ECR repo, nothing else
aws iam create-role --role-name jenkins-ec2-role \
  --assume-role-policy-document file://deploy/iam/ec2-trust-policy.json
aws iam put-role-policy --role-name jenkins-ec2-role \
  --policy-name ecr-push --policy-document file://deploy/iam/jenkins-instance-policy.json
aws iam create-instance-profile --instance-profile-name jenkins-ec2-profile
aws iam add-role-to-instance-profile --instance-profile-name jenkins-ec2-profile --role-name jenkins-ec2-role

# App role: can pull from that repo and read only its own SSM parameters
aws iam create-role --role-name app-ec2-role \
  --assume-role-policy-document file://deploy/iam/ec2-trust-policy.json
aws iam put-role-policy --role-name app-ec2-role \
  --policy-name ecr-pull-and-ssm-read --policy-document file://deploy/iam/prod-instance-policy.json
aws iam create-instance-profile --instance-profile-name app-ec2-profile
aws iam add-role-to-instance-profile --instance-profile-name app-ec2-profile --role-name app-ec2-role
```

`deploy/iam/ec2-trust-policy.json` (the standard EC2 assume-role trust document) is in this directory too.

Neither instance ever holds a static AWS access key. This is the whole reason Jenkins doesn't need an AWS credential of any kind — the CLI on each box picks up temporary credentials from the instance metadata service automatically.

## 3. ECR repository

```bash
aws ecr create-repository --repository-name ecommerce-app --region us-east-1
```

## 4. SSM parameters (the app's runtime secrets)

```bash
aws ssm put-parameter --name /ecommerce/jwt-secret --type SecureString \
  --value "$(openssl rand -base64 48)"

aws ssm put-parameter --name /ecommerce/db-password --type SecureString \
  --value "$(openssl rand -base64 24)"

aws ssm put-parameter --name /ecommerce/gemini-api-key --type SecureString \
  --value "your-gemini-api-key"   # optional — AI features stay inert without it
```

These never pass through Jenkins. `deploy/fetch-secrets.sh` reads them directly on the app host at deploy time, using that host's own IAM role.

## 5. The app EC2 instance

Launch one instance (Amazon Linux 2023, `t3.small` is a safe minimum — the app + Postgres both need headroom):

- Security group: `app-sg`
- IAM instance profile: `app-ec2-profile`
- An Elastic IP, so `PROD_HOST` doesn't change on stop/start

Then, one time, over SSH:

```bash
sudo dnf install -y docker
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user
# aws-cli v2 ships preinstalled on Amazon Linux 2023; verify with: aws --version
sudo mkdir -p /opt/ecommerce/deploy
sudo chown ec2-user:ec2-user /opt/ecommerce -R
```

Docker Compose v2 ships as the `docker compose` plugin on recent Docker packages; if `docker compose version` doesn't resolve, install the `docker-compose-plugin` package for your AMI.

Log out and back in after `usermod` so the group membership takes effect.

## 6. The Jenkins EC2 instance

Launch a second instance (`t3.small` is comfortable for Maven + Docker builds):

- Security group: `jenkins-sg`
- IAM instance profile: `jenkins-ec2-profile`

Install Java 17, Docker, and Jenkins itself:

```bash
sudo dnf install -y java-17-amazon-corretto docker git
sudo systemctl enable --now docker
sudo usermod -aG docker jenkins   # after Jenkins is installed, below

sudo wget -O /etc/yum.repos.d/jenkins.repo https://pkg.jenkins.io/redhat-stable/jenkins.repo
sudo rpm --import https://pkg.jenkins.io/redhat-stable/jenkins.repo.key
sudo dnf install -y jenkins
sudo systemctl enable --now jenkins
```

Visit `http://<jenkins-host>:8080`, unlock with `sudo cat /var/lib/jenkins/secrets/initialAdminPassword`, and install the suggested plugins plus:

- **SSH Agent** (the `sshagent` step the Jenkinsfile uses)
- **Pipeline** (usually already in the suggested set)

Restart Jenkins after `usermod -aG docker jenkins` (`sudo systemctl restart jenkins`) so its process picks up Docker group membership.

## 7. Jenkins credentials

Only two are needed — everything else is IAM instance roles, deliberately.

| ID | Type | Value |
|---|---|---|
| `jwt-secret` | Secret text | A JWT secret for the **test** profile only — this is not the production one from SSM, just what `mvn verify` needs to run |
| `gemini-api-key` | Secret text | Same idea — a test-time value; can be a dummy string, since tests never call the real Gemini API |
| `prod-ec2-ssh-key` | SSH Username with private key | The private key half of the app EC2 instance's key pair; username `ec2-user` |

## 8. The pipeline job

New Item → Pipeline → "Pipeline script from SCM" → point it at this repository and the `Jenkinsfile` at the repo root. Set the job parameters (or accept the Jenkinsfile's defaults) for `AWS_REGION`, `ECR_REPOSITORY`, and set `PROD_HOST` to the app instance's Elastic IP.

Run it. The five original stages behave exactly as before; three new ones follow:

1. **Build Docker Image** — tags with the short git commit SHA and `latest`
2. **Push to ECR** — auths via the Jenkins instance role, no stored keys
3. **Deploy to Production** — copies `docker-compose.prod.yml` and the `deploy/` scripts to the app host over SSH, then runs `deploy.sh` there

`deploy.sh` fetches secrets from SSM, pulls the new image, restarts the stack, and curls `/login` in a retry loop as a smoke test. If that never returns `200`, it automatically redeploys whatever image last passed and fails the Jenkins build — so a bad push doesn't take the site down, but you still get a red build to notice.

---

## What this doesn't cover

- **TLS / a domain.** The app is reachable over plain HTTP on the Elastic IP. Adding a domain means a Route 53 record; adding HTTPS on a single EC2 box without a load balancer usually means fronting it with Caddy or Nginx + Certbot in the compose file. Worth doing before this is anything but a demo — ask if you want that added.
- **Zero-downtime deploys.** `docker compose up -d` briefly stops the old container before the new one is healthy. Fine for a single low-traffic box; a real zero-downtime setup wants at least two app instances behind a load balancer, which is the ECS Fargate path this plan deliberately avoided for cost.
- **Database backups.** The Postgres data lives in a named Docker volume on the app instance. Nothing here snapshots it. At minimum, put the EBS volume backing that instance on a scheduled AWS Backup plan.
