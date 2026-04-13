# API 문서 산출물

## 목적
- 서비스 간/클라이언트 간 REST 계약을 명확히 정의
- 구현 전후 변경 포인트를 추적 가능하게 관리

## 현재 상태
### 구현됨
- `openapi-summary.md`
- `error-model.md`
- `auth-policy.md`

### 미구현
- 서비스별 상세 스펙 예시 및 버저닝 정책 확정

## 선행 조건
1. 서비스 범위 확인: [`backend/README.md`](../../backend/README.md)
2. 도메인/데이터 모델 확인: [`docs/erd/README.md`](../erd/README.md)

## 실행/검증 순서
1. 엔드포인트 목록 정리 (`openapi-summary.md`)
2. 오류 모델 정리 (`error-model.md`)
3. 인증/인가 정책 정리 (`auth-policy.md`)

## DoD (완료 기준)
- 각 서비스 핵심 API가 누락 없이 정의됨
- 에러 코드/응답 구조가 일관됨
- 인증 정책이 게이트웨이 책임과 충돌하지 않음

## 다음 단계
- OpenAPI 원문(yaml/json) 연결
- 계약 테스트 시나리오 추가
