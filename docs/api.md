# API

## Gateway
- `GET /`
- `GET /api/v1/health`
- `POST /api/v1/auth/signup`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/me`
- `POST /api/v1/documents`
- `POST /api/v1/documents/upload`
- `GET /api/v1/documents/{id}`
- `GET /api/v1/documents/{id}/content`
- `GET /api/v1/documents`
- `POST /api/v1/analysis/jobs`
- `GET /api/v1/analysis/jobs/{id}`
- `POST /api/v1/analysis/jobs/{id}/retry`
- `POST /api/v1/notifications/dispatch`
- `GET /api/v1/notifications/events`
- `GET /api/v1/notifications/events/{id}`
- `GET /api/v1/notifications/rules`
- `POST /api/v1/notifications/rules`

Gateway는 프론트엔드의 기본 API 진입점이며, document/analysis/notification 서비스로 요청을 프록시합니다.
Auth v1은 별도 서비스 없이 Gateway 내부 H2(in-memory)에 사용자 정보를 저장합니다.

인증 정책:
- 공개 API: `GET /`, `GET /api/v1/health`, `POST /api/v1/auth/signup`, `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`
- 보호 API: `GET /api/v1/auth/me` 및 document/analysis/notification 프록시 API 전체
- 보호 API 호출 시 `Authorization: Bearer <accessToken>` 헤더가 필요합니다.
- 로컬 기본 seed 계정: `test@smartdoc.local` / `password`
- Gateway는 인증된 사용자를 downstream 서비스에 `X-SmartDoc-User-Id`, `X-SmartDoc-User-Email` 헤더로 전달합니다.
- document/analysis/notification 서비스는 `X-SmartDoc-User-Id` 기준으로 사용자별 데이터를 분리합니다.

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

### `POST /api/v1/auth/login` 요청/응답 예시
요청:
```json
{
  "email": "test@smartdoc.local",
  "password": "password"
}
```

응답 (`200 OK`):
```json
{
  "user": {
    "userId": "uuid",
    "email": "test@smartdoc.local",
    "displayName": "SmartDoc Tester",
    "role": "USER"
  },
  "accessToken": "jwt-token",
  "tokenType": "Bearer",
  "expiresAt": "2026-04-15T07:00:00Z"
}
```

### `POST /api/v1/auth/signup` 요청/응답 예시
요청:
```json
{
  "email": "user@example.com",
  "password": "password123",
  "displayName": "SmartDoc User"
}
```

응답은 `POST /api/v1/auth/login`과 동일한 세션 DTO입니다.

### `GET /api/v1/auth/me` 응답 예시 (`200 OK`)
```json
{
  "userId": "uuid",
  "email": "test@smartdoc.local",
  "displayName": "SmartDoc Tester",
  "role": "USER"
}
```

### 인증 실패 예시 (`401 Unauthorized`)
```json
{
  "timestamp": "2026-04-14T07:00:00Z",
  "path": "/api/v1/documents",
  "code": "AUTHENTICATION_REQUIRED",
  "message": "authentication required",
  "traceId": "trace-id"
}
```

## Document
- `POST /api/v1/documents`
- `POST /api/v1/documents/upload`
- `GET /api/v1/documents/{id}`
- `GET /api/v1/documents/{id}/content`
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
  "ownerUserId": "uuid",
  "filename": "invoice-2026-04.pdf",
  "fileKey": "uploads/invoice-2026-04.pdf",
  "contentType": "application/pdf",
  "status": "RECEIVED",
  "createdAt": "2026-04-13T07:00:00Z",
  "updatedAt": "2026-04-13T07:00:00Z"
}
```

