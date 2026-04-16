#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

kubectl apply -k "$ROOT_DIR/infra/k8s/overlays/local"
kubectl -n smartdoc rollout status deployment/smartdoc-document --timeout=180s
kubectl -n smartdoc rollout status deployment/smartdoc-analysis --timeout=180s
kubectl -n smartdoc rollout status deployment/smartdoc-notification --timeout=180s
kubectl -n smartdoc rollout status deployment/smartdoc-gateway --timeout=180s
