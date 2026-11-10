#!/usr/bin/env bash
# One-time AWS setup for this app, run from AWS CloudShell in the account that
# should host it. Safe to re-run: every create tolerates an existing resource.
#
#   bash deploy/bootstrap-aws.sh
#
# What this builds, and what it deliberately leaves out:
#
#   - ONE t3.small running the app and its PostgreSQL under docker-compose.
#     deploy/README.md describes a second instance for Jenkins; the build moved
#     to GitHub's runners (.github/workflows/deploy.yml) and that host is gone.
#   - NO key pair and NO port 22. Deploys arrive over Systems Manager, and
#     Session Manager covers the rare need for a shell. The three things that
#     used to break deploys here - a key file, a security group rule and a
#     known_hosts entry - are simply absent.
#   - Docker and the Compose plugin install from user-data at launch, so the
#     host is ready without anyone logging into it.
set -euo pipefail

export AWS_PAGER=""
AWS_REGION="${AWS_REGION:-ap-southeast-2}"        # must match deploy.yml's AWS_REGION
export AWS_DEFAULT_REGION="$AWS_REGION"
GITHUB_REPO="${GITHUB_REPO:-pavansaiambala7/ecommerce_project_spring-boot}"
ECR_REPOSITORY=ecommerce-app
INSTANCE_TAG=ecommerce-app
INSTANCE_TYPE="${INSTANCE_TYPE:-t3.small}"

ACCT=$(aws sts get-caller-identity --query Account --output text)
echo "account $ACCT, region $AWS_REGION"
say() { printf '\n== %s\n' "$*"; }

say "IAM role for the instance"
# The policy file pins an S3 backup bucket belonging to the previous account,
# so the name is rewritten here. The bucket itself is optional - backup.sh is
# the only thing that uses it - but the policy must name one we could own.
BACKUP_BUCKET="ecommerce-db-backups-$ACCT"
sed "s/ecommerce-db-backups-792026110282/$BACKUP_BUCKET/g" \
    deploy/iam/prod-instance-policy.json > /tmp/prod-instance-policy.json

aws iam create-role --role-name app-ec2-role \
  --assume-role-policy-document file://deploy/iam/ec2-trust-policy.json \
  >/dev/null 2>&1 || echo "  role exists"
aws iam put-role-policy --role-name app-ec2-role \
  --policy-name ecr-pull-and-ssm-read --policy-document file:///tmp/prod-instance-policy.json
# Without this managed policy the SSM agent never registers, and the deploy
# workflow's send-command has nothing to send to.
aws iam attach-role-policy --role-name app-ec2-role \
  --policy-arn arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore
aws iam create-instance-profile --instance-profile-name app-ec2-profile \
  >/dev/null 2>&1 || echo "  profile exists"
aws iam add-role-to-instance-profile --instance-profile-name app-ec2-profile \
  --role-name app-ec2-role >/dev/null 2>&1 || echo "  role already in profile"

say "ECR repository"
aws ecr create-repository --repository-name "$ECR_REPOSITORY" >/dev/null 2>&1 \
  || echo "  repository exists"

say "Runtime secrets in SSM Parameter Store"
# Each secret is written only when absent: --overwrite would make a re-run
# harmless but would also roll the generated values, invalidating every
# issued token and locking the app out of its own database.
put_secret() {
    if aws ssm get-parameter --name "$1" >/dev/null 2>&1; then
        echo "  $1 already set"
    else
        aws ssm put-parameter --name "$1" --type SecureString --value "$2" >/dev/null
        echo "  $1 created"
    fi
}
put_secret /ecommerce/jwt-secret  "$(openssl rand -base64 48)"
put_secret /ecommerce/db-password "$(openssl rand -base64 24)"
# AI search and chat stay inert until this holds a real key.
put_secret /ecommerce/gemini-api-key "${GEMINI_API_KEY:-replace-me}"

say "Security group"
VPC_ID=$(aws ec2 describe-vpcs --filters Name=is-default,Values=true \
          --query 'Vpcs[0].VpcId' --output text)
APP_SG=$(aws ec2 describe-security-groups \
          --filters Name=group-name,Values=app-sg Name=vpc-id,Values="$VPC_ID" \
          --query 'SecurityGroups[0].GroupId' --output text 2>/dev/null || echo None)
if [ "$APP_SG" = "None" ] || [ -z "$APP_SG" ]; then
    APP_SG=$(aws ec2 create-security-group --group-name app-sg --vpc-id "$VPC_ID" \
      --description "ecommerce app: HTTP in, nothing else" --query GroupId --output text)
    # Port 80 only. PostgreSQL is never published on the host's public
    # interface; the app reaches it over the compose-internal network.
    aws ec2 authorize-security-group-ingress --group-id "$APP_SG" \
      --ip-permissions IpProtocol=tcp,FromPort=80,ToPort=80,IpRanges='[{CidrIp=0.0.0.0/0}]' >/dev/null
fi
echo "  $APP_SG"

