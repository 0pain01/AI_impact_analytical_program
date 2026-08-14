#!/usr/bin/env bash
# End-to-end ingestion smoke test (PRD F1/F2 / FR-1.8 verification):
#   webhook POST -> connector (auth check) -> RabbitMQ -> ingestion-writer -> staging.raw_event
# Prerequisites: docker compose infra running (or startable), services built (mvn package).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"
SECRET="e2e-smoke-secret"
JIRA_TOKEN="e2e-jira-token"
DELIVERY_ID="e2e-$(date +%s)"
JIRA_MARKER="e2e-jira-$(date +%s)"
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

echo "1/10 Starting infra (postgres + rabbitmq)..."
docker compose -f "$ROOT/infra/docker-compose.yml" up -d --wait

echo "2/10 Starting services..."
"$JAVA_BIN" -jar "$ROOT"/services/api-core/target/api-core-*.jar >/tmp/aiimpacteval-api-core.log 2>&1 &
PIDS+=($!)
"$JAVA_BIN" -jar "$ROOT"/services/ingestion-writer/target/ingestion-writer-*.jar >/tmp/aiimpacteval-ingestion-writer.log 2>&1 &
PIDS+=($!)
GITHUB_WEBHOOK_SECRET="$SECRET" \
  "$JAVA_BIN" -jar "$ROOT"/services/connectors/connector-github/target/connector-github-*.jar >/tmp/aiimpacteval-connector-github.log 2>&1 &
PIDS+=($!)
JIRA_WEBHOOK_SECRET="$JIRA_TOKEN" \
  "$JAVA_BIN" -jar "$ROOT"/services/connectors/connector-jira/target/connector-jira-*.jar >/tmp/aiimpacteval-connector-jira.log 2>&1 &
PIDS+=($!)
"$JAVA_BIN" -jar "$ROOT"/services/metrics-engine/target/metrics-engine-*.jar >/tmp/aiimpacteval-metrics-engine.log 2>&1 &
PIDS+=($!)
"$JAVA_BIN" -jar "$ROOT"/services/identity-service/target/identity-service-*.jar >/tmp/aiimpacteval-identity-service.log 2>&1 &
PIDS+=($!)

wait_healthy http://localhost:8080/actuator/health "api-core (migrations)"
wait_healthy http://localhost:8082/actuator/health "ingestion-writer"
wait_healthy http://localhost:8081/actuator/health "connector-github"
wait_healthy http://localhost:8083/actuator/health "connector-jira"
wait_healthy http://localhost:8084/actuator/health "metrics-engine"
wait_healthy http://localhost:8085/actuator/health "identity-service"

echo "3/10 Posting signed fake GitHub webhook (delivery $DELIVERY_ID)..."
BODY='{"action":"opened","number":42,"pull_request":{"id":98765,"title":"E2E smoke PR"}}'
SIG=$(python3 -c "import hmac,hashlib,sys; print('sha256='+hmac.new('$SECRET'.encode(),'$BODY'.encode(),hashlib.sha256).hexdigest())")

HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST http://localhost:8081/webhooks/github \
  -H "X-Hub-Signature-256: $SIG" -H "X-GitHub-Event: pull_request" \
  -H "X-GitHub-Delivery: $DELIVERY_ID" -H "Content-Type: application/json" -d "$BODY")
[ "$HTTP_CODE" = "202" ] || { echo "FAIL: webhook returned $HTTP_CODE (expected 202)"; exit 1; }

BAD_CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST http://localhost:8081/webhooks/github \
  -H "X-Hub-Signature-256: sha256=deadbeef" -H "X-GitHub-Event: pull_request" \
  -H "X-GitHub-Delivery: bad-$DELIVERY_ID" -H "Content-Type: application/json" -d "$BODY")
[ "$BAD_CODE" = "401" ] || { echo "FAIL: forged webhook returned $BAD_CODE (expected 401)"; exit 1; }
echo "  accepted valid signature (202), rejected forged signature (401)"

echo "4/10 Verifying GitHub event landed in staging.raw_event..."
for _ in $(seq 1 15); do
  ROW=$(docker compose -f "$ROOT/infra/docker-compose.yml" exec -T postgres \
    psql -U aiimpacteval -d aiimpacteval -tAc \
    "SELECT source || '|' || event_type || '|' || (payload->'pull_request'->>'title') FROM staging.raw_event WHERE source_id = '$DELIVERY_ID'")
  if [ -n "$ROW" ]; then break; fi
  sleep 2
