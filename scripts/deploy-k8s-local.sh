#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

kubectl apply -f "$ROOT_DIR/infra/k8s/base/namespace.yaml"
kubectl wait --for=jsonpath='{.status.phase}'=Active namespace/smartdoc --timeout=60s
kubectl apply -f "$ROOT_DIR/infra/k8s/base/app-configmap.yaml"
kubectl apply -f "$ROOT_DIR/infra/k8s/base/app-secret.yaml"
kubectl apply -f "$ROOT_DIR/infra/k8s/base/gateway-service.yaml"
kubectl apply -f "$ROOT_DIR/infra/k8s/base/document-service.yaml"
kubectl apply -f "$ROOT_DIR/infra/k8s/base/analysis-service.yaml"
kubectl apply -f "$ROOT_DIR/infra/k8s/base/notification-service.yaml"
kubectl apply -f "$ROOT_DIR/infra/k8s/base/gateway-deployment.yaml"
kubectl apply -f "$ROOT_DIR/infra/k8s/base/document-deployment.yaml"
kubectl apply -f "$ROOT_DIR/infra/k8s/base/analysis-deployment.yaml"
kubectl apply -f "$ROOT_DIR/infra/k8s/base/notification-deployment.yaml"
kubectl apply -f "$ROOT_DIR/infra/k8s/base/gateway-ingress.yaml"
kubectl -n smartdoc rollout status deployment/smartdoc-document --timeout=180s
kubectl -n smartdoc rollout status deployment/smartdoc-analysis --timeout=180s
kubectl -n smartdoc rollout status deployment/smartdoc-notification --timeout=180s
kubectl -n smartdoc rollout status deployment/smartdoc-gateway --timeout=180s
