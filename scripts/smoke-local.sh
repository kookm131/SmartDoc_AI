#!/usr/bin/env bash
set -euo pipefail

DOCUMENT_BASE_URL="${DOCUMENT_BASE_URL:-http://localhost:8081}"
ANALYSIS_BASE_URL="${ANALYSIS_BASE_URL:-http://localhost:8082}"
NOTIFICATION_BASE_URL="${NOTIFICATION_BASE_URL:-http://localhost:8083}"

extract_json_string() {
  local key="$1"
  sed -n "s/.*\"$key\":\"\\([^\"]*\\)\".*/\\1/p"
}

echo "Checking health endpoints..."
curl -fsS "$DOCUMENT_BASE_URL/api/v1/health" >/dev/null
curl -fsS "$ANALYSIS_BASE_URL/api/v1/health" >/dev/null
curl -fsS "$NOTIFICATION_BASE_URL/api/v1/health" >/dev/null
echo "Health checks passed."

echo "Creating document..."
document_response="$(
  curl -fsS -X POST "$DOCUMENT_BASE_URL/api/v1/documents" \
    -H "Content-Type: application/json" \
    -d '{"filename":"smoke-test.pdf","fileKey":"uploads/smoke-test.pdf","contentType":"application/pdf"}'
)"
document_id="$(printf '%s' "$document_response" | extract_json_string "documentId")"

if [[ -z "$document_id" ]]; then
  echo "Could not parse documentId from response: $document_response"
  exit 1
fi

echo "Document created: $document_id"

echo "Creating analysis job..."
analysis_response="$(
  curl -fsS -X POST "$ANALYSIS_BASE_URL/api/v1/analysis/jobs" \
    -H "Content-Type: application/json" \
    -d "{\"documentId\":\"$document_id\"}"
)"
job_id="$(printf '%s' "$analysis_response" | extract_json_string "jobId")"

if [[ -z "$job_id" ]]; then
  echo "Could not parse jobId from response: $analysis_response"
  exit 1
fi

echo "Analysis job created: $job_id"

echo "Waiting for analysis state transition..."
sleep 5
analysis_status_response="$(curl -fsS "$ANALYSIS_BASE_URL/api/v1/analysis/jobs/$job_id")"
analysis_state="$(printf '%s' "$analysis_status_response" | extract_json_string "state")"

if [[ "$analysis_state" != "COMPLETED" ]]; then
  echo "Expected analysis state COMPLETED, got: $analysis_status_response"
  exit 1
fi

document_status_response="$(curl -fsS "$DOCUMENT_BASE_URL/api/v1/documents/$document_id")"
document_status="$(printf '%s' "$document_status_response" | extract_json_string "status")"

if [[ "$document_status" != "ANALYSIS_COMPLETED" ]]; then
  echo "Expected document status ANALYSIS_COMPLETED, got: $document_status_response"
  exit 1
fi

echo "Analysis completed and document status synced."

echo "Dispatching notification..."
notification_response="$(
  curl -fsS -X POST "$NOTIFICATION_BASE_URL/api/v1/notifications/dispatch" \
    -H "Content-Type: application/json" \
    -d "{\"documentId\":\"$document_id\",\"channel\":\"slack\",\"message\":\"Smoke test notification\"}"
)"
event_id="$(printf '%s' "$notification_response" | extract_json_string "eventId")"

if [[ -z "$event_id" ]]; then
  echo "Could not parse eventId from response: $notification_response"
  exit 1
fi

curl -fsS "$NOTIFICATION_BASE_URL/api/v1/notifications/events/$event_id" >/dev/null
echo "Notification dispatched and fetched: $event_id"

echo "Smoke test passed."
