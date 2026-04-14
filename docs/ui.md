# UI

## 화면 인벤토리
- 로그인/회원가입
- 메인 대시보드
- 문서 목록/검색/필터
- 문서 상세: 분석 실행, 분석 Job 상태, 분석 요약/리스크/키워드, 알림 발송, 알림 이벤트 목록
- 업로드 모달/페이지
- 알림 규칙 관리: 키워드/채널/활성 여부 등록 및 갱신
- 알림 이력

## 현재 구현 상태
- 로그인/회원가입 화면에서 Gateway Auth v1(`POST /api/v1/auth/login`, `POST /api/v1/auth/signup`) 사용
- 로그인 성공 후 JWT access token을 브라우저 `localStorage`에 저장하고 Gateway 요청에 자동 첨부
- 로그아웃 시 서버는 stateless 처리하고 프론트에서 저장된 token을 삭제
- 문서 목록에서 파일 선택 또는 메타데이터 입력으로 새 문서 등록 후 상세 화면으로 이동
- 파일 선택 시 `POST /api/v1/documents/upload`로 로컬 업로드 API 호출
- 알림 규칙 화면에서 `GET /api/v1/notifications/rules`, `POST /api/v1/notifications/rules`로 규칙 관리
- 상세 화면에서 `POST /api/v1/analysis/jobs`로 분석 실행
- 상세 화면에서 text/plain 파일 내용 기반 `resultSummary`, `riskScore`, `keywords` 표시
- 분석 완료 후 enabled 알림 규칙과 키워드가 매칭되면 Slack 알림 이벤트 자동 생성
- 상세 화면에서 `POST /api/v1/notifications/dispatch`로 Slack 알림 이벤트 수동 생성도 가능
- 상세 화면에서 `GET /api/v1/notifications/events` 결과 중 현재 문서 이벤트 표시

## 로컬 인증 UX
- 기본 로그인 계정: `test@smartdoc.local` / `password`
- H2 in-memory라 재시작 시 가입 사용자는 초기화되지만, 기본 계정은 자동으로 다시 생성
- 운영 인증 방식은 AWS/운영 배포 단계에서 별도 검토

## 캡처 로그 템플릿
| 날짜 | 화면 | 목적 | 파일 |
|------|------|------|------|
| YYYY-MM-DD | 대시보드 | AG Grid 개요 | docs/evidence/dashboard.png |
