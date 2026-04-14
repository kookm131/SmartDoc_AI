#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ "$#" -gt 0 ]]; then
  SERVICES=("$@")
else
  SERVICES=(document analysis notification)
fi
PIDS=()

cleanup() {
  for pid in "${PIDS[@]}"; do
    if kill -0 "$pid" >/dev/null 2>&1; then
      kill "$pid" >/dev/null 2>&1 || true
    fi
  done
}

trap cleanup EXIT INT TERM

echo "Starting SmartDoc backend services: ${SERVICES[*]}"
echo "Tip: on low-memory machines, prefer scripts/run-service.sh <service> in separate terminals."

for service in "${SERVICES[@]}"; do
  "$ROOT_DIR/scripts/run-service.sh" "$service" &
  PIDS+=("$!")
  sleep 3
done

wait
