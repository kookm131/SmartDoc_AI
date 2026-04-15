# Roadmap

## 현재 완료된 기준선
- 프론트엔드 루트 평탄화 및 Vite 실행 흐름 정리
- Gateway Auth v1 구현: 로그인, 회원가입, 로그아웃, JWT 발급/검증
- 개발용 seed 계정 제공: `test@smartdoc.local` / `password`
- Gateway가 인증 사용자 정보를 downstream에 전달
- document/analysis/notification 데이터 `owner_user_id` 기준 분리
- 로컬 문서 업로드 API 구현
- text/plain 문서 내용 기반 로컬 키워드 감지
- analysis job 저장, 상태 전이, 키워드 감지 저장
- analysis 실패 상태와 재시도 API/UI 구현
- notification rule 기반 자동 알림 이벤트 생성
- H2 기본 프로필 유지
- VM MariaDB용 `mariadb` 프로필 준비
- Docker Compose 및 Kubernetes base 매니페스트 준비
- 주요 README/API/ERD/Architecture/UI 문서 동기화

## 완료: 분석 실패와 재시도
목표: 정상 흐름뿐 아니라 실패 흐름도 서비스 상태로 관리합니다.

작업 항목:
- `analysis_jobs`에 `FAILED` 상태 추가
- `analysis_jobs`에 `error_code`, `error_message`, `failed_at` 필드 추가
- document 상태에 `ANALYSIS_FAILED` 추가
- 로컬 분석 실패 조건 추가: 파일명 또는 내용에 `fail`, `분석실패` 포함
- `POST /api/v1/analysis/jobs/{id}/retry` API 추가
- Gateway에 retry 라우팅 추가
- 프론트 문서 상세에 실패 메시지와 재시도 버튼 추가
- `docs/api.md`, `docs/erd.md`, `docs/architecture.md`, `docs/ui.md` 동기화
- analysis/gateway/frontend 테스트 보강

완료 기준:
- 실패 조건 문서 분석 시 job 상태가 `FAILED`가 됩니다.
- 실패 시 document 상태가 `ANALYSIS_FAILED`가 됩니다.
- 재시도 API 호출 시 같은 job이 다시 `QUEUED`로 돌아갑니다.
- 재시도 후 정상 조건에서는 `COMPLETED`까지 진행됩니다.

## 다음 작업: MariaDB 실제 연결 검증
목표: H2가 아니라 VM MariaDB에 실제 데이터를 저장합니다.

작업 항목:
- `.env.local`에 VM MariaDB 접속값 설정
- `SPRING_PROFILES_ACTIVE=mariadb`로 서비스 4개 실행
- Gateway seed 계정이 MariaDB에 생성되는지 확인
- 문서/분석/알림 데이터가 서비스별 DB에 저장되는지 확인
- 재시작 후 데이터가 남는지 확인
- `scripts/smoke-gateway.sh`로 전체 흐름 검증

완료 기준:
- `smartdoc_gateway.app_users`에 seed 계정이 존재합니다.
- `smartdoc_document.documents`에 업로드 문서가 저장됩니다.
- `smartdoc_analysis.analysis_jobs`와 `keyword_detections`에 분석 결과가 저장됩니다.
- `smartdoc_notification.notification_events`와 `notification_rules`에 알림 데이터가 저장됩니다.
- 서비스 재시작 후에도 데이터가 유지됩니다.

## 백엔드 완성도 보강
목: 로컬 데모가 아니라 실제 서비스에 가까운 백엔드 표안정성을 확보합니다.

작업 항목:
- 파일 크기 제한 추가: 완료 (`SMARTDOC_MAX_UPLOAD_BYTES`, 기본 10MiB)
- 허용 확장자와 content type 검증 강화: 완료 (`.pdf`, `.txt`, `.bin` 매핑 검증)
- 문서 삭제 또는 보관 처리 정책 추가
- 분석 job 중복 생성 방지 또는 최근 job 재사용 정책 결정
- notification rule 수정/비활성화/삭제 API 추가
- 공통 에러 코드 정리
- 서비스 간 호출 traceId 전달
- actuator 노출 범위 정리
- 로그에 민감정보가 남지 않는지 점검

완료 기준:
- 잘못된 파일과 요청이 일관된 `VALIDATION_ERROR`로 반환됩니다.
- 실패한 서비스 간 호출이 `traceId`로 추적 가능합니다.
- 알림 규칙을 UI/API에서 관리할 수 있습니다.

## 문서 분석 고도화
목표: stub 분석을 실제 문서 분석에 가깝게 확장합니다.

작업 항목:
- 로컬 PDF 텍스트 추출 구현
- 파일 내용 기반 키워드 규칙 확장
- 분석 결과 요약 필드 구조화
- 위험 점수 계산 기준 문서화
- 추출 실패와 부분 성공 상태 정의

완료 기준:
- text/plain뿐 아니라 PDF에서도 텍스트를 추출합니다.
- 분석 결과가 keywords, summary, riskScore로 안정적으로 반환됩니다.
- 실패/부분 성공 케이스가 API와 UI에 표현됩니다.

## AWS 연동 준비
목표: 비용이 드는 AWS 연동을 마지막 단계에서 흔들림 없이 붙입니다.

작업 항목:
- S3 업로드 어댑터 구현
- Textract 텍스트 추출 어댑터 구현
- Comprehend 또는 대체 AI 분석 어댑터 구현
- AWS credential 주입 방식 정리
- Secret Manager 또는 Parameter Store 사용 검토
- ECR 이미지 푸시 흐름 추가
- EKS 배포용 Ingress/Secret/ConfigMap 정리
- CloudWatch 로그/메트릭 수집 설정

완료 기준:
- `SPRING_PROFILES_ACTIVE=aws`에서 S3/Textract 기반 분석 흐름이 동작합니다.
- 민감정보가 git에 저장되지 않습니다.
- EKS에서 Gateway를 통해 전체 API 흐름이 검증됩니다.

## 프론트엔드 후속 작업
목표: 백엔드 기능을 사용자가 확인하기 쉽게 연결합니다.

작업 항목:
- 분석 실패 카드와 재시도 버튼 추가
- MariaDB 사용 여부를 UI에 직접 노출하지 않고 데이터 유지 흐름만 검증
- 알림 규칙 수정/삭제 UI 추가
- 문서 목록 필터와 검색 추가
- 분석 상태 polling 개선
- 로그인 만료 처리 개선

완료 기준:
- 실패/재시도/완료 상태를 문서 상세에서 명확히 볼 수 있습니다.
- 사용자는 자기 문서와 자기 알림만 확인합니다.
- 토큰 만료 시 로그인 화면으로 자연스럽게 이동합니다.

## 운영 전 점검
목표: AWS 이전에 로컬/VM 환경에서 운영 리스크를 줄입니다.

체크리스트:
- `.env.local`과 민감정보가 git에 포함되지 않음
- VM MariaDB 외부 접근 범위가 필요한 IP로 제한됨
- MariaDB 계정 권한이 필요한 DB에만 부여됨
- smoke 스크립트가 H2와 MariaDB 환경에서 모두 동작함
- Docker/Kubernetes 매니페스트가 최신 환경변수와 맞음
- README와 docs가 실제 실행 흐름과 맞음
