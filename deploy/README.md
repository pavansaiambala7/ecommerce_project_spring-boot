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

The last rule above lets Jenkins SSH to the app host by naming `jenkins-sg` as the *source group* rather than an IP, so it never goes stale the way `MY_IP` does. It comes with one condition that is easy to trip over: **AWS only applies a source-group reference to traffic arriving on the target's private IP.** If Jenkins connects to the app host's Elastic IP instead, the packet leaves through the internet gateway and comes back with Jenkins' *public* IP as its source, which this rule does not match — the connection then hangs until it times out. That is why the pipeline's `PROD_HOST` parameter defaults to the app instance's **private** IP (`aws ec2 describe-instances --filters "Name=tag:Name,Values=ecommerce-app" --query 'Reservations[0].Instances[0].PrivateIpAddress' --output text`). The Elastic IP is only for browsing the app on port 80.

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

# Elastic IP, so the address you browse the app on never changes across stop/start
APP_EIP_ALLOC=$(aws ec2 allocate-address --query AllocationId --output text)
aws ec2 associate-address --instance-id "$APP_INSTANCE_ID" --allocation-id "$APP_EIP_ALLOC"
APP_PUBLIC_IP=$(aws ec2 describe-addresses --allocation-ids "$APP_EIP_ALLOC" --query 'Addresses[0].PublicIp' --output text)
APP_PRIVATE_IP=$(aws ec2 describe-instances --instance-ids "$APP_INSTANCE_ID" \
  --query 'Reservations[0].Instances[0].PrivateIpAddress' --output text)
echo "Browse the app at: http://$APP_PUBLIC_IP"
echo "Jenkinsfile PROD_HOST parameter: $APP_PRIVATE_IP  <- private IP, see step 1"
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

# Docker Compose v2. Amazon Linux 2023's `docker` package does NOT include it
# and there is no docker-compose-plugin package in its repos, so install the
# plugin binary directly. Without this, deploy.sh dies on `docker compose -f`
# with a confusing top-level "unknown shorthand flag: 'f'" from the Docker CLI.
sudo mkdir -p /usr/libexec/docker/cli-plugins
sudo curl -sSL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o /usr/libexec/docker/cli-plugins/docker-compose
sudo chmod +x /usr/libexec/docker/cli-plugins/docker-compose
docker compose version   # must print a v2.x version before you go further

sudo mkdir -p /opt/ecommerce/deploy
sudo chown ec2-user:ec2-user /opt/ecommerce -R
```

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

No Elastic IP needed here — you'll only ever open this by IP occasionally, unlike the app host whose Elastic IP is the stable address people browse the app on. If you stop/start this instance, re-check the public IP with the `describe-instances` command above.

SSH in (`ssh -i ecommerce-deploy-key.pem ec2-user@$JENKINS_PUBLIC_IP`) and install Java, Docker, and Jenkins itself, **in this order** — Jenkins has to exist before you can add its user to the `docker` group:

```bash
sudo dnf install -y java-21-amazon-corretto docker git
sudo systemctl enable --now docker

sudo curl -fsSL -o /etc/yum.repos.d/jenkins.repo https://pkg.jenkins.io/redhat-stable/jenkins.repo
# No separate `rpm --import` for the signing key: the .repo file's own gpgkey=
# line points at the current key URL, and dnf imports it automatically during
# the install below. An older guide had a hardcoded key URL here that 404s
# now that Jenkins restructured their package repo layout - dnf handling it
# itself is more resilient to exactly that kind of drift.
sudo dnf install -y jenkins

# Current Jenkins requires Java 21+ (it will fail to start on 17 with
# "Running with Java 17 ... older than the minimum required version" in the
# journal). Pin it explicitly rather than relying on whichever `java`
# `alternatives` happens to default to.
JAVA21_HOME=$(ls -d /usr/lib/jvm/java-21-amazon-corretto* | head -1)
echo "JENKINS_JAVA_CMD=\"$JAVA21_HOME/bin/java\"" | sudo tee -a /etc/sysconfig/jenkins