done
[ "$ROW" = "github|pull_request|E2E smoke PR" ] || { echo "FAIL: staged row mismatch: '$ROW'"; exit 1; }
echo "  staged row verified: $ROW"

echo "5/10 Verifying Jira leg (token auth + staging write)..."
JIRA_BODY='{"timestamp":1751630400000,"webhookEvent":"jira:issue_updated","user":{"accountId":"e2e-actor-1","displayName":"E2E Actor"},"issue":{"id":"10042","fields":{"summary":"'"$JIRA_MARKER"'"}}}'
JIRA_CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST "http://localhost:8083/webhooks/jira?token=$JIRA_TOKEN" \
  -H "Content-Type: application/json" -d "$JIRA_BODY")
[ "$JIRA_CODE" = "202" ] || { echo "FAIL: jira webhook returned $JIRA_CODE (expected 202)"; exit 1; }
JIRA_BAD=$(curl -s -o /dev/null -w '%{http_code}' -X POST "http://localhost:8083/webhooks/jira?token=wrong" \
  -H "Content-Type: application/json" -d "$JIRA_BODY")
[ "$JIRA_BAD" = "401" ] || { echo "FAIL: jira forged token returned $JIRA_BAD (expected 401)"; exit 1; }
for _ in $(seq 1 15); do
  JROW=$(docker compose -f "$ROOT/infra/docker-compose.yml" exec -T postgres \
    psql -U aiimpacteval -d aiimpacteval -tAc \
    "SELECT source || '|' || event_type FROM staging.raw_event WHERE payload->'issue'->'fields'->>'summary' = '$JIRA_MARKER'")
  if [ -n "$JROW" ]; then break; fi
  sleep 2
done
[ "$JROW" = "jira|jira:issue_updated" ] || { echo "FAIL: jira staged row mismatch: '$JROW'"; exit 1; }
echo "  jira: accepted valid token (202), rejected wrong token (401), staged row verified"

echo "6/10 Verifying idempotency (redelivery of same delivery ID)..."
curl -s -o /dev/null -X POST http://localhost:8081/webhooks/github \
  -H "X-Hub-Signature-256: $SIG" -H "X-GitHub-Event: pull_request" \
  -H "X-GitHub-Delivery: $DELIVERY_ID" -H "Content-Type: application/json" -d "$BODY"
sleep 3
COUNT=$(docker compose -f "$ROOT/infra/docker-compose.yml" exec -T postgres \
  psql -U aiimpacteval -d aiimpacteval -tAc "SELECT count(*) FROM staging.raw_event WHERE source_id = '$DELIVERY_ID'")
[ "$COUNT" = "1" ] || { echo "FAIL: expected 1 row after redelivery, got $COUNT"; exit 1; }
echo "  redelivery deduplicated (1 row)"

echo "7/10 Verifying metrics path (workflow_run -> mart -> Cockpit API)..."
TODAY=$(date -u +%Y-%m-%dT%H:%M:%SZ)
WF_BODY='{"action":"completed","workflow_run":{"id":555001,"name":"Deploy production","conclusion":"success","status":"completed","updated_at":"'"$TODAY"'"},"repository":{"full_name":"ai-impact-evaluation/demo"}}'
post_github() { # event, delivery, body
  local sig
  sig=$(python3 -c "import hmac,hashlib; print('sha256='+hmac.new('$SECRET'.encode(),'''$3'''.encode(),hashlib.sha256).hexdigest())")
  local code
  code=$(curl -s -o /dev/null -w '%{http_code}' -X POST http://localhost:8081/webhooks/github \
    -H "X-Hub-Signature-256: $sig" -H "X-GitHub-Event: $1" \
    -H "X-GitHub-Delivery: $2" -H "Content-Type: application/json" -d "$3")
  [ "$code" = "202" ] || { echo "FAIL: github $1 webhook returned $code"; exit 1; }
}

