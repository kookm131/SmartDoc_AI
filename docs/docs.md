# 문서 산출물 온보딩

이 디렉토리는 배포 준비 과정을 문서 증빙으로 남기기 위한 산출물 모음입니다.
루트/인프라/백엔드 온보딩 완료 후 이 순서로 작성합니다.

## 목적
- 아키텍처/데이터/API/UI 증빙의 최소 기준 통일
- 포트폴리오 및 팀 온보딩 문서의 일관성 확보

## 현재 상태
### 구현됨
- 하위 도메인별 템플릿 파일 생성

### 미구현
- 실제 구현 결과 반영
- 문서 간 상호 참조 링크 완성

## 선행 조건
1. 인프라 온보딩 확인
   - [`infra/README.md`](../infra/README.md)
2. 백엔드 서비스 책임/포트 확인
   - [`backend/README.md`](../backend/README.md)

## 실행/검증 순서
1. 아키텍처 문서 정리
   - [`docs/architecture/architecture.md`](./architecture/architecture.md)
2. ERD/도메인 모델 정리
   - [`docs/erd/erd.md`](./erd/erd.md)
3. API 계약 정리
   - [`docs/api/api.md`](./api/api.md)
4. UI 캡처/기록 정리
   - [`docs/ui/ui.md`](./ui/ui.md)

## 다음 단계
- PRD 항목과 산출물 매핑표 추가
- 변경 이력(changelog) 섹션 도입
- 완료 기준(DoD) 충족 여부 주기적 점검
