#!/usr/bin/env bash
set -euo pipefail

SERVICE="${1:-}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -z "$SERVICE" ]]; then
  echo "usage: scripts/run-service.sh <gateway|document|analysis|notification>"
  exit 1
fi

SERVICE_DIR="$ROOT_DIR/backend/services/$SERVICE"

if [[ ! -d "$SERVICE_DIR" ]]; then
  echo "unknown service: $SERVICE"
  exit 1
fi

export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:--Xms128m -Xmx384m}"
export GRADLE_OPTS="${GRADLE_OPTS:--Dorg.gradle.jvmargs=-Xmx384m -Dorg.gradle.daemon=false}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ROOT_DIR/.gradle-local}"

PROJECT_CACHE_DIR="${PROJECT_CACHE_DIR:-/tmp/smartdoc-${SERVICE}-projcache}"

cd "$SERVICE_DIR"
exec ./gradlew --no-daemon --project-cache-dir "$PROJECT_CACHE_DIR" bootRun
