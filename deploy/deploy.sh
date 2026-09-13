#!/usr/bin/env bash
# Runs ON THE APP EC2 HOST. Jenkins scp's this file (plus fetch-secrets.sh and
# docker-compose.prod.yml) here and invokes it over SSH - the host never needs
# git, Maven, or a JDK; it only ever pulls and runs an image someone else built.
#
# Usage: ./deploy.sh <ecr-image-uri-with-tag> <aws-region>
# Example: ./deploy.sh 123456789012.dkr.ecr.us-east-1.amazonaws.com/ecommerce-app:a1b2c3d us-east-1
set -euo pipefail

ECR_IMAGE="${1:?usage: deploy.sh <ecr-image-uri-with-tag> <aws-region>}"
AWS_REGION="${2:?usage: deploy.sh <ecr-image-uri-with-tag> <aws-region>}"
DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REGISTRY="${ECR_IMAGE%%/*}"
ENV_FILE="$DEPLOY_DIR/.env"
LAST_GOOD_FILE="$DEPLOY_DIR/.last_good_tag"
COMPOSE="docker compose -f $DEPLOY_DIR/docker-compose.prod.yml --env-file $ENV_FILE"

log() { echo "[deploy] $*"; }

smoke_test() {
    # /login is unauthenticated (permitAll) and always renders, so it doubles
    # as a cheap end-to-end check that Flyway migrated, the DB connection
    # works, and Spring Security's filter chain came up correctly.
    for i in $(seq 1 12); do
        if curl -sf -o /dev/null -w '%{http_code}' http://localhost/login | grep -q '^200$'; then
            log "smoke test passed on attempt $i"
            return 0
        fi
        log "smoke test attempt $i/12 not ready yet, retrying in 5s"
        sleep 5
    done
    return 1
}

deploy_image() {
    local image="$1"
    AWS_REGION="$AWS_REGION" bash "$DEPLOY_DIR/fetch-secrets.sh" "$ENV_FILE"
    echo "ECR_IMAGE=$image" >> "$ENV_FILE"

    aws ecr get-login-password --region "$AWS_REGION" \
        | docker login --username AWS --password-stdin "$REGISTRY"

    $COMPOSE pull app
    $COMPOSE up -d
}

log "deploying $ECR_IMAGE"
deploy_image "$ECR_IMAGE"

if smoke_test; then
    echo "$ECR_IMAGE" > "$LAST_GOOD_FILE"
    log "deploy succeeded: $ECR_IMAGE"
    exit 0
fi

log "smoke test FAILED for $ECR_IMAGE"

if [ -f "$LAST_GOOD_FILE" ] && [ "$(cat "$LAST_GOOD_FILE")" != "$ECR_IMAGE" ]; then
    PREVIOUS="$(cat "$LAST_GOOD_FILE")"
    log "rolling back to last known-good image: $PREVIOUS"
    deploy_image "$PREVIOUS"
    if smoke_test; then
        log "rollback to $PREVIOUS succeeded"
    else
        log "rollback ALSO failed smoke test - manual intervention required"
    fi
else
    log "no previous known-good image recorded - nothing to roll back to"
fi

# Always fail the pipeline run when the new image didn't pass, even though
# the host may now be running a rolled-back previous version.
exit 1