post_github workflow_run "wf-$DELIVERY_ID" "$WF_BODY"
# A merged PR before the deploy → lead time links PR-open to first prod deploy after merge.
PR_CREATED=$(python3 -c "import datetime; print((datetime.datetime.utcnow()-datetime.timedelta(hours=48)).strftime('%Y-%m-%dT%H:%M:%SZ'))")
PR_MERGED=$(python3 -c "import datetime; print((datetime.datetime.utcnow()-datetime.timedelta(hours=2)).strftime('%Y-%m-%dT%H:%M:%SZ'))")
PR_BODY='{"action":"closed","pull_request":{"id":777001,"created_at":"'"$PR_CREATED"'","merged_at":"'"$PR_MERGED"'","base":{"repo":{"full_name":"ai-impact-evaluation/demo"}}}}'
post_github pull_request "prlead-$DELIVERY_ID" "$PR_BODY"
# A hotfix deploy after the deploy → marks it failed (CFR) and gives a restore time (MTTR).
HOTFIX_BODY='{"action":"completed","workflow_run":{"id":555002,"name":"Hotfix production","conclusion":"success","status":"completed","updated_at":"'"$TODAY"'"},"repository":{"full_name":"ai-impact-evaluation/demo"}}'
post_github workflow_run "hf-$DELIVERY_ID" "$HOTFIX_BODY"
sleep 3
curl -sf -X POST http://localhost:8084/internal/recompute >/dev/null || { echo "FAIL: recompute trigger failed"; exit 1; }

