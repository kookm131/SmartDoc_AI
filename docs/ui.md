# UI

## 화면 인벤토리
- 메인 대시보드
- 문서 목록/검색/필터
- 문서 상세: 분석 실행, 분석 Job 상태, 알림 발송, 알림 이벤트 목록
- 업로드 모달/페이지
- 알림 이력

## 현재 구현 상태
- 문서 목록에서 새 문서 등록 후 상세 화면으로 이동
- 상세 화면에서 `POST /api/v1/analysis/jobs`로 분석 실행
- 상세 화면에서 `POST /api/v1/notifications/dispatch`로 Slack 알림 이벤트 생성
- 상세 화면에서 `GET /api/v1/notifications/events` 결과 중 현재 문서 이벤트 표시

## 캡처 로그 템플릿
| 날짜 | 화면 | 목적 | 파일 |
|------|------|------|------|
| YYYY-MM-DD | 대시보드 | AG Grid 개요 | docs/evidence/dashboard.png |