sudo usermod -aG docker jenkins
sudo systemctl enable --now jenkins
sudo systemctl restart jenkins   # picks up the docker group membership
```

If Jenkins still won't start, `sudo systemctl status jenkins --no-pager -l` and `sudo journalctl -xeu jenkins.service --no-pager | tail -60` show the real reason — Jenkins' own Java-version check logs plainly to the journal, so this is usually enough to diagnose directly rather than guess.

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

New Item → Pipeline → "Pipeline script from SCM" → point it at this repository and the `Jenkinsfile` at the repo root. Set the job parameters (or accept the Jenkinsfile's defaults) for `AWS_REGION`, `ECR_REPOSITORY`, and set `PROD_HOST` to the app instance's **private** IP (not its Elastic IP — see step 1 for why).

Note that when a job loads its pipeline via "Pipeline script from SCM", Jenkins re-syncs the job's parameter defaults from the `Jenkinsfile` on every run. Editing a default in the job's UI alone will not stick — change it in the `Jenkinsfile` and push.

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

## 9. Database backups

The database lives in a Docker volume on a single instance. Without backups,
losing that instance destroys every user, order and payment permanently.

Create the bucket once, from CloudShell. Versioning is on so that an
overwrite or a delete is itself recoverable; public access is blocked because
the objects contain every customer record you hold.

```bash
export AWS_DEFAULT_REGION=eu-north-1
BUCKET=ecommerce-db-backups-$(aws sts get-caller-identity --query Account --output text)

aws s3api create-bucket --bucket "$BUCKET" \
  --create-bucket-configuration LocationConstraint=$AWS_DEFAULT_REGION
aws s3api put-bucket-versioning --bucket "$BUCKET" \
  --versioning-configuration Status=Enabled
aws s3api put-public-access-block --bucket "$BUCKET" \
  --public-access-block-configuration \
  "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"
aws s3api put-bucket-encryption --bucket "$BUCKET" \
  --server-side-encryption-configuration \
  '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'

echo "$BUCKET"
```

Then refresh the app host's IAM policy, which now also grants S3 access to this
one bucket. The bucket name is hard-coded in `deploy/iam/prod-instance-policy.json`
- change it there if your account id differs.

```bash
aws iam put-role-policy --role-name app-ec2-role \
  --policy-name ecr-pull-and-ssm-read \
  --policy-document file://deploy/iam/prod-instance-policy.json
```

Schedule it on the app host. 02:00 UTC is outside Indian shopping hours:

```bash
ssh -i "$KEY" ec2-user@"$APP"
sudo mkdir -p /opt/ecommerce/deploy && sudo chown -R ec2-user: /opt/ecommerce
# backup.sh and restore.sh are shipped by the pipeline alongside deploy.sh
( crontab -l 2>/dev/null; \
  echo "0 2 * * * /opt/ecommerce/deploy/backup.sh ecommerce-db-backups-792026110282 >> /var/log/ecommerce-backup.log 2>&1" \
) | crontab -
crontab -l
```

Verify immediately rather than waiting for 02:00 - a backup job nobody has
watched run is an assumption, not a backup:

```bash
/opt/ecommerce/deploy/backup.sh ecommerce-db-backups-792026110282
aws s3 ls s3://ecommerce-db-backups-792026110282/daily/
```

**Restoring.** `restore.sh` downloads a dump, asks you to type the database name
to confirm, and replaces the current contents:

```bash
/opt/ecommerce/deploy/restore.sh ecommerce-db-backups-792026110282 latest
```

Practise this against a scratch database before you need it. A restore
procedure that has never been run is not a recovery plan.

## 10. Health monitoring and outage alerts

`/actuator/health` is exposed; everything else is not. The default Actuator set
includes `env`, `beans` and `configprops`, which publish the resolved
configuration - including property values - to anyone who asks.

Two probes, answering different questions:

| Probe | Question | Includes the database? |
|---|---|---|
| `/actuator/health/liveness` | Is this JVM broken? | No |
| `/actuator/health/readiness` | Can it serve traffic? | Yes |

The split matters. When the database goes down, readiness reports `503 DOWN`
while liveness stays `200 UP` - the app should stop receiving traffic, but
restarting the JVM would not fix anything. Both the container healthcheck and
`deploy.sh` now check readiness, so a deploy whose database connection failed
is rolled back instead of promoted.

Create an SNS topic and subscribe to it (confirm the emailed link):

```bash
export AWS_DEFAULT_REGION=eu-north-1
TOPIC_ARN=$(aws sns create-topic --name ecommerce-alerts --query TopicArn --output text)
aws sns subscribe --topic-arn "$TOPIC_ARN" --protocol email --notification-endpoint you@example.com
echo "$TOPIC_ARN"
```

Grant the app host permission to publish, then schedule the check:

```bash
aws iam put-role-policy --role-name app-ec2-role --policy-name publish-alerts \
  --policy-document "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Action\":\"sns:Publish\",\"Resource\":\"$TOPIC_ARN\"}]}"

