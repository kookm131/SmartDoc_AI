# ERD 및 도메인 모델 산출물

## 목적
- 문서 처리 도메인의 핵심 엔터티와 관계를 정리
- 서비스 책임과 데이터 저장 구조의 정합성 확보

## 현재 상태
### 구현됨
- `logical-model.md`
- `physical-schema.md`

### 미구현
- 최종 인덱스/제약조건/감사 필드 전략 확정

## 선행 조건
1. 서비스 책임 확인: [`backend/README.md`](../../backend/README.md)
2. 아키텍처 흐름 확인: [`docs/architecture/architecture.md`](../architecture/architecture.md)

## 실행/검증 순서
1. 논리 모델 정리 (`logical-model.md`)
2. 물리 스키마 초안 작성 (`physical-schema.md`)
3. 엔터티 책임과 서비스 경계 검토

## DoD (완료 기준)
- 핵심 엔터티 관계가 충돌 없이 정의됨
- 서비스별 소유 데이터 경계가 명확함
- 필수 PK/FK/유니크 후보가 명시됨

## 다음 단계
- 실제 DB 마이그레이션 전략과 연결
- 이벤트/이력 테이블 설계 반영
