# UI/UX 증빙 산출물

## 목적
- 사용자 시나리오별 화면 증빙을 일관된 형식으로 기록
- 기능 구현 상태와 문서화 상태를 함께 추적

## 현재 상태
### 구현됨
- `screen-inventory.md`
- `capture-log.md`

### 미구현
- 시나리오별 성공/실패 상태 스냅샷 체계화

## 선행 조건
1. API/도메인 흐름 확인: [`docs/api/README.md`](../api/README.md)
2. 프론트엔드 컨텍스트 확인: [`smartdoc-ai/docu/README.md`](../../smartdoc-ai/docu/README.md)

## 실행/검증 순서
1. 화면 목록 최신화 (`screen-inventory.md`)
2. 캡처 로그 기록 (`capture-log.md`)
3. 기능 흐름(업로드/분석/알림) 커버리지 점검

## DoD (완료 기준)
- 핵심 사용자 플로우 화면이 모두 캡처됨
- 캡처 시점/버전/조건이 추적 가능함
- UI 설명이 API/도메인 문서와 모순 없음

## 다음 단계
- 반응형(모바일/데스크톱) 증빙 분리
- 디자인 변경 이력 추적 템플릿 추가