ssh -i "$KEY" ec2-user@"$APP"
( crontab -l 2>/dev/null; \
  echo "*/5 * * * * /opt/ecommerce/deploy/healthcheck.sh $TOPIC_ARN >> /var/log/ecommerce-health.log 2>&1" \
) | crontab -
```

It alerts on the second consecutive failure, not the first: a deploy restarts
the container for about 30 seconds, and an alert that fires on every deploy is
one that gets muted - at which point it protects nothing. It sends one message
when the outage starts and one when it recovers, never a message per check.

## 11. Razorpay payments

Payments were simulated: the service generated a random UUID and declared
every payment successful. This replaces that with a real gateway.

Store the credentials. The key id is public and reaches the browser; the two
secrets never leave the server:

```bash
export AWS_DEFAULT_REGION=eu-north-1

aws ssm put-parameter --name /ecommerce/razorpay-key-id \
  --value "rzp_test_xxxxxxxxxxxx" --type SecureString --overwrite
aws ssm put-parameter --name /ecommerce/razorpay-key-secret \
  --value "YOUR_KEY_SECRET" --type SecureString --overwrite
```

The webhook secret is a value you choose and then enter in the Razorpay
dashboard. Generate one rather than inventing it by hand:

```bash
aws ssm put-parameter --name /ecommerce/razorpay-webhook-secret \
  --value "$(openssl rand -base64 32)" --type SecureString --overwrite

# You will need to read it once to paste into the dashboard
aws ssm get-parameter --name /ecommerce/razorpay-webhook-secret \
  --with-decryption --query 'Parameter.Value' --output text
```

In the Razorpay dashboard, under Settings → Webhooks, add:

| Field | Value |
|---|---|
| URL | `http://13.50.19.252/api/payments/razorpay/webhook` |
| Secret | the value printed above |
| Events | `payment.captured`, `payment.failed`, `order.paid`, `refund.processed` |

Redeploy so the host picks the secrets up, then confirm the storefront can
see the gateway:

```bash
curl -s http://13.50.19.252/api/payments/razorpay/config
# {"data":{"enabled":true,"keyId":"rzp_test_..."}}
```

`enabled: false` means the parameters were not read - check the app host's
IAM role still grants `ssm:GetParameter` on `/ecommerce/*`.

**Test cards.** In test mode Razorpay accepts `4111 1111 1111 1111` with any
future expiry and any CVV. UPI succeeds with `success@razorpay`.

**Webhooks and HTTP.** Razorpay will deliver to a plain HTTP endpoint, but the
payload crosses the internet unencrypted and the signature is the only thing
proving it is genuine. This is one more reason TLS matters before taking real
payments; the signature check stops forgery, not eavesdropping.

**What is verified where.** The browser reports a payment id and a signature;
the server recomputes that signature with the key secret, which the browser
never sees, so neither the amount nor the outcome can be tampered with. The
webhook is the authoritative confirmation, because a browser can be closed
between paying and reporting back. Both paths settle the same payment row and
whichever arrives first wins.

## 12. Loading the 50,000-product catalogue

The catalogue is generated, not stored in the repository: fifty thousand rows
would bloat every clone and slow every test run. Generating it needs Python;
loading it needs nothing but curl.

```bash
cd tools
pip install faker
python generate_catalogue.py --out catalogue.csv --count 50000
gzip -kf catalogue.csv          # 12 MB becomes about 1.4 MB
```

Upload it straight to the running application - no SSH, no psql, no security
group changes:

```bash
HOST=http://13.50.19.252
TOKEN=$(curl -s -X POST $HOST/api/auth/login -H 'Content-Type: application/json' \
        -d '{"username":"admin","password":"YOUR_ADMIN_PASSWORD"}' \
        | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

curl -X POST "$HOST/api/admin/catalogue/import?embed=true" \
     -H "Authorization: Bearer $TOKEN" \
     -H 'Content-Type: application/gzip' \
     --data-binary @catalogue.csv.gz
```

Administrators can do the same from **Admin -> Catalogue import** in the
storefront. Either way it reports what happened:

```json
{"rowsRead":50000,"inserted":50000,"updated":0,
 "skippedUnknownDepartment":0,"skippedInvalid":0,"awaitingEmbedding":50096}
```

Fifty thousand rows import in about ten seconds. Products are matched on
`external_id`, so running it again updates rather than duplicates, and only
products whose text actually changed lose their embedding.

### Embeddings, and why they take hours

`embed=true` starts a background job; watch it at **Admin -> Catalogue import**
or:

```bash
curl -s "$HOST/api/admin/catalogue/embeddings" -H "Authorization: Bearer $TOKEN"
```