로컬 저장소:
- 현재 `document` 서비스는 기본 H2(in-memory) 또는 `mariadb` 프로필의 VM MariaDB로 `documents`를 저장합니다.
- 로컬 프로필에서 업로드 파일은 `SMARTDOC_LOCAL_UPLOAD_DIR`에 저장하며 기본값은 `.smartdoc/uploads`
- 업로드 최대 크기는 `SMARTDOC_MAX_UPLOAD_BYTES`로 조정하며 기본값은 `10485760` bytes(10MiB)입니다.
- 문서 상태: `RECEIVED`, `ANALYSIS_QUEUED`, `ANALYSIS_PROCESSING`, `ANALYSIS_COMPLETED`, `ANALYSIS_FAILED`
- 업로드 허용 확장자/content type 조합:
  - `.pdf`: `application/pdf`
  - `.txt`: `text/plain`
  - `.bin`: `application/octet-stream`

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

### `POST /api/v1/documents/upload` 실행 예시
```bash
curl -X POST http://localhost:8081/api/v1/documents/upload \
  -F "file=@./sample.pdf;type=application/pdf"
```

`fileKey`를 직접 지정해야 할 때:
```bash
curl -X POST http://localhost:8081/api/v1/documents/upload \
  -F "file=@./sample.pdf;type=application/pdf" \
  -F "fileKey=uploads/sample.pdf"
```

