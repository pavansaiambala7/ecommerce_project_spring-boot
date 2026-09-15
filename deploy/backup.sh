#!/usr/bin/env bash
# Nightly database backup to S3. Runs ON THE APP EC2 HOST from cron.
#
# The database lives in a Docker volume on a single instance. Without this,
# losing that instance - or one careless `docker compose down -v` - destroys
# every user, order and payment permanently. That is the difference between an
# outage and going out of business.
#
# Usage: ./backup.sh <s3-bucket-name>
set -euo pipefail

BUCKET="${1:?usage: backup.sh <s3-bucket-name>}"
DB_CONTAINER="${DB_CONTAINER:-ecommerce-db}"
DB_NAME="${POSTGRES_DB:-ecommjava}"
DB_USER="${POSTGRES_USER:-postgres}"
RETAIN_DAYS="${RETAIN_DAYS:-30}"

STAMP="$(date -u +%Y-%m-%dT%H-%M-%SZ)"
ARCHIVE="/tmp/ecommjava-${STAMP}.sql.gz"

log() { echo "[backup $(date -u +%H:%M:%S)] $*"; }

cleanup() { rm -f "$ARCHIVE"; }
trap cleanup EXIT

log "dumping $DB_NAME from container $DB_CONTAINER"

# --clean --if-exists so the dump can be restored over an existing database
# without hand-editing it first, which is exactly the moment nobody wants to be
# editing SQL by hand.
docker exec "$DB_CONTAINER" pg_dump \
    --username "$DB_USER" \
    --dbname "$DB_NAME" \
    --clean --if-exists --no-owner --no-privileges \
    | gzip -9 > "$ARCHIVE"

SIZE=$(stat -c %s "$ARCHIVE")
# A dump of an empty or failed database still produces a small valid gzip, so
# size is the cheapest guard against silently uploading nothing for months.
if [ "$SIZE" -lt 1024 ]; then
    log "FAILED: dump is only ${SIZE} bytes, refusing to upload"
    exit 1
fi

log "uploading $(( SIZE / 1024 )) KiB to s3://$BUCKET/"
aws s3 cp "$ARCHIVE" "s3://$BUCKET/daily/ecommjava-${STAMP}.sql.gz" \
    --storage-class STANDARD_IA

# Verify the object is actually there. `aws s3 cp` exiting 0 is not by itself
# proof that a later read will succeed.
aws s3api head-object --bucket "$BUCKET" --key "daily/ecommjava-${STAMP}.sql.gz" >/dev/null
log "verified s3://$BUCKET/daily/ecommjava-${STAMP}.sql.gz"

# Prune anything past the retention window. Lifecycle rules on the bucket would
# also work; doing it here keeps the whole policy in one readable place.
CUTOFF=$(date -u -d "${RETAIN_DAYS} days ago" +%Y-%m-%d)
aws s3 ls "s3://$BUCKET/daily/" | while read -r date_part _ _ key; do
    if [[ "$date_part" < "$CUTOFF" ]]; then
        log "pruning $key (older than ${RETAIN_DAYS} days)"
        aws s3 rm "s3://$BUCKET/daily/$key"
    fi
done

log "done"
