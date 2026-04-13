# 백엔드 서비스 온보딩

SmartDoc AI 백엔드는 Spring Boot 기반 4개 마이크로서비스 템플릿으로 구성되어 있습니다.

## 목적
- 서비스 책임/포트/실행 순서를 빠르게 파악
- 로컬 실행 전 공통 규칙을 일관되게 적용

## 현재 상태
### 구현됨
- `gateway`, `document`, `analysis`, `notification` 템플릿
- 공통 규칙 문서
  - [`backend/CONVENTIONS.md`](./CONVENTIONS.md)

### 미구현
- DB/AWS/인증/메시징 실연동
- 서비스 간 장애 복구/재시도 정책

## 서비스 목록
- `gateway` (`8080`): 외부 진입점/라우팅
- `document` (`8081`): 문서 업로드/메타데이터 수명주기
- `analysis` (`8082`): AI 분석 오케스트레이션
- `notification` (`8083`): 이벤트 알림/디스패치

## 선행 조건
1. Java 17
2. Gradle 실행 환경
3. 서비스별 `.env` 설정

## 실행/검증 순서
1. 공통 규칙 확인
   - [`backend/CONVENTIONS.md`](./CONVENTIONS.md)
2. 서비스별 README 순서대로 점검
   - [`backend/services/gateway/README.md`](./services/gateway/README.md)
   - [`backend/services/document/README.md`](./services/document/README.md)
   - [`backend/services/analysis/README.md`](./services/analysis/README.md)
   - [`backend/services/notification/README.md`](./services/notification/README.md)

## 다음 단계
- Gradle Wrapper 통일(`./gradlew`)
- 공통 라이브러리/에러 모델 표준화
- 헬스체크 외 readiness/liveness 엔드포인트 보강
