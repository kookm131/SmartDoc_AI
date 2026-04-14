#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PIDS=()

cleanup() {
  for pid in "${PIDS[@]}"; do
    if kill -0 "$pid" >/dev/null 2>&1; then
      kill "$pid" >/dev/null 2>&1 || true
    fi
  done
}

trap cleanup EXIT INT TERM

kubectl -n smartdoc wait --for=condition=available deployment/smartdoc-document --timeout=180s
kubectl -n smartdoc wait --for=condition=available deployment/smartdoc-analysis --timeout=180s
kubectl -n smartdoc wait --for=condition=available deployment/smartdoc-notification --timeout=180s

kubectl -n smartdoc port-forward service/smartdoc-document 18081:8081 >/tmp/smartdoc-document-port-forward.log 2>&1 &
PIDS+=("$!")
kubectl -n smartdoc port-forward service/smartdoc-analysis 18082:8082 >/tmp/smartdoc-analysis-port-forward.log 2>&1 &
PIDS+=("$!")
kubectl -n smartdoc port-forward service/smartdoc-notification 18083:8083 >/tmp/smartdoc-notification-port-forward.log 2>&1 &
PIDS+=("$!")

sleep 5

DOCUMENT_BASE_URL=http://localhost:18081 \
ANALYSIS_BASE_URL=http://localhost:18082 \
NOTIFICATION_BASE_URL=http://localhost:18083 \
  "$ROOT_DIR/scripts/smoke-local.sh"
