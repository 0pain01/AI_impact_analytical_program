#!/usr/bin/env bash
# Single-command local dev startup: infra (Postgres + RabbitMQ) + all eight backend services.
# connector-ai-telemetry needs CLAUDE_CODE_USAGE_FILE / COPILOT_USAGE_FILE exported before
# running this script to actually backfill anything (it starts fine without them, same as
# connector-github starts fine without GITHUB_TOKEN) — point them at
# infra/sample-data/claude-code-usage-report.json / copilot-usage-report.ndjson locally.
# Builds once, starts each service detached, waits for /actuator/health, then returns control
# to the shell (services keep running in the background — use stop-backend.sh to tear down).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"
PID_FILE="/tmp/aiimpacteval-backend.pids"
: > "$PID_FILE"

echo "1/3 Starting infra (postgres + rabbitmq)..."
docker compose -f "$ROOT/infra/docker-compose.yml" up -d --wait

echo "2/3 Building all services (mvn package -DskipTests)..."
mvn -q -f "$ROOT/services/pom.xml" package -DskipTests

echo "3/3 Starting services..."
start() { # name, module-path, port
  "$JAVA_BIN" -jar "$ROOT/services/$2/target/$(basename "$2")"-*.jar \
    > "/tmp/aiimpacteval-$1.log" 2>&1 &
  echo "$!" >> "$PID_FILE"
  echo "  $1 starting on :$3 (log: /tmp/aiimpacteval-$1.log, pid $!)"
}

start api-core                 api-core                       8080
start ingestion-writer         ingestion-writer                8082
start connector-github         connectors/connector-github     8081
start connector-jira           connectors/connector-jira       8083
start metrics-engine           metrics-engine                  8084
start identity-service         identity-service                8085
start connector-jenkins        connectors/connector-jenkins    8086
start connector-ai-telemetry   connectors/connector-ai-telemetry 8087

wait_healthy() { # url, name
  for _ in $(seq 1 60); do
    if curl -sf "$1" >/dev/null 2>&1; then echo "  $2 healthy"; return 0; fi
    sleep 2
  done
  echo "FAIL: $2 did not become healthy — check its log in /tmp/aiimpacteval-*.log"; exit 1
}

echo "Waiting for health checks..."
wait_healthy http://localhost:8080/actuator/health "api-core"
wait_healthy http://localhost:8082/actuator/health "ingestion-writer"
wait_healthy http://localhost:8081/actuator/health "connector-github"
wait_healthy http://localhost:8083/actuator/health "connector-jira"
wait_healthy http://localhost:8084/actuator/health "metrics-engine"
wait_healthy http://localhost:8085/actuator/health "identity-service"
wait_healthy http://localhost:8086/actuator/health "connector-jenkins"
wait_healthy http://localhost:8087/actuator/health "connector-ai-telemetry"

echo
echo "All 8 backend services + infra are up. PIDs recorded in $PID_FILE."
echo "Frontend: cd frontend && npm install && npm run dev"
echo "Stop everything: ./infra/stop-backend.sh"
