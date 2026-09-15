#!/usr/bin/env bash
# Restores the database from an S3 backup. Runs ON THE APP EC2 HOST.
#
# A backup nobody has restored is not a backup - it is an untested assumption.
# Run this against a scratch database at least once so the procedure is known
# to work before the day it is needed.
#
# Usage: ./restore.sh <s3-bucket> <key>            e.g. daily/ecommjava-2026-09-15T02-00-00Z.sql.gz
#        ./restore.sh <s3-bucket> latest
set -euo pipefail

BUCKET="${1:?usage: restore.sh <s3-bucket> <key|latest>}"
KEY="${2:?usage: restore.sh <s3-bucket> <key|latest>}"
DB_CONTAINER="${DB_CONTAINER:-ecommerce-db}"
DB_NAME="${POSTGRES_DB:-ecommjava}"
DB_USER="${POSTGRES_USER:-postgres}"

if [ "$KEY" = "latest" ]; then
    KEY="daily/$(aws s3 ls "s3://$BUCKET/daily/" | sort | tail -1 | awk '{print $4}')"
    echo "[restore] latest is $KEY"
fi

ARCHIVE="/tmp/restore-$(basename "$KEY")"
trap 'rm -f "$ARCHIVE"' EXIT

echo "[restore] downloading s3://$BUCKET/$KEY"
aws s3 cp "s3://$BUCKET/$KEY" "$ARCHIVE"

echo
echo "About to OVERWRITE database '$DB_NAME' in container '$DB_CONTAINER'."
echo "Everything currently in it will be replaced by the backup."
read -r -p "Type the database name to confirm: " CONFIRM
[ "$CONFIRM" = "$DB_NAME" ] || { echo "[restore] aborted"; exit 1; }

echo "[restore] restoring..."
gunzip -c "$ARCHIVE" | docker exec -i "$DB_CONTAINER" psql \
    --username "$DB_USER" --dbname "$DB_NAME" --quiet -v ON_ERROR_STOP=1

echo "[restore] complete. Restart the app so it reconnects:"
echo "  docker compose -f /opt/ecommerce/deploy/docker-compose.prod.yml restart app"
