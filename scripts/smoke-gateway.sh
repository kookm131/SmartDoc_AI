#!/usr/bin/env bash
set -euo pipefail

GATEWAY_BASE_URL="${GATEWAY_BASE_URL:-http://localhost:8080}"
DOCUMENT_BASE_URL="${DOCUMENT_BASE_URL:-http://localhost:8081}"
ANALYSIS_BASE_URL="${ANALYSIS_BASE_URL:-http://localhost:8082}"
NOTIFICATION_BASE_URL="${NOTIFICATION_BASE_URL:-http://localhost:8083}"
TRACE_ID="${TRACE_ID:-$(cat /proc/sys/kernel/random/uuid)}"
trace_header="X-SmartDoc-Trace-Id: $TRACE_ID"

wait_for_http() {
  local url="$1"
  local label="$2"
  local attempts="${3:-30}"
  local delay_seconds="${4:-1}"

  for ((i = 1; i <= attempts; i++)); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      echo "$label ready."
      return 0
    fi
    sleep "$delay_seconds"
  done

  echo "Timed out waiting for $label at $url"
  exit 1
}

extract_json_string() {
  local key="$1"
  sed -n "s/.*\"$key\":\"\\([^\"]*\\)\".*/\\1/p"
}

wait_for_analysis_completed() {
  local job_id="$1"
  local attempts="${2:-20}"
  local delay_seconds="${3:-1}"
  local response_file
  local http_status

  response_file="$(mktemp /tmp/smartdoc-gateway-analysis-status-XXXXXX.json)"

  for ((i = 1; i <= attempts; i++)); do
    http_status="$(
      curl -sS -o "$response_file" -w "%{http_code}" \
        -H "$auth_header" \
        -H "$trace_header" \
        "$GATEWAY_BASE_URL/api/v1/analysis/jobs/$job_id"
    )"
    analysis_status_response="$(cat "$response_file")"

    if [[ "$http_status" != "200" ]]; then
      echo "Analysis status request failed. HTTP $http_status"
      echo "Response:"
      cat "$response_file"
      rm -f "$response_file"
      echo
      echo "Trace: $TRACE_ID"
      exit 1
    fi

    analysis_state="$(printf '%s' "$analysis_status_response" | extract_json_string "state")"

    if [[ "$analysis_state" == "COMPLETED" ]]; then
      rm -f "$response_file"
      return 0
    fi

    if [[ "$analysis_state" == "FAILED" ]]; then
      echo "Analysis job failed: $analysis_status_response"
      rm -f "$response_file"
      exit 1
    fi

    sleep "$delay_seconds"
  done

  echo "Timed out waiting for analysis state COMPLETED, got: $analysis_status_response"
  rm -f "$response_file"
  exit 1
}

echo "Checking gateway health..."
wait_for_http "$GATEWAY_BASE_URL/api/v1/health" "Gateway health"
echo "Gateway health check passed."

echo "Waiting for upstream services..."
wait_for_http "$DOCUMENT_BASE_URL/api/v1/health" "Document service health"
wait_for_http "$ANALYSIS_BASE_URL/api/v1/health" "Analysis service health"
wait_for_http "$NOTIFICATION_BASE_URL/api/v1/health" "Notification service health"

echo "Logging in through gateway..."
auth_response="$(
  curl -fsS -X POST "$GATEWAY_BASE_URL/api/v1/auth/login" \
    -H "$trace_header" \
    -H "Content-Type: application/json" \
    -d '{"email":"test@smartdoc.local","password":"password"}'
)"
access_token="$(printf '%s' "$auth_response" | extract_json_string "accessToken")"

if [[ -z "$access_token" ]]; then
  echo "Could not parse accessToken from response: $auth_response"
  exit 1
fi

auth_header="Authorization: Bearer $access_token"

echo "Ensuring notification rule through gateway..."
rule_out="$(mktemp /tmp/smartdoc-gateway-smoke-rule-XXXXXX.json)"
rule_http="$(
  curl -sS -o "$rule_out" -w "%{http_code}" -X POST "$GATEWAY_BASE_URL/api/v1/notifications/rules" \
    -H "$auth_header" \
    -H "$trace_header" \
    -H "Content-Type: application/json" \
    -d '{"keyword":"계약","channel":"slack","enabled":true}'
)"
if [[ "$rule_http" != "200" && "$rule_http" != "201" ]]; then
  echo "Failed to ensure notification rule. HTTP $rule_http"
  echo "Response:"
  cat "$rule_out"
  rm -f "$rule_out"
  echo
  echo "Hint: check gateway upstream config SMARTDOC_NOTIFICATION_BASE_URL and whether notification service is running."
  echo "Trace: $TRACE_ID"
  exit 1
fi
rm -f "$rule_out"

upload_file="$(mktemp /tmp/smartdoc-gateway-smoke-XXXXXX.txt)"
printf '긴급 계약 검토 알림이 필요한 SmartDoc gateway smoke test document\n' > "$upload_file"

echo "Uploading document through gateway..."
document_response="$(
  curl -fsS -X POST "$GATEWAY_BASE_URL/api/v1/documents/upload" \
    -H "$auth_header" \
    -H "$trace_header" \
    -F "file=@$upload_file;type=text/plain"
)"
rm -f "$upload_file"
document_id="$(printf '%s' "$document_response" | extract_json_string "documentId")"

if [[ -z "$document_id" ]]; then
  echo "Could not parse documentId from response: $document_response"
  exit 1
fi

echo "Document uploaded: $document_id"

echo "Creating analysis job through gateway..."
analysis_response="$(
  curl -fsS -X POST "$GATEWAY_BASE_URL/api/v1/analysis/jobs" \
    -H "$auth_header" \
    -H "$trace_header" \
    -H "Content-Type: application/json" \
    -d "{\"documentId\":\"$document_id\"}"
)"
job_id="$(printf '%s' "$analysis_response" | extract_json_string "jobId")"

if [[ -z "$job_id" ]]; then
  echo "Could not parse jobId from response: $analysis_response"
  exit 1
fi

echo "Waiting for analysis completion through gateway..."
wait_for_analysis_completed "$job_id"

document_status_response="$(curl -fsS -H "$auth_header" -H "$trace_header" "$GATEWAY_BASE_URL/api/v1/documents/$document_id")"
document_status="$(printf '%s' "$document_status_response" | extract_json_string "status")"

if [[ "$document_status" != "ANALYSIS_COMPLETED" ]]; then
  echo "Expected document status ANALYSIS_COMPLETED, got: $document_status_response"
  exit 1
fi

echo "Analysis completed and document status synced through gateway."

echo "Checking automatic notification through gateway..."
notification_events_response="$(curl -fsS -H "$auth_header" -H "$trace_header" "$GATEWAY_BASE_URL/api/v1/notifications/events")"

if ! printf '%s' "$notification_events_response" | grep -q "\"documentId\":\"$document_id\""; then
  echo "Expected automatic notification event for document $document_id, got: $notification_events_response"
  exit 1
fi

if ! printf '%s' "$notification_events_response" | grep -q "분석 완료"; then
  echo "Expected automatic notification message, got: $notification_events_response"
  exit 1
fi

echo "Gateway smoke test passed."
