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

Everything below is AWS CLI steps you run once, from **AWS CloudShell** (the terminal icon in the console's bottom-left — it's already authenticated as your IAM user, no local setup needed). CloudShell is a fresh environment scoped to your account, not your own machine, so the first thing to do there is clone this repo so the relative `file://deploy/iam/...` paths below resolve:

```bash
git clone https://github.com/pavansaiambala7/ecommerce_project_spring-boot.git
cd ecommerce_project_spring-boot
```

Then set these once per CloudShell session — everything below reuses them:

```bash
export AWS_REGION=eu-north-1      # match whatever region you see top-right in the console
export AWS_DEFAULT_REGION=$AWS_REGION
```

I can't execute any of this myself — there's no AWS CLI or credentials in the environment I work from — so this is the plan to run, not something already applied.

---

## 1. Networking (default VPC is fine)

Confirm you have a default VPC in this region — new-ish accounts get one automatically, but check rather than assume:

```bash
aws ec2 describe-vpcs --filters Name=is-default,Values=true --query 'Vpcs[0].VpcId' --output text
```

If that prints `None`, create one: `aws ec2 create-default-vpc`.

Create the two security groups:

```bash
VPC_ID=$(aws ec2 describe-vpcs --filters Name=is-default,Values=true --query 'Vpcs[0].VpcId' --output text)
MY_IP=$(curl -s https://checkip.amazonaws.com)/32

JENKINS_SG=$(aws ec2 create-security-group --group-name jenkins-sg \
  --description "Jenkins host" --vpc-id "$VPC_ID" --query GroupId --output text)
aws ec2 authorize-security-group-ingress --group-id "$JENKINS_SG" \
  --ip-permissions IpProtocol=tcp,FromPort=22,ToPort=22,IpRanges="[{CidrIp=$MY_IP}]" \
                    IpProtocol=tcp,FromPort=8080,ToPort=8080,IpRanges="[{CidrIp=$MY_IP}]"

APP_SG=$(aws ec2 create-security-group --group-name app-sg \
  --description "App host" --vpc-id "$VPC_ID" --query GroupId --output text)
aws ec2 authorize-security-group-ingress --group-id "$APP_SG" \
  --ip-permissions IpProtocol=tcp,FromPort=80,ToPort=80,IpRanges="[{CidrIp=0.0.0.0/0}]"
aws ec2 authorize-security-group-ingress --group-id "$APP_SG" \
  --ip-permissions IpProtocol=tcp,FromPort=22,ToPort=22,UserIdGroupPairs="[{GroupId=$JENKINS_SG}]"

echo "JENKINS_SG=$JENKINS_SG"
echo "APP_SG=$APP_SG"
```

`MY_IP` picks up CloudShell's own outbound IP, which changes between sessions — if SSH/8080 access stops working later, re-run that `MY_IP` line and update the security group rule (`aws ec2 revoke-security-group-ingress` the old one, `authorize` the new one), or just use the EC2 console's "My IP" helper when editing the rule by hand.

The app's Postgres container is never exposed — `docker-compose.prod.yml` publishes no port for `db`, so there's nothing to open for it.

**Note the two group IDs printed above (`JENKINS_SG`, `APP_SG`) — you'll pass them to `aws ec2 run-instances` in step 5.**

## 2. IAM roles

Two roles, each scoped to only what its instance needs:

- **`jenkins-ec2-role`** — can push to the `ecommerce-app` ECR repository and nothing else. This is the whole reason Jenkins never needs a static AWS access key: the CLI on that instance picks up temporary credentials from the instance metadata service automatically.
- **`app-ec2-role`** — read-only ECR pull, plus read-only access to this app's own `/ecommerce/*` SSM parameters. It can never read or write anything outside that path.

Both trust `deploy/iam/ec2-trust-policy.json` — the standard EC2 assume-role document, which only says "an EC2 instance may assume this role"; the actual permissions come from the two policies above, attached alongside it.

> **If you hit `MalformedPolicyDocument: Unknown field ...`:** `git pull` first. An earlier version of these three JSON files had a `"Comment"` field at the top for documentation — harmless in most JSON, but IAM's policy parser accepts only a fixed set of top-level keys (`Version`, `Statement`, optionally `Id`) and rejects anything else outright. That's fixed now; if you created any of the four roles/profiles before pulling the fix, re-running the block below is safe — `EntityAlreadyExists` on `create-role`/`create-instance-profile` just means that part already exists, and `put-role-policy`/`add-role-to-instance-profile` will succeed now that the role itself was actually created.

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
aws ecr create-repository --repository-name ecommerce-app --region "$AWS_REGION"
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

