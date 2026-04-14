# ERD

## 애그리게잇 후보
- Document
- AnalysisJob
- AlertRule
- AlertEvent

## 물리 스키마 초안 (로컬 H2 시작, 운영 MSSQL 전환)
- `documents(id, file_key, filename, status, content_type, created_at, updated_at)`
- `analysis_jobs(id, document_id, state, sentiment, extracted_text_ref, created_at)`
- `keyword_detections(id, analysis_job_id, keyword, confidence, created_at)`
- `notification_rules(id, keyword, channel, enabled, created_at)`
- `notification_events(id, rule_id, analysis_job_id, channel, delivered_at, status)`

## 인덱스 아이디어
- `documents(status, created_at)`
- `analysis_jobs(document_id)`
- `keyword_detections(keyword)`
