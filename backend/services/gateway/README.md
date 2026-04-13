# SmartDoc Gateway 서비스

## 목적
- 외부 요청 진입점 제공
- 라우팅 및 인증/인가 정책의 시작 지점 역할

## 현재 상태
### 구현됨
- Spring Boot 템플릿 앱
- 기본 헬스 엔드포인트

### 미구현
- JWT/2FA 실연동
- 다운스트림 서비스 라우팅 정책 세분화

## 선행 조건
1. Java 17
2. `.env.example` 복사
   - `cp .env.example .env`
3. 공통 규칙 확인
   - [`backend/CONVENTIONS.md`](../../CONVENTIONS.md)

## 실행/검증 순서
1. 실행
   - `./gradlew bootRun`
2. 기본 엔드포인트 확인
   - `GET /api/v1/health`

## 표준 설정
- 포트: `8080`
- 프로필: `local`
- 패키지: `com.smartdoc.gateway`
- 환경변수 접두사: `SMARTDOC_GATEWAY_*`

## 다음 단계
- 인증 필터 체인 도입
- 서비스별 라우팅/타임아웃/서킷브레이커 정책 추가