One key pair, shared by both instances (download the `.pem` CloudShell hands back — it's the only copy):

```bash
aws ec2 create-key-pair --key-name ecommerce-deploy-key \
  --query 'KeyMaterial' --output text > ecommerce-deploy-key.pem
chmod 400 ecommerce-deploy-key.pem
```

Look up the latest Amazon Linux 2023 AMI for your region (avoids hardcoding a region-specific AMI ID) and a default subnet to launch into:

```bash
AMI_ID=$(aws ssm get-parameters --names /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64 \
  --query 'Parameters[0].Value' --output text)
SUBNET_ID=$(aws ec2 describe-subnets --filters Name=vpc-id,Values=$VPC_ID \
  --query 'Subnets[0].SubnetId' --output text)
```

Launch it (Amazon Linux 2023, `t3.small` is a safe minimum — the app + Postgres both need headroom):

```bash
APP_INSTANCE_ID=$(aws ec2 run-instances --image-id "$AMI_ID" --instance-type t3.small \
  --key-name ecommerce-deploy-key --security-group-ids "$APP_SG" --subnet-id "$SUBNET_ID" \
  --iam-instance-profile Name=app-ec2-profile \
  --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=ecommerce-app}]' \
  --query 'Instances[0].InstanceId' --output text)

aws ec2 wait instance-running --instance-ids "$APP_INSTANCE_ID"

# Elastic IP, so PROD_HOST never changes across stop/start
APP_EIP_ALLOC=$(aws ec2 allocate-address --query AllocationId --output text)
aws ec2 associate-address --instance-id "$APP_INSTANCE_ID" --allocation-id "$APP_EIP_ALLOC"
APP_PUBLIC_IP=$(aws ec2 describe-addresses --allocation-ids "$APP_EIP_ALLOC" --query 'Addresses[0].PublicIp' --output text)
echo "App host: $APP_PUBLIC_IP  <- this is your Jenkinsfile PROD_HOST parameter"
```

`app-sg`'s SSH rule only allows `jenkins-sg` as a source (step 1) — correct for Jenkins' automated deploys later, but it means *you* can't SSH in directly yet from CloudShell or your own machine. Open it to your current IP too, the same way you did for `jenkins-sg`:

```bash
MY_IP=$(curl -s https://checkip.amazonaws.com)/32
aws ec2 authorize-security-group-ingress --group-id "$APP_SG" \
  --ip-permissions IpProtocol=tcp,FromPort=22,ToPort=22,IpRanges="[{CidrIp=$MY_IP}]"
```

SSH in (`ssh -i ecommerce-deploy-key.pem ec2-user@$APP_PUBLIC_IP` from CloudShell, or copy the `.pem` to wherever you'll actually run Jenkins deploys from) and, one time:

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

Launch a second instance (`t3.small` is comfortable for Maven + Docker builds), reusing the same key pair, AMI lookup, and subnet from step 5:

```bash
JENKINS_INSTANCE_ID=$(aws ec2 run-instances --image-id "$AMI_ID" --instance-type t3.small \
  --key-name ecommerce-deploy-key --security-group-ids "$JENKINS_SG" --subnet-id "$SUBNET_ID" \
  --iam-instance-profile Name=jenkins-ec2-profile \
  --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=ecommerce-jenkins}]' \
  --query 'Instances[0].InstanceId' --output text)

aws ec2 wait instance-running --instance-ids "$JENKINS_INSTANCE_ID"
JENKINS_PUBLIC_IP=$(aws ec2 describe-instances --instance-ids "$JENKINS_INSTANCE_ID" \
  --query 'Reservations[0].Instances[0].PublicIpAddress' --output text)
echo "Jenkins host: $JENKINS_PUBLIC_IP"
```

No Elastic IP needed here — you'll only ever open this by IP occasionally, unlike the app host which needs a stable address for the pipeline's `PROD_HOST` parameter. If you stop/start this instance, re-check the public IP with the `describe-instances` command above.

SSH in (`ssh -i ecommerce-deploy-key.pem ec2-user@$JENKINS_PUBLIC_IP`) and install Java 17, Docker, and Jenkins itself, **in this order** — Jenkins has to exist before you can add its user to the `docker` group:

```bash
sudo dnf install -y java-17-amazon-corretto docker git
sudo systemctl enable --now docker

sudo curl -fsSL -o /etc/yum.repos.d/jenkins.repo https://pkg.jenkins.io/redhat-stable/jenkins.repo
sudo rpm --import https://pkg.jenkins.io/redhat-stable/jenkins.repo.key
sudo dnf install -y jenkins

sudo usermod -aG docker jenkins
sudo systemctl enable --now jenkins
sudo systemctl restart jenkins   # picks up the docker group membership
```

Visit `http://<jenkins-public-ip>:8080`, unlock with `sudo cat /var/lib/jenkins/secrets/initialAdminPassword`, and install the suggested plugins plus:

- **SSH Agent** (the `sshagent` step the Jenkinsfile uses)
- **Pipeline** (usually already in the suggested set)

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
