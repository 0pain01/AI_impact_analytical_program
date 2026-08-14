#!/usr/bin/env bash
# Seeds ~30 days of synthetic GitHub Actions/PR history through the REAL ingestion pipeline
# (connector-github -> RabbitMQ -> ingestion-writer -> staging.raw_event -> metrics-engine
# recompute -> mart.metric_daily), so Cockpit's DORA radar/trend/tier-band charts show genuine
# day-over-day variation instead of the single data point left by infra/smoke-e2e.sh.
#
# This does not fabricate numbers in the UI — every event is a real webhook POST processed by
# the same services production traffic would use. Safe to re-run: delivery IDs are deterministic
# per event, so ingestion-writer's idempotency (source_id uniqueness) skips duplicates.
#
# Prerequisites: infra/docker-compose.yml running (postgres+rabbitmq), service jars built
# (mvn package in api-core/ingestion-writer/connectors/connector-github/metrics-engine).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"
SECRET="${GITHUB_WEBHOOK_SECRET:-demo-seed-secret}"
PIDS=()

cleanup() {
  for pid in "${PIDS[@]:-}"; do kill "$pid" 2>/dev/null || true; done
}
trap cleanup EXIT

wait_healthy() { # url, name
  for _ in $(seq 1 60); do
    if curl -sf "$1" >/dev/null 2>&1; then echo "  $2 healthy"; return 0; fi
    sleep 2
  done
  echo "FAIL: $2 did not become healthy"; exit 1
}

port_healthy() { curl -sf "http://localhost:$1/actuator/health" >/dev/null 2>&1; }

echo "1/4 Starting infra (postgres + rabbitmq)..."
docker compose -f "$ROOT/infra/docker-compose.yml" up -d --wait

echo "2/4 Starting services not already running..."
if ! port_healthy 8082; then
  "$JAVA_BIN" -jar "$ROOT"/services/ingestion-writer/target/ingestion-writer-*.jar \
    >/tmp/aiimpacteval-seed-ingestion-writer.log 2>&1 &
  PIDS+=($!)
fi
if ! port_healthy 8081; then
  GITHUB_WEBHOOK_SECRET="$SECRET" \
    "$JAVA_BIN" -jar "$ROOT"/services/connectors/connector-github/target/connector-github-*.jar \
    >/tmp/aiimpacteval-seed-connector-github.log 2>&1 &
  PIDS+=($!)
fi
if ! port_healthy 8084; then
  "$JAVA_BIN" -jar "$ROOT"/services/metrics-engine/target/metrics-engine-*.jar \
    >/tmp/aiimpacteval-seed-metrics-engine.log 2>&1 &
  PIDS+=($!)
fi

wait_healthy http://localhost:8082/actuator/health "ingestion-writer"
wait_healthy http://localhost:8081/actuator/health "connector-github"
wait_healthy http://localhost:8084/actuator/health "metrics-engine"

echo "3/4 Posting synthetic events for ai-impact-evaluation/demo across the last 30 days..."
DEPLOY_COUNT=0
PR_COUNT=0
FAIL_COUNT=0

while IFS=$'\t' read -r EVENT DELIVERY BODY; do
  SIG=$(python3 -c "
import hmac, hashlib, sys
secret = sys.argv[1].encode()
body = sys.argv[2].encode()
print('sha256=' + hmac.new(secret, body, hashlib.sha256).hexdigest())
" "$SECRET" "$BODY")

  CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST http://localhost:8081/webhooks/github \
    -H "X-Hub-Signature-256: $SIG" -H "X-GitHub-Event: $EVENT" \
    -H "X-GitHub-Delivery: $DELIVERY" -H "Content-Type: application/json" -d "$BODY")

  if [ "$CODE" = "202" ]; then
    case "$EVENT" in
      workflow_run) DEPLOY_COUNT=$((DEPLOY_COUNT + 1)) ;;
      pull_request) PR_COUNT=$((PR_COUNT + 1)) ;;
    esac
  else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    echo "  WARN: $EVENT ($DELIVERY) returned $CODE"
  fi
done < <(python3 "$ROOT/infra/generate-seed-events.py")

echo "  posted $DEPLOY_COUNT workflow_run + $PR_COUNT pull_request events ($FAIL_COUNT failed)"

echo "4/4 Waiting for staging writes to land, then triggering recompute..."
sleep 5
curl -sf -X POST http://localhost:8084/internal/recompute >/dev/null
echo "  recompute triggered"

echo ""
echo "Done. Reload Cockpit in the frontend — the DORA tiles, radar, and trend chart should now"
echo "show 30 days of history instead of a single data point."
