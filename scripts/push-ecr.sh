#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ "$#" -gt 0 ]]; then
  SERVICES=("$@")
else
  SERVICES=(gateway document analysis notification)
fi

for env_file in "$ROOT_DIR/.env" "$ROOT_DIR/.env.local"; do
  if [[ -f "$env_file" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$env_file"
    set +a
  fi
done

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "missing required command: $1" >&2
    exit 1
  }
}

require_cmd docker
require_cmd aws
require_cmd git

AWS_REGION="${AWS_REGION:-${SMARTDOC_AWS_REGION:-us-east-1}}"
ECR_REGISTRY="${SMARTDOC_ECR_REGISTRY:-}"
if [[ -z "$ECR_REGISTRY" ]]; then
  if [[ -n "${AWS_ACCOUNT_ID:-}" ]]; then
    ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
  fi
fi

if [[ -z "$ECR_REGISTRY" ]]; then
  echo "set SMARTDOC_ECR_REGISTRY (e.g. 123456789012.dkr.ecr.${AWS_REGION}.amazonaws.com)" >&2
  echo "or set AWS_ACCOUNT_ID to derive registry automatically" >&2
  exit 1
fi

GIT_SHA="$(git -C "$ROOT_DIR" rev-parse --short HEAD 2>/dev/null || true)"
if [[ -z "$GIT_SHA" ]]; then
  echo "failed to determine git sha; is this a git repository?" >&2
  exit 1
fi

echo "ECR registry: $ECR_REGISTRY"
echo "Region: $AWS_REGION"
echo "Tag: $GIT_SHA (+ latest)"

echo "Logging in to ECR..."
aws ecr get-login-password --region "$AWS_REGION" \
  | docker login --username AWS --password-stdin "$ECR_REGISTRY" >/dev/null

ensure_repo() {
  local repo="$1"
  if aws ecr describe-repositories --region "$AWS_REGION" --repository-names "$repo" >/dev/null 2>&1; then
    return 0
  fi
  echo "Creating ECR repository: $repo"
  aws ecr create-repository --region "$AWS_REGION" --repository-name "$repo" >/dev/null
}

for service in "${SERVICES[@]}"; do
  local_image="smartdoc/${service}:local"
  repo="smartdoc-${service}"
  remote_sha="${ECR_REGISTRY}/${repo}:${GIT_SHA}"
  remote_latest="${ECR_REGISTRY}/${repo}:latest"

  echo "== $service =="
  ensure_repo "$repo"

  echo "Building ${local_image}"
  "$ROOT_DIR/scripts/build-images.sh" "$service" >/dev/null

  echo "Tagging -> ${remote_sha}"
  docker tag "$local_image" "$remote_sha"
  echo "Tagging -> ${remote_latest}"
  docker tag "$local_image" "$remote_latest"

  echo "Pushing ${remote_sha}"
  docker push "$remote_sha" >/dev/null
  echo "Pushing ${remote_latest}"
  docker push "$remote_latest" >/dev/null
done

echo "ECR push completed."