say "App instance"
EXISTING=$(aws ec2 describe-instances \
  --filters "Name=tag:Name,Values=$INSTANCE_TAG" \
            "Name=instance-state-name,Values=running,pending,stopped" \
  --query 'Reservations[0].Instances[0].InstanceId' --output text)
if [ "$EXISTING" != "None" ] && [ -n "$EXISTING" ]; then
    APP_INSTANCE_ID="$EXISTING"
    echo "  reusing $APP_INSTANCE_ID"
    aws ec2 start-instances --instance-ids "$APP_INSTANCE_ID" >/dev/null 2>&1 || true
else
    AMI_ID=$(aws ssm get-parameters \
      --names /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64 \
      --query 'Parameters[0].Value' --output text)
    SUBNET_ID=$(aws ec2 describe-subnets --filters Name=vpc-id,Values="$VPC_ID" \
      --query 'Subnets[0].SubnetId' --output text)
    # Amazon Linux 2023 ships the SSM agent and aws-cli v2, but neither Docker
    # nor the Compose plugin - and its repos have no docker-compose-plugin
    # package, so the binary is fetched directly. Without it deploy.sh dies on
    # `docker compose -f` with an unrelated-looking "unknown shorthand flag".
    cat > /tmp/user-data.sh <<'USERDATA'
#!/bin/bash
set -eux
dnf install -y docker
systemctl enable --now docker
usermod -aG docker ec2-user
mkdir -p /usr/libexec/docker/cli-plugins
curl -sSL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o /usr/libexec/docker/cli-plugins/docker-compose
chmod +x /usr/libexec/docker/cli-plugins/docker-compose
mkdir -p /opt/ecommerce/deploy
USERDATA
    # A new instance profile takes a moment to become usable. run-instances
    # fails outright rather than retrying, so give IAM a beat to propagate.
    sleep 15
    APP_INSTANCE_ID=$(aws ec2 run-instances --image-id "$AMI_ID" \
      --instance-type "$INSTANCE_TYPE" --security-group-ids "$APP_SG" \
      --subnet-id "$SUBNET_ID" --iam-instance-profile Name=app-ec2-profile \
      --user-data file:///tmp/user-data.sh \
      --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=$INSTANCE_TAG}]" \
      --query 'Instances[0].InstanceId' --output text)
    echo "  launched $APP_INSTANCE_ID"
fi
aws ec2 wait instance-running --instance-ids "$APP_INSTANCE_ID"

say "Elastic IP"
# The previous deployment's address moved and broke every link to the demo,
# including the one on a CV. An Elastic IP survives stop/start.
ALLOC=$(aws ec2 describe-addresses \
  --query "Addresses[?InstanceId=='$APP_INSTANCE_ID'].AllocationId | [0]" --output text)
if [ "$ALLOC" = "None" ] || [ -z "$ALLOC" ]; then
    ALLOC=$(aws ec2 allocate-address --query AllocationId --output text)
    aws ec2 associate-address --instance-id "$APP_INSTANCE_ID" --allocation-id "$ALLOC" >/dev/null
fi
APP_IP=$(aws ec2 describe-addresses --allocation-ids "$ALLOC" \
  --query 'Addresses[0].PublicIp' --output text)
echo "  $APP_IP"

say "GitHub OIDC trust"
aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com \
  --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1 \
  >/dev/null 2>&1 || echo "  provider exists"
sed -e "s/ACCOUNT_ID/$ACCT/" \
    -e "s#pavansaiambala7/ecommerce_project_spring-boot#$GITHUB_REPO#" \
    deploy/iam/github-actions-trust-policy.json > /tmp/trust.json
aws iam create-role --role-name github-actions-deploy \
  --assume-role-policy-document file:///tmp/trust.json >/dev/null 2>&1 \
  || aws iam update-assume-role-policy --role-name github-actions-deploy \
       --policy-document file:///tmp/trust.json
aws iam put-role-policy --role-name github-actions-deploy \
  --policy-name build-and-deploy --policy-document file://deploy/iam/github-actions-policy.json

say "Waiting for the SSM agent to register"
# user-data is still installing Docker at this point. The deploy workflow needs
# the agent Online, so wait here rather than letting the first deploy fail.
PING=unknown
for i in $(seq 1 30); do
    PING=$(aws ssm describe-instance-information \
      --filters "Key=InstanceIds,Values=$APP_INSTANCE_ID" \
      --query 'InstanceInformationList[0].PingStatus' --output text 2>/dev/null || echo None)
    [ "$PING" = "Online" ] && break
    sleep 10
done
echo "  SSM agent: $PING"

cat <<SUMMARY

------------------------------------------------------------
Done.

  instance   $APP_INSTANCE_ID
  public IP  $APP_IP
  SSM agent  $PING

Next, add this as the AWS_ROLE_ARN secret in GitHub
(Settings > Secrets and variables > Actions):

  arn:aws:iam::$ACCT:role/github-actions-deploy

Then push to main. The workflow builds the image, pushes it to ECR and
deploys it here. The store will answer at http://$APP_IP
------------------------------------------------------------
SUMMARY
