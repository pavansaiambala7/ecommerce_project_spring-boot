#!/usr/bin/env bash
# Pulls runtime secrets from AWS SSM Parameter Store into a .env file that
# docker-compose reads automatically.
#
# Requires no AWS keys of any kind on this host: it relies entirely on the
# IAM instance role attached to this EC2 instance (see
# deploy/iam/prod-instance-policy.json), scoped to read-only on the
# /ecommerce/* parameter path. Jenkins never sees these values - it only
# triggers this script over SSH.
set -euo pipefail

REGION="${AWS_REGION:-us-east-1}"
ENV_FILE="${1:-.env}"

get_param() {
    aws ssm get-parameter --name "$1" --with-decryption --region "$REGION" \
        --query 'Parameter.Value' --output text
}

JWT_SECRET_VALUE=$(get_param "/ecommerce/jwt-secret")
GEMINI_API_KEY_VALUE=$(get_param "/ecommerce/gemini-api-key" 2>/dev/null || echo "")
POSTGRES_PASSWORD_VALUE=$(get_param "/ecommerce/db-password")

umask 077
cat > "$ENV_FILE" <<EOF
JWT_SECRET=${JWT_SECRET_VALUE}
GEMINI_API_KEY=${GEMINI_API_KEY_VALUE}
POSTGRES_PASSWORD=${POSTGRES_PASSWORD_VALUE}
POSTGRES_USER=postgres
EOF
chmod 600 "$ENV_FILE"

echo "Wrote secrets into $ENV_FILE (mode 600)"
