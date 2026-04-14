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

kubectl -n smartdoc wait --for=condition=available deployment/smartdoc-gateway --timeout=180s
kubectl -n smartdoc wait --for=condition=available deployment/smartdoc-document --timeout=180s
kubectl -n smartdoc wait --for=condition=available deployment/smartdoc-analysis --timeout=180s
kubectl -n smartdoc wait --for=condition=available deployment/smartdoc-notification --timeout=180s

kubectl -n smartdoc port-forward service/smartdoc-gateway 18080:8080 >/tmp/smartdoc-gateway-port-forward.log 2>&1 &
PIDS+=("$!")

sleep 5

GATEWAY_BASE_URL=http://localhost:18080 "$ROOT_DIR/scripts/smoke-gateway.sh"
