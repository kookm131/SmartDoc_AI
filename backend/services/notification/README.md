# SmartDoc Notification 서비스

## 역할
알림 및 워크플로우 이벤트 서비스

## 로컬 실행
1. `.env.example`을 `.env`로 복사하고 필요 값을 수정합니다.
2. 애플리케이션을 실행합니다.
   - `gradle bootRun` (래퍼 추가 시 `./gradlew bootRun`)
3. 헬스/기본 엔드포인트를 확인합니다.
   - `POST /api/v1/notifications/dispatch`

## 기본 설정
- 포트: `8083`
- 프로필: `local`
- 베이스 패키지: `com.smartdoc.notification`

## 참고
- 본 서비스는 MSA 초기 스캐폴딩 템플릿입니다.
- DB/AWS/인증/메시징 연동은 단계적으로 구현해야 합니다.
