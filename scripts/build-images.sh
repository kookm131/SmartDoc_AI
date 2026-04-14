#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ "$#" -gt 0 ]]; then
  SERVICES=("$@")
else
  SERVICES=(gateway document analysis notification)
fi

for service in "${SERVICES[@]}"; do
  echo "Building smartdoc/$service:local"
  docker build -t "smartdoc/$service:local" "$ROOT_DIR/backend/services/$service"
done