응답 (`201 Created`):
```json
{
  "documentId": "uuid",
  "ownerUserId": "uuid",
  "filename": "sample.pdf",
  "fileKey": "uploads/sample.pdf",
  "contentType": "application/pdf",
  "status": "RECEIVED",
  "createdAt": "2026-04-14T09:00:00Z",
  "updatedAt": "2026-04-14T09:00:00Z"
}
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

파일 크기 초과 시에도 동일한 `VALIDATION_ERROR` 형식으로 반환합니다.
```json
{
  "timestamp": "2026-04-13T12:00:00Z",
  "path": "/api/v1/documents/upload",
  "code": "VALIDATION_ERROR",
  "message": "file size must be less than or equal to 10485760 bytes",
  "traceId": "trace-id"
}
```

허용되지 않은 확장자나 확장자와 맞지 않는 content type도 `VALIDATION_ERROR`로 반환합니다.
```json
{
  "timestamp": "2026-04-13T12:00:00Z",
  "path": "/api/v1/documents/upload",
  "code": "VALIDATION_ERROR",
  "message": "unsupported contentType text/plain for .pdf file",
  "traceId": "trace-id"
}
```

### `GET /api/v1/documents/{id}` 응답 예시 (`200 OK`)
```json
{
  "documentId": "uuid",
  "ownerUserId": "uuid",
  "filename": "invoice-2026-04.pdf",
  "fileKey": "uploads/invoice-2026-04.pdf",
  "contentType": "application/pdf",
  "status": "RECEIVED",
  "createdAt": "2026-04-13T07:00:00Z",
  "updatedAt": "2026-04-13T07:00:00Z"
}
```

### `GET /api/v1/documents/{id}/content` 응답 예시 (`200 OK`)
`text/plain` 업로드 파일이면 로컬 분석용 텍스트 내용을 반환합니다. PDF는 아직 실제 파싱하지 않으므로 `textContent`가 `null`입니다.

```json
{
  "documentId": "uuid",
  "ownerUserId": "uuid",
  "fileKey": "uploads/sample.txt",
  "contentType": "text/plain",
  "textContent": "긴급 계약 검토 알림이 필요한 문서입니다."
}
```

### `GET /api/v1/documents` 응답 예시 (`200 OK`)
```json
[
  {
    "documentId": "uuid",
    "ownerUserId": "uuid",
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
  "ownerUserId": "uuid",
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
- `POST /api/v1/analysis/jobs/{id}/retry`

로컬 저장소:
- 현재 `analysis` 서비스는 기본 H2(in-memory) 또는 `mariadb` 프로필의 VM MariaDB로 `analysis_jobs`를 저장합니다.
- `analysis_jobs` 최소 필드: `id`, `owner_user_id`, `document_id`, `state`, `analysis_provider`, `result_summary`, `risk_score`, `keywords`, `error_code`, `error_message`, `failed_at`, `notification_dispatched_at`, `created_at`
- `keyword_detections` 최소 필드: `id`, `analysis_job_id`, `keyword`, `confidence`, `created_at`
- 로컬 stub 상태 전이: `QUEUED` -> `PROCESSING` -> `COMPLETED` 또는 `FAILED`

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
  "ownerUserId": "uuid",
  "documentId": "uuid",
  "state": "QUEUED",
  "createdAt": "2026-04-14T08:00:00Z",
  "analysisProvider": "local-stub",
  "resultSummary": null,
  "riskScore": null,
  "keywords": [],
  "errorCode": null,
  "errorMessage": null,
  "failedAt": null
}
```

주의:
- `documentId`가 실제로 존재하지 않으면 `404 RESOURCE_NOT_FOUND`를 반환합니다.
- 생성된 Job은 DB에 저장되며, `GET /api/v1/analysis/jobs/{id}`는 DB에서 조회합니다.
- 로컬 분석은 `text/plain` 문서의 업로드 내용을 읽어 `계약`, `검토`, `알림`, `긴급`, `위험`, `청구`, `개인정보` 키워드를 감지합니다.
- PDF 등 텍스트를 읽을 수 없는 문서는 filename/fileKey 기반 메타데이터 fallback으로 분석합니다.
- 분석 완료 시 키워드는 `keyword_detections`에 저장되며, 중복 조회해도 같은 Job/키워드는 다시 저장하지 않습니다.
- `analysis` 상태 전이에 따라 `document` 상태도 `ANALYSIS_QUEUED`, `ANALYSIS_PROCESSING`, `ANALYSIS_COMPLETED`, `ANALYSIS_FAILED`로 갱신합니다.
- 로컬 분석에서 파일명/fileKey/텍스트 내용에 `분석실패`, `fail`, `analysis-fail`, `force-fail`이 포함되면 실패 검증용으로 `FAILED` 상태가 됩니다.
- `analysis`가 `COMPLETED`로 전이되면 `notification` 서비스에 키워드/위험 점수를 전달해 자동 알림 판단을 요청합니다.
- `analysis -> document` 호출은 기본 connect timeout `1000ms`, read timeout `2000ms`를 사용합니다.
- `analysis -> notification` 호출은 기본 connect timeout `1000ms`, read timeout `2000ms`를 사용하며, 실패 시 분석 조회는 유지하고 다음 조회에서 재시도합니다.

### `GET /api/v1/analysis/jobs/{id}` 응답 예시 (`200 OK`)
```json
{
  "jobId": "uuid",
  "ownerUserId": "uuid",
  "documentId": "uuid",
  "state": "COMPLETED",
  "createdAt": "2026-04-14T08:00:00Z",
  "analysisProvider": "local-stub",
  "resultSummary": "로컬 분석이 완료되었습니다. 업로드된 텍스트 파일 내용 기준으로 계약, 검토, 알림, 긴급 키워드를 감지했습니다.",
  "riskScore": 98,
  "keywords": ["계약", "검토", "알림", "긴급"],
  "errorCode": null,
  "errorMessage": null,
  "failedAt": null
}
```

### 실패 응답 예시 (`GET /api/v1/analysis/jobs/{id}`)
```json
{
  "jobId": "uuid",
  "ownerUserId": "uuid",
  "documentId": "uuid",
  "state": "FAILED",
  "createdAt": "2026-04-14T08:00:00Z",
  "analysisProvider": "local-stub",
  "resultSummary": null,
  "riskScore": null,
  "keywords": [],
  "errorCode": "LOCAL_ANALYSIS_FAILED",
  "errorMessage": "로컬 분석 실패 마커가 감지되었습니다.",
  "failedAt": "2026-04-14T08:00:04Z"
}
```

### `POST /api/v1/analysis/jobs/{id}/retry` 응답 예시 (`200 OK`)
실패한 Job만 재시도할 수 있습니다. 같은 `jobId`를 다시 `QUEUED`로 돌리고 오류 필드를 초기화합니다.

```json
{
  "jobId": "uuid",
  "ownerUserId": "uuid",
  "documentId": "uuid",
  "state": "QUEUED",
  "createdAt": "2026-04-14T08:01:00Z",
  "analysisProvider": "local-stub",
  "resultSummary": null,
  "riskScore": null,
  "keywords": [],
  "errorCode": null,
  "errorMessage": null,
  "failedAt": null
}
```

## Notification
- `POST /api/v1/notifications/dispatch`
- `GET /api/v1/notifications/events`
- `GET /api/v1/notifications/events/{id}`
- `GET /api/v1/notifications/rules`
- `POST /api/v1/notifications/rules`

로컬 저장소:
- 현재 `notification` 서비스는 기본 H2(in-memory) 또는 `mariadb` 프로필의 VM MariaDB로 `notification_events`, `notification_rules`를 저장합니다.
- `notification_events` 최소 필드: `id`, `owner_user_id`, `document_id`, `channel`, `message`, `status`, `created_at`
- `notification_rules` 최소 필드: `id`, `owner_user_id`, `keyword`, `channel`, `enabled`, `created_at`
- 기본 로컬 규칙: `keyword=계약`, `channel=slack`, `enabled=true`

### `POST /api/v1/notifications/dispatch` 수동 요청/응답 예시
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
  "ownerUserId": "uuid",
  "documentId": "uuid",
  "channel": "slack",
  "message": "긴급 검토가 필요한 문서입니다.",
  "status": "DISPATCHED",
  "createdAt": "2026-04-14T09:00:00Z"
}
```

### `POST /api/v1/notifications/dispatch` 자동 알림 요청 예시
요청:
```json
{
  "documentId": "uuid",
  "keywords": ["계약", "검토", "알림"],
  "riskScore": 24
}
```

응답:
- enabled rule과 키워드가 매칭되면 `201 Created`와 생성된 이벤트를 반환합니다.
- 매칭되는 enabled rule이 없으면 `204 No Content`를 반환하며 이벤트를 만들지 않습니다.

### `GET /api/v1/notifications/events` 응답 예시 (`200 OK`)
```json
[
  {
    "eventId": "uuid",
    "ownerUserId": "uuid",
    "documentId": "uuid",
    "channel": "slack",
    "message": "분석 완료: '계약' 키워드 규칙이 매칭되었습니다. 위험 점수 24점",
    "status": "DISPATCHED",
    "createdAt": "2026-04-14T09:00:00Z"
  }
]
```

### `GET /api/v1/notifications/rules` 응답 예시 (`200 OK`)
```json
[
  {
    "ruleId": "uuid",
    "keyword": "계약",
    "channel": "slack",
    "enabled": true,
    "createdAt": "2026-04-14T09:00:00Z"
  }
]
```

### `POST /api/v1/notifications/rules` 요청/응답 예시
요청:
```json
{
  "keyword": "계약",
  "channel": "slack",
  "enabled": true
}
```

응답 (`201 Created`):
```json
{
  "ruleId": "uuid",
  "keyword": "계약",
  "channel": "slack",
  "enabled": true,
  "createdAt": "2026-04-14T09:00:00Z"
}
```

## 오류 모델
- 공통 필드: `timestamp`, `path`, `code`, `message`, `traceId`
- 기본 코드: `VALIDATION_ERROR`, `RESOURCE_NOT_FOUND`, `UPSTREAM_DOCUMENT_ERROR`, `UPSTREAM_AI_ERROR`, `INTERNAL_ERROR`
- `UPSTREAM_DOCUMENT_ERROR`: `analysis`가 `document` 서비스에 연결하지 못했을 때 `502 Bad Gateway`로 반환합니다.
- 모든 서비스는 예상하지 못한 오류를 `INTERNAL_ERROR` 형태로 반환합니다.