# RBAC: the Cockpit endpoint now requires a bearer token (E8-S1).
NOAUTH=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:8080/api/v1/metrics/cockpit?days=30")
[ "$NOAUTH" = "401" ] || { echo "FAIL: unauthenticated cockpit returned $NOAUTH (expected 401)"; exit 1; }
LEAD_TOKEN=$(curl -s -X POST "http://localhost:8080/api/v1/auth/dev-token?email=lead@demo&role=ENG_LEADER" \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['token'])")
COCKPIT=$(curl -sf "http://localhost:8080/api/v1/metrics/cockpit?days=30" -H "Authorization: Bearer $LEAD_TOKEN")
read DEPLOYS LEAD CFR < <(echo "$COCKPIT" | python3 -c "
import json,sys
d = json.load(sys.stdin)
t = {x['metricKey']: x['aggregate'] for x in d['tiles']}
def s(v): return 'null' if v is None else v
print(s(t['deployment_frequency']), s(t['lead_time_p50_hours']), s(t['change_failure_rate']))")
python3 -c "import sys; sys.exit(0 if float('$DEPLOYS') >= 1 else 1)" \
  || { echo "FAIL: deployment_frequency = $DEPLOYS (expected >= 1)"; exit 1; }
python3 -c "import sys; sys.exit(0 if '$LEAD' != 'null' and float('$LEAD') > 0 else 1)" \
  || { echo "FAIL: lead_time_p50_hours = $LEAD (expected a positive number)"; exit 1; }
python3 -c "import sys; sys.exit(0 if '$CFR' != 'null' and 0 <= float('$CFR') <= 1 else 1)" \
  || { echo "FAIL: change_failure_rate = $CFR (expected a ratio in [0,1])"; exit 1; }
echo "  RBAC enforced (401 without token); ENG_LEADER token -> all four DORA metrics:"
echo "    deployment_frequency=$DEPLOYS  lead_time_hours=$LEAD  change_failure_rate=$CFR"

echo "8/10 Verifying identity resolution (jira actor -> core.contributor)..."
for _ in $(seq 1 15); do
  IDROW=$(docker compose -f "$ROOT/infra/docker-compose.yml" exec -T postgres \
    psql -U aiimpacteval -d aiimpacteval -tAc \
    "SELECT c.canonical_name || '|' || a.source FROM core.contributor c JOIN core.contributor_alias a ON a.contributor_id = c.id WHERE a.source='jira' LIMIT 1")
  if [ -n "$IDROW" ]; then break; fi
  sleep 2
done
[ -n "$IDROW" ] || { echo "FAIL: no jira contributor resolved"; exit 1; }
echo "  contributor resolved: $IDROW"

echo "9/10 Verifying team import (E2-S2) and org -> team drill-down (E4-S2)..."
TEAM_MARKER="e2e-team-$DELIVERY_ID"
TEAM_BODY='{"id":9001,"name":"'"$TEAM_MARKER"'","slug":"smoke-team","repositories":[{"full_name":"ai-impact-evaluation/demo"}],"members":[{"id":606060,"login":"smoke-member"}]}'
post_github team.snapshot "team-$DELIVERY_ID" "$TEAM_BODY"

for _ in $(seq 1 15); do
  TEAM_ID=$(docker compose -f "$ROOT/infra/docker-compose.yml" exec -T postgres \
    psql -U aiimpacteval -d aiimpacteval -tAc "SELECT id FROM core.team WHERE name = '$TEAM_MARKER'")
  if [ -n "$TEAM_ID" ]; then break; fi
  sleep 2
done
[ -n "$TEAM_ID" ] || { echo "FAIL: team not imported into core.team"; exit 1; }
TEAM_REPO_ROW=$(docker compose -f "$ROOT/infra/docker-compose.yml" exec -T postgres \
  psql -U aiimpacteval -d aiimpacteval -tAc "SELECT repo FROM core.team_repo WHERE team_id = '$TEAM_ID'")
[ "$TEAM_REPO_ROW" = "ai-impact-evaluation/demo" ] || { echo "FAIL: team_repo mapping missing/wrong: '$TEAM_REPO_ROW'"; exit 1; }
TEAM_MEMBER_COUNT=$(docker compose -f "$ROOT/infra/docker-compose.yml" exec -T postgres \
  psql -U aiimpacteval -d aiimpacteval -tAc "SELECT count(*) FROM core.team_member WHERE team_id = '$TEAM_ID'")
[ "$TEAM_MEMBER_COUNT" = "1" ] || { echo "FAIL: team_member count = $TEAM_MEMBER_COUNT (expected 1)"; exit 1; }
echo "  team imported: repo mapped, 1 member resolved"

# Recompute again now that the team->repo mapping exists, so team-scoped mart rows populate.
curl -sf -X POST http://localhost:8084/internal/recompute >/dev/null

TEAM_LISTED=$(curl -sf http://localhost:8080/api/v1/teams -H "Authorization: Bearer $LEAD_TOKEN" \
  | python3 -c "
import json,sys
teams = json.load(sys.stdin)
print(any(t['id']=='$TEAM_ID' for t in teams))")
[ "$TEAM_LISTED" = "True" ] || { echo "FAIL: GET /api/v1/teams did not include the imported team"; exit 1; }

TEAM_DEPLOYS=$(curl -sf "http://localhost:8080/api/v1/metrics/cockpit?days=30&scope=$TEAM_ID" \
  -H "Authorization: Bearer $LEAD_TOKEN" | python3 -c "
import json,sys
d = json.load(sys.stdin)
tile = next(t for t in d['tiles'] if t['metricKey'] == 'deployment_frequency')
print(tile['aggregate'] if tile['aggregate'] is not None else 0)")
python3 -c "import sys; sys.exit(0 if float('$TEAM_DEPLOYS') >= 1 else 1)" \
  || { echo "FAIL: team-scoped deployment_frequency = $TEAM_DEPLOYS (expected >= 1)"; exit 1; }
echo "  drill-down works: team listed via GET /api/v1/teams; scoped Cockpit deployment_frequency=$TEAM_DEPLOYS"

echo "10/10 Verifying audit trail (E8-S3: admin-only, records token issuance)..."
# Non-admin is forbidden from the audit log.
AUDIT_FORBIDDEN=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:8080/api/v1/audit" \
  -H "Authorization: Bearer $LEAD_TOKEN")
[ "$AUDIT_FORBIDDEN" = "403" ] || { echo "FAIL: ENG_LEADER audit read returned $AUDIT_FORBIDDEN (expected 403)"; exit 1; }
ADMIN_TOKEN=$(curl -s -X POST "http://localhost:8080/api/v1/auth/dev-token?email=admin@demo&role=ADMIN" \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['token'])")
AUDIT_HAS_ISSUE=$(curl -sf "http://localhost:8080/api/v1/audit?limit=50" -H "Authorization: Bearer $ADMIN_TOKEN" \
  | python3 -c "import json,sys; print(any(e['action']=='AUTH_TOKEN_ISSUED' for e in json.load(sys.stdin)))")
[ "$AUDIT_HAS_ISSUE" = "True" ] || { echo "FAIL: audit log missing AUTH_TOKEN_ISSUED entry"; exit 1; }
echo "  audit: ENG_LEADER forbidden (403); ADMIN sees AUTH_TOKEN_ISSUED entries"

echo "PASS — full pipeline verified: webhooks -> staging -> identity + team import + metrics -> RBAC'd Cockpit drill-down + audit."
