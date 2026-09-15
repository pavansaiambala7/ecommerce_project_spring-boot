#!/usr/bin/env bash
# Outage alerting. Runs ON THE APP EC2 HOST from cron, every 5 minutes.
#
# Without this the first notification of an outage is a customer telling you.
# Publishes to SNS, which fans out to email or SMS.
#
# Usage: ./healthcheck.sh <sns-topic-arn>
set -uo pipefail   # deliberately not -e: a failing check is the normal path here

TOPIC="${1:?usage: healthcheck.sh <sns-topic-arn>}"
URL="${HEALTH_URL:-http://localhost/actuator/health/readiness}"
STATE_FILE="/tmp/.ecommerce-health-state"
FAIL_THRESHOLD="${FAIL_THRESHOLD:-2}"

body=$(curl -s --max-time 10 -o /tmp/hc.json -w '%{http_code}' "$URL" 2>/dev/null || echo 000)
detail=$(head -c 300 /tmp/hc.json 2>/dev/null)
rm -f /tmp/hc.json

if [ "$body" = "200" ] && [[ "$detail" == *'"status":"UP"'* ]]; then
    healthy=1
else
    healthy=0
fi

previous_fails=$(cat "$STATE_FILE" 2>/dev/null || echo 0)
[[ "$previous_fails" =~ ^[0-9]+$ ]] || previous_fails=0

notify() {
    aws sns publish --topic-arn "$TOPIC" --subject "$1" --message "$2" >/dev/null 2>&1 \
        || echo "[healthcheck] WARNING: could not publish to SNS"
}

if [ "$healthy" -eq 1 ]; then
    # Only announce recovery if it had actually alerted, so a single blip
    # followed by a recovery does not produce two messages about nothing.
    if [ "$previous_fails" -ge "$FAIL_THRESHOLD" ]; then
        notify "RECOVERED: ShopKart is back up" \
"The application is answering readiness checks again.

Host:  $(hostname)
Time:  $(date -u '+%Y-%m-%d %H:%M:%S UTC')
It was failing for roughly $(( previous_fails * 5 )) minutes."
    fi
    echo 0 > "$STATE_FILE"
    exit 0
fi

fails=$(( previous_fails + 1 ))
echo "$fails" > "$STATE_FILE"

# Alert on the threshold only. Alerting on the first failure turns a 30-second
# restart during a deploy into a page, and an alert that cries wolf gets muted -
# at which point it protects nothing.
if [ "$fails" -eq "$FAIL_THRESHOLD" ]; then
    notify "DOWN: ShopKart is not responding" \
"Readiness check failed ${fails} times in a row.

URL:      $URL
HTTP:     $body
Response: ${detail:-<empty>}
Host:     $(hostname)
Time:     $(date -u '+%Y-%m-%d %H:%M:%S UTC')

Containers:
$(docker ps --format '  {{.Names}}  {{.Status}}' 2>/dev/null || echo '  could not list containers')

Recent application log:
$(docker logs --tail 25 ecommerce-app 2>&1 | tail -25 || echo '  unavailable')"
fi

echo "[healthcheck] unhealthy (HTTP $body), consecutive failures: $fails"
exit 1
