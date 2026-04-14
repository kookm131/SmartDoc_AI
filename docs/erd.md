# ERD

## 애그리게잇 후보
- Document
- AnalysisJob
- AlertRule
- AlertEvent

## 물리 스키마 초안 (로컬 H2 시작, 운영 MSSQL 전환)
- `documents(id, file_key, filename, status, content_type, created_at, updated_at)`
- `analysis_jobs(id, document_id, state, analysis_provider, created_at)`
- `keyword_detections(id, analysis_job_id, keyword, confidence, created_at)`
- `notification_rules(id, keyword, channel, enabled, created_at)`
- `notification_events(id, document_id, channel, message, status, created_at)`

## 현재 구현 상태
- `document`: JPA + H2(in-memory)로 `documents` 저장
- `analysis`: JPA + H2(in-memory)로 `analysis_jobs` 저장
- `notification`: JPA + H2(in-memory)로 `notification_events` 저장

## 인덱스 아이디어
- `documents(status, created_at)`
- `analysis_jobs(document_id)`
- `notification_events(document_id, created_at)`
- `keyword_detections(keyword)`
