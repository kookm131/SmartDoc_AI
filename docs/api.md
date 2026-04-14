# API

## Gateway
- `GET /`
- `GET /api/v1/health`

### `GET /` 응답 예시 (`200 OK`)
```json
{
  "service": "gateway",
  "message": "gateway up"
}
```

### `GET /api/v1/health` 응답 예시 (`200 OK`)
```json
{
  "service": "gateway",
  "status": "ok"
}
```

## Document
- `POST /api/v1/documents`
- `GET /api/v1/documents/{id}`
- `GET /api/v1/documents`
- `PATCH /api/v1/documents/{id}/status`
- `POST /api/v1/documents/{id}/status` (서비스 간 내부 호출 호환용)

### `POST /api/v1/documents` DTO
요청:
```json
{
  "filename": "invoice-2026-04.pdf",
  "fileKey": "uploads/invoice-2026-04.pdf",
  "contentType": "application/pdf"
}
```

응답 (`201 Created`):
```json
{
  "documentId": "uuid",
  "filename": "invoice-2026-04.pdf",
  "fileKey": "uploads/invoice-2026-04.pdf",
  "contentType": "application/pdf",
  "status": "RECEIVED",
  "createdAt": "2026-04-13T07:00:00Z",
  "updatedAt": "2026-04-13T07:00:00Z"
}
```

로컬 저장소:
- 현재 `document` 서비스는 JPA + H2(in-memory)로 시작하며, 이후 MSSQL로 전환 예정
- 문서 상태: `RECEIVED`, `ANALYSIS_QUEUED`, `ANALYSIS_PROCESSING`, `ANALYSIS_COMPLETED`

### `POST /api/v1/documents` 실행 예시
```bash
curl -X POST http://localhost:8081/api/v1/documents \
  -H "Content-Type: application/json" \
  -d '{
    "filename":"invoice-2026-04.pdf",
    "fileKey":"uploads/invoice-2026-04.pdf",
    "contentType":"application/pdf"
  }'
```

### 검증 실패 예시 (`400 Bad Request`)
```json
{
  "timestamp": "2026-04-13T12:00:00Z",
  "path": "/api/v1/documents",
  "code": "VALIDATION_ERROR",
  "message": "filename must not be blank",
  "traceId": "trace-id"
}
```

### `GET /api/v1/documents/{id}` 응답 예시 (`200 OK`)
```json
{
  "documentId": "uuid",
  "filename": "invoice-2026-04.pdf",
  "fileKey": "uploads/invoice-2026-04.pdf",
  "contentType": "application/pdf",
  "status": "RECEIVED",
  "createdAt": "2026-04-13T07:00:00Z",
  "updatedAt": "2026-04-13T07:00:00Z"
}
```

### `GET /api/v1/documents` 응답 예시 (`200 OK`)
```json
[
  {
    "documentId": "uuid",
    "filename": "invoice-2026-04.pdf",
    "fileKey": "uploads/invoice-2026-04.pdf",
    "contentType": "application/pdf",
    "status": "RECEIVED",
    "createdAt": "2026-04-13T07:00:00Z",
    "updatedAt": "2026-04-13T07:00:00Z"
  }
]
```

### `PATCH /api/v1/documents/{id}/status` 요청/응답 예시
요청:
```json
{
  "status": "ANALYSIS_COMPLETED"
}
```

응답 (`200 OK`):
```json
{
  "documentId": "uuid",
  "filename": "invoice-2026-04.pdf",
  "fileKey": "uploads/invoice-2026-04.pdf",
  "contentType": "application/pdf",
  "status": "ANALYSIS_COMPLETED",
  "createdAt": "2026-04-13T07:00:00Z",
  "updatedAt": "2026-04-14T08:00:05Z"
}
```

## Analysis
- `POST /api/v1/analysis/jobs`
- `GET /api/v1/analysis/jobs/{id}`

로컬 저장소:
- 현재 `analysis` 서비스는 JPA + H2(in-memory)로 `analysis_jobs`를 저장하며, 이후 MSSQL로 전환 예정
- `analysis_jobs` 최소 필드: `id`, `document_id`, `state`, `analysis_provider`, `created_at`
- 로컬 stub 상태 전이: `QUEUED` -> `PROCESSING` -> `COMPLETED`

### `POST /api/v1/analysis/jobs` 요청/응답 예시
요청:
```json
{
  "documentId": "uuid"
}
```

응답 (`201 Created`):
```json
{
  "jobId": "uuid",
  "documentId": "uuid",
  "state": "QUEUED",
  "createdAt": "2026-04-14T08:00:00Z",
  "analysisProvider": "local-stub"
}
```

주의:
- `documentId`가 실제로 존재하지 않으면 `404 RESOURCE_NOT_FOUND`를 반환합니다.
- 생성된 Job은 DB에 저장되며, `GET /api/v1/analysis/jobs/{id}`는 DB에서 조회합니다.
- `analysis` 상태 전이에 따라 `document` 상태도 `ANALYSIS_QUEUED`, `ANALYSIS_PROCESSING`, `ANALYSIS_COMPLETED`로 갱신합니다.
- `analysis -> document` 호출은 기본 connect timeout `1000ms`, read timeout `2000ms`를 사용합니다.

### `GET /api/v1/analysis/jobs/{id}` 응답 예시 (`200 OK`)
```json
{
  "jobId": "uuid",
  "documentId": "uuid",
  "state": "COMPLETED",
  "createdAt": "2026-04-14T08:00:00Z",
  "analysisProvider": "local-stub"
}
```

## Notification
- `POST /api/v1/notifications/dispatch`
- `GET /api/v1/notifications/events`
- `GET /api/v1/notifications/events/{id}`

로컬 저장소:
- 현재 `notification` 서비스는 JPA + H2(in-memory)로 `notification_events`를 저장하며, 이후 MSSQL로 전환 예정
- `notification_events` 최소 필드: `id`, `document_id`, `channel`, `message`, `status`, `created_at`

### `POST /api/v1/notifications/dispatch` 요청/응답 예시
요청:
```json
{
  "documentId": "uuid",
  "channel": "slack",
  "message": "긴급 검토가 필요한 문서입니다."
}
```

응답 (`201 Created`):
```json
{
  "eventId": "uuid",
  "documentId": "uuid",
  "channel": "slack",
  "message": "긴급 검토가 필요한 문서입니다.",
  "status": "DISPATCHED",
  "createdAt": "2026-04-14T09:00:00Z"
}
```

### `GET /api/v1/notifications/events` 응답 예시 (`200 OK`)
```json
[
  {
    "eventId": "uuid",
    "documentId": "uuid",
    "channel": "slack",
    "message": "긴급 검토가 필요한 문서입니다.",
    "status": "DISPATCHED",
    "createdAt": "2026-04-14T09:00:00Z"
  }
]
```

## 오류 모델
- 공통 필드: `timestamp`, `path`, `code`, `message`, `traceId`
- 기본 코드: `VALIDATION_ERROR`, `RESOURCE_NOT_FOUND`, `UPSTREAM_DOCUMENT_ERROR`, `UPSTREAM_AI_ERROR`, `INTERNAL_ERROR`
- `UPSTREAM_DOCUMENT_ERROR`: `analysis`가 `document` 서비스에 연결하지 못했을 때 `502 Bad Gateway`로 반환합니다.
- 모든 서비스는 예상하지 못한 오류를 `INTERNAL_ERROR` 형태로 반환합니다.
