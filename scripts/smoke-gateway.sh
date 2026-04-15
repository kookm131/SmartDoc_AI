#!/usr/bin/env bash
set -euo pipefail

GATEWAY_BASE_URL="${GATEWAY_BASE_URL:-http://localhost:8080}"

extract_json_string() {
  local key="$1"
  sed -n "s/.*\"$key\":\"\\([^\"]*\\)\".*/\\1/p"
}

echo "Checking gateway health..."
curl -fsS "$GATEWAY_BASE_URL/api/v1/health" >/dev/null
echo "Gateway health check passed."

echo "Logging in through gateway..."
auth_response="$(
  curl -fsS -X POST "$GATEWAY_BASE_URL/api/v1/auth/login" \
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
curl -fsS -X POST "$GATEWAY_BASE_URL/api/v1/notifications/rules" \
  -H "$auth_header" \
  -H "Content-Type: application/json" \
  -d '{"keyword":"계약","channel":"slack","enabled":true}' >/dev/null

upload_file="$(mktemp /tmp/smartdoc-gateway-smoke-XXXXXX.txt)"
printf '긴급 계약 검토 알림이 필요한 SmartDoc gateway smoke test document\n' > "$upload_file"

echo "Uploading document through gateway..."
document_response="$(
  curl -fsS -X POST "$GATEWAY_BASE_URL/api/v1/documents/upload" \
    -H "$auth_header" \
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
    -H "Content-Type: application/json" \
    -d "{\"documentId\":\"$document_id\"}"
)"
job_id="$(printf '%s' "$analysis_response" | extract_json_string "jobId")"

if [[ -z "$job_id" ]]; then
  echo "Could not parse jobId from response: $analysis_response"
  exit 1
fi

sleep 5
analysis_status_response="$(curl -fsS -H "$auth_header" "$GATEWAY_BASE_URL/api/v1/analysis/jobs/$job_id")"
analysis_state="$(printf '%s' "$analysis_status_response" | extract_json_string "state")"

if [[ "$analysis_state" != "COMPLETED" ]]; then
  echo "Expected analysis state COMPLETED, got: $analysis_status_response"
  exit 1
fi

document_status_response="$(curl -fsS -H "$auth_header" "$GATEWAY_BASE_URL/api/v1/documents/$document_id")"
document_status="$(printf '%s' "$document_status_response" | extract_json_string "status")"

if [[ "$document_status" != "ANALYSIS_COMPLETED" ]]; then
  echo "Expected document status ANALYSIS_COMPLETED, got: $document_status_response"
  exit 1
fi

echo "Analysis completed and document status synced through gateway."

echo "Checking automatic notification through gateway..."
notification_events_response="$(curl -fsS -H "$auth_header" "$GATEWAY_BASE_URL/api/v1/notifications/events")"

if ! printf '%s' "$notification_events_response" | grep -q "\"documentId\":\"$document_id\""; then
  echo "Expected automatic notification event for document $document_id, got: $notification_events_response"
  exit 1
fi

if ! printf '%s' "$notification_events_response" | grep -q "분석 완료"; then
  echo "Expected automatic notification message, got: $notification_events_response"
  exit 1
fi

echo "Gateway smoke test passed."
