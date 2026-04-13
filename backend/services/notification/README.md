# SmartDoc Notification 서비스

## 목적
- 키워드/규칙 기반 알림 디스패치
- 문서 처리 이벤트를 사용자 알림으로 변환

## 현재 상태
### 구현됨
- Spring Boot 템플릿 앱
- 알림 발송 요청 엔드포인트 골격

### 미구현
- Slack/Webhook 실연동
- 알림 실패 재시도 및 중복 방지 정책

## 선행 조건
1. Java 17
2. `.env.example` 복사
   - `cp .env.example .env`
3. 공통 규칙 확인
   - [`backend/CONVENTIONS.md`](../../CONVENTIONS.md)

## 실행/검증 순서
1. 실행
   - `gradle bootRun` (또는 `./gradlew bootRun`)
2. 기본 엔드포인트 확인
   - `POST /api/v1/notifications/dispatch`

## 표준 설정
- 포트: `8083`
- 프로필: `local`
- 패키지: `com.smartdoc.notification`
- 환경변수 접두사: `SMARTDOC_NOTIFICATION_*`

## 다음 단계
- 알림 채널 다중화(Email/Slack/Webhook)
- 템플릿 기반 메시지 및 재시도 정책 추가
