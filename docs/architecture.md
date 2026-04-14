# Architecture

## 핵심 컴포넌트
- 프론트엔드: 루트 앱(`src`, `vite`)
- Gateway: API 진입점/라우팅/Auth v1
- Document: 문서 수명주기/메타데이터
- Analysis: Textract/Comprehend 오케스트레이션
- Notification: 알림 디스패치
- 저장소: S3(원본), RDBMS(메타데이터)

## 배포 가정
- Amazon EKS + ALB
- CloudWatch 로그 수집
- 로컬 검증: Docker Compose + kind/Kubernetes `infra/k8s/base`

## 데이터 흐름
1. 프론트엔드가 Gateway Auth API로 로그인하고 JWT access token을 저장
2. 프론트엔드가 Gateway로 API 요청할 때 `Authorization: Bearer <token>`을 전달
3. Gateway가 토큰을 검증한 뒤 document/analysis/notification 서비스로 라우팅
4. Document가 로컬 파일 저장소에 원본 파일을 저장하고 문서 메타데이터를 기록
5. Analysis가 분석 Job을 생성하고 document 상태를 동기화
6. Analysis가 document의 로컬 텍스트 내용을 조회해 키워드 감지 결과를 저장
7. Analysis가 notification으로 키워드/위험 점수를 전달
8. Notification이 enabled rule과 키워드를 매칭해 알림 이벤트 저장

## 현재 단계와 연동 계획
- 현재: 로컬 개발 단계(H2/JPA, Gateway Auth v1, 로컬 파일 업로드, 실제 Docker 이미지, Kubernetes base 매니페스트)
- Gateway Auth v1은 별도 인증 서비스를 띄우지 않고 Gateway 내부 H2에 `app_users`를 저장합니다.
- 로컬 기본 계정은 `test@smartdoc.local` / `password`이며, 재시작 시 seed로 자동 생성됩니다.
- DB는 당분간 H2 in-memory를 유지하며, 운영 DB 전환은 AWS 연동 후반부로 보류
- 로컬 컨테이너 검증:
  - Docker Compose: `infra/docker/docker-compose.yml`
  - Kubernetes: `smartdoc/*:local` 이미지를 kind/minikube에 로드 후 `infra/k8s/base` 적용
- 다음: AWS/EKS 연동
  - 필요 시 후반부에 RDBMS를 MSSQL(RDS 등)로 전환
  - S3/Textract/Comprehend 연동 어댑터 추가
  - EKS 배포(Deployment/Service + Ingress/Secret/ConfigMap)로 운영 경로 전환
