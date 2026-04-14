#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" -gt 0 ]]; then
  SERVICES=("$@")
else
  SERVICES=(gateway document analysis notification)
fi

if command -v kind >/dev/null 2>&1; then
  LOADER=(kind load docker-image)
elif command -v minikube >/dev/null 2>&1; then
  LOADER=(minikube image load)
else
  echo "kind or minikube is required to load local images into Kubernetes"
  exit 1
fi

for service in "${SERVICES[@]}"; do
  echo "Loading smartdoc/$service:local"
  "${LOADER[@]}" "smartdoc/$service:local"
done
