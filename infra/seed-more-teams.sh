#!/usr/bin/env bash
# Seeds 5 additional demo teams (Payments, Platform, Growth, Mobile, Checkout) plus ~30 days of
# per-team deploy/PR history, through the REAL ingestion pipeline (connector-github -> RabbitMQ
# -> ingestion-writer -> staging.raw_event; team.snapshot events additionally flow to
# identity-service -> core.team/core.team_repo/core.team_member).
#
# infra/smoke-e2e.sh creates exactly one team (source_id 9001); this script adds five more with
# distinct source_ids (9101-9105, see infra/generate-team-events.py) so the Teams view has more
# than a single card to show. Re-runnable safely: team upsert is keyed on (source, source_id),
# and event delivery IDs are deterministic so ingestion-writer's idempotency dedupes re-posts.
#
# Prerequisites: infra/docker-compose.yml running (postgres+rabbitmq), service jars built,
# JAVA_HOME pointing at a Java 21+ JDK if the default `java` on PATH is older (see README).
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
if ! port_healthy 8085; then
  "$JAVA_BIN" -jar "$ROOT"/services/identity-service/target/identity-service-*.jar \
    >/tmp/aiimpacteval-seed-identity-service.log 2>&1 &
  PIDS+=($!)
fi

wait_healthy http://localhost:8082/actuator/health "ingestion-writer"
wait_healthy http://localhost:8081/actuator/health "connector-github"
wait_healthy http://localhost:8084/actuator/health "metrics-engine"
wait_healthy http://localhost:8085/actuator/health "identity-service"

echo "3/4 Posting team snapshots + per-team deploy/PR history..."
TEAM_COUNT=0
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
      team.snapshot) TEAM_COUNT=$((TEAM_COUNT + 1)) ;;
      workflow_run) DEPLOY_COUNT=$((DEPLOY_COUNT + 1)) ;;
      pull_request) PR_COUNT=$((PR_COUNT + 1)) ;;
    esac
  else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    echo "  WARN: $EVENT ($DELIVERY) returned $CODE"
  fi
done < <(python3 "$ROOT/infra/generate-team-events.py")

echo "  posted $TEAM_COUNT team.snapshot + $DEPLOY_COUNT workflow_run + $PR_COUNT pull_request events ($FAIL_COUNT failed)"

echo "4/4 Waiting for team import + staging writes to land, then triggering recompute..."
sleep 6
curl -sf -X POST http://localhost:8084/internal/recompute >/dev/null
echo "  recompute triggered"

echo ""
echo "Done. Reload Teams in the frontend — Payments, Platform, Growth, Mobile, and Checkout"
echo "should now appear alongside the existing e2e-team, each with its own repo count and,"
echo "on drill-down, its own DORA performance (deliberately varied: Mobile/Payments strong,"
echo "Platform weakest, to echo the mock Investment Profile team narrative)."
