#!/usr/bin/env bash
# Stops all backend services started by start-backend.sh. Infra (postgres/rabbitmq) is left
# running — use `docker compose -f infra/docker-compose.yml down` to stop that too.
set -uo pipefail

PID_FILE="/tmp/aiimpacteval-backend.pids"

if [ ! -f "$PID_FILE" ]; then
  echo "No $PID_FILE found — nothing to stop (were services started with start-backend.sh?)"
  exit 0
fi

while read -r pid; do
  [ -z "$pid" ] && continue
  if kill "$pid" 2>/dev/null; then
    echo "  stopped pid $pid"
  else
    echo "  pid $pid already stopped"
  fi
done < "$PID_FILE"

rm -f "$PID_FILE"
echo "Done."
