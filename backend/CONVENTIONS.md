# 백엔드 공통 규칙

이 문서는 백엔드 서비스 전반에서 공통으로 따를 규칙을 정의합니다.

## 런타임 기준
- Java: 17
- 프레임워크: Spring Boot 3.x
- 빌드: Gradle Kotlin DSL
- 패키징: `backend/services` 하위 서비스별 독립 디렉토리

## 패키지 네이밍
- 기본 패턴: `com.smartdoc.<service>`
- 서비스별 패키지:
  - gateway: `com.smartdoc.gateway`
  - document: `com.smartdoc.document`
  - analysis: `com.smartdoc.analysis`
  - notification: `com.smartdoc.notification`

## 포트 정책
- `gateway`: `8080`
- `document`: `8081`
- `analysis`: `8082`
- `notification`: `8083`

로컬 기본 포트는 `SERVER_PORT`로 변경할 수 있습니다.

## 프로필 정책
- 기본 프로필: `local`
- 선택 프로필: `dev`, `prod`

`SPRING_PROFILES_ACTIVE`로 실행 프로필을 전환합니다.

## 로깅 정책
- 루트 로그 레벨: `INFO`
- 애플리케이션 패키지 로그 레벨: 로컬에서 `DEBUG`
- 출력 형식: 템플릿 단계에서는 콘솔 기본 출력 사용

## 환경 변수 네이밍
- 충돌 방지를 위해 서비스 접두사를 사용합니다.
  - `SMARTDOC_GATEWAY_*`
  - `SMARTDOC_DOCUMENT_*`
  - `SMARTDOC_ANALYSIS_*`
  - `SMARTDOC_NOTIFICATION_*`

## 서비스 책임
- `gateway`: 라우팅/인증 진입점
- `document`: 업로드 및 문서 메타데이터 수명주기 관리
- `analysis`: Textract/Comprehend 연동 오케스트레이션
- `notification`: Slack 및 이벤트 알림 전달
