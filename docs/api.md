# API

## Gateway
- `GET /api/v1/health`

## Document
- `POST /api/v1/documents`
- `GET /api/v1/documents/{id}`
- `GET /api/v1/documents`

## Analysis
- `POST /api/v1/analysis/jobs`
- `GET /api/v1/analysis/jobs/{id}`

## Notification
- `POST /api/v1/notifications/dispatch`
- `GET /api/v1/notifications/events`

## 오류 모델
- 공통 필드: `timestamp`, `path`, `code`, `message`, `traceId`
- 기본 코드: `VALIDATION_ERROR`, `RESOURCE_NOT_FOUND`, `UNAUTHORIZED`, `UPSTREAM_AI_ERROR`, `INTERNAL_ERROR`