Gemini's free tier allows **100 embedding requests per minute**, and every
product in a batch counts as one request. Fifty thousand products is therefore
about **eight hours** of mostly waiting. The job expects this: a quota error
pauses it for a minute and it carries on, progress is the vectors already in
the database, and stopping or redeploying loses nothing - starting it again
continues from where it stopped.

Two ways to make it quick instead:

* **Enable billing** on the Gemini API. The paid tier's limits are far higher,
  and fifty thousand products is roughly three million tokens - cents, not
  dollars, at current embedding prices.
* **Embed nothing, or only part.** Search still works without embeddings: the
  keyword half of the hybrid search answers on its own, and rows without a
  vector are simply not offered by the semantic half. Semantic queries
  ("something to keep tea hot") only work once the rows are embedded.

### What the search does while the catalogue is half-embedded

Nearest-neighbour search always returns *something*, so with only part of the
catalogue embedded the nearest vector to "saree" was Motichoor Ladoo. Matches
beyond `app.search.max-vector-distance` (0.40 by default, measured against this
catalogue) are dropped, so a query with no real semantic match falls back to
keywords rather than showing whatever happened to be closest.

## 13. Building on GitHub Actions instead of Jenkins

The Jenkins host is a 2 GB t3.small that also runs Jenkins. Building this image
needs Node, Maven and the Docker daemon at the same time, and it could not do
both: builds hung for the best part of an hour and no image reached ECR for
three days, while the same build takes about two minutes elsewhere. Resizing
the instance is blocked on a free account plan.

So the build moves to GitHub's runners - free and unlimited for a public
repository - and the server goes back to only pulling and running an image.
`.github/workflows/deploy.yml` does the whole job: test, build, push to ECR,
then run `deploy.sh` on the app host.

Two things make this simpler than the Jenkins pipeline it replaces:

- **No AWS keys.** The workflow assumes an IAM role through GitHub's OIDC
  provider, and the role's trust policy only accepts tokens from this
  repository's `main` branch.
- **No SSH.** Deployment goes through Systems Manager, so the app host needs no
  port 22 rule, no key pair and no `known_hosts` entry - the three things that
  broke deploys here before.

### One-time AWS setup (CloudShell)

```bash
export AWS_PAGER="" AWS_DEFAULT_REGION=eu-north-1
ACCT=$(aws sts get-caller-identity --query Account --output text)
REPO=pavansaiambala7/ecommerce_project_spring-boot
cd ~/ecommerce_project_spring-boot && git pull

# 1. Teach AWS to trust GitHub's token issuer (once per account)
aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com \
  --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1 \
  2>/dev/null || echo "provider already exists"

# 2. The role this repository may assume
sed "s/ACCOUNT_ID/$ACCT/" deploy/iam/github-actions-trust-policy.json > /tmp/trust.json
aws iam create-role --role-name github-actions-deploy \
  --assume-role-policy-document file:///tmp/trust.json \
  --description "Build and deploy the storefront from GitHub Actions"

aws iam put-role-policy --role-name github-actions-deploy \
  --policy-name build-and-deploy \
  --policy-document file://deploy/iam/github-actions-policy.json

# 3. The app host must be reachable by Systems Manager
aws iam attach-role-policy --role-name app-ec2-role \
  --policy-arn arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore

echo
echo "Add this as the AWS_ROLE_ARN secret on GitHub:"
aws iam get-role --role-name github-actions-deploy --query 'Role.Arn' --output text
```

### One-time GitHub setup

**Settings -> Secrets and variables -> Actions -> New repository secret**

| Name | Value |
|---|---|
| `AWS_ROLE_ARN` | the ARN printed above |

That is the only secret. Until it exists the workflow still runs the tests and
stops before deploying, saying why, rather than failing on a missing secret.

### Using it

Every push to `main` builds and deploys. **Actions -> Deploy -> Run workflow**
runs it by hand, with a **skip tests** tick for when the tests have already
passed on that commit and the point is to get it onto the host.

The permissions are deliberately narrow: push to the one ECR repository, and
run a shell command on an instance tagged `Name=ecommerce-app`. It cannot touch
other instances, read secrets from SSM, or change any infrastructure.

### What happens to Jenkins

The `Jenkinsfile` still works and is unchanged, so nothing is lost by keeping
the instance for its own sake. But it will keep failing at the image build for
the reason above, so turn its SCM polling off to stop it queuing builds that
cannot finish:

**Job -> Configure -> Build Triggers -> untick "Poll SCM" -> Save**
