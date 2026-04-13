# SmartDoc AI

SmartDoc AI는 비정형 문서를 AI로 분석하고 후속 업무를 자동화하는 MSA 기반 플랫폼입니다.
이 저장소는 프론트엔드(기존) + 백엔드/인프라/문서(스캐폴딩)를 함께 관리합니다.

## 가장 먼저 할 일: 프로젝트 기준선 고정
로컬 K8s 기준으로 아래 항목을 먼저 준비하세요.

1. Docker 실행 가능 여부 확인
   - `docker --version`
2. Kubernetes CLI 확인
   - `kubectl version --client`
3. 로컬 클러스터 도구 확인(kind 또는 minikube)
   - `kind version` 또는 `minikube version`
4. 백엔드 런타임 확인
   - `java -version` (Java 17)
5. 프론트엔드 런타임 확인
   - `node -v` (Node 20+)

## 온보딩 순서 (배포 준비 우선)
1. 인프라 준비
   - [`infra/README.md`](./infra/README.md)
2. 백엔드 서비스 점검
   - [`backend/README.md`](./backend/README.md)
3. 산출물 문서 정리
   - [`docs/README.md`](./docs/README.md)
4. 제품/PRD 문맥 확인
   - [`smartdoc-ai/docu/README.md`](./smartdoc-ai/docu/README.md)
   - [`smartdoc-ai/docu/PRD.md`](./smartdoc-ai/docu/PRD.md)

## 실행 방법 (Quick Start)
### 1) 프론트엔드 실행
1. 디렉토리 이동
   - `cd smartdoc-ai`
2. 의존성 설치
   - `npm install`
3. 환경 변수 설정
   - `.env.example` 참고 후 `.env.local` 작성
4. 개발 서버 실행
   - `npm run dev`

### 2) 백엔드 실행 (서비스별)
1. 서비스 디렉토리 이동
   - `cd backend/services/gateway` (또는 `document`, `analysis`, `notification`)
2. 환경 변수 템플릿 복사
   - `cp .env.example .env`
3. 애플리케이션 실행
   - `gradle bootRun` (또는 `./gradlew bootRun`)

기본 포트:
- gateway `8080`
- document `8081`
- analysis `8082`
- notification `8083`

### 3) 인프라 템플릿 실행 (선택)
1. 디렉토리 이동
   - `cd infra/docker`
2. Compose 설정 확인
   - `docker compose -f docker-compose.yml config`
3. 컨테이너 기동
   - `docker compose -f docker-compose.yml up -d`
4. 상태 확인
   - `docker compose -f docker-compose.yml ps`

## 현재 상태
### 구현됨
- 프론트엔드 프로젝트 기본 구조 유지
- 백엔드 4개 서비스 템플릿 생성
- Docker Compose/Kubernetes base 매니페스트 골격 생성
- 아키텍처/ERD/API/UI 문서 템플릿 생성

### 미구현 (다음 단계)
- AWS 실연동(Textract/Comprehend/S3/IAM)
- 인증/인가, 시크릿 관리, 운영 수준 관측성
- 서비스 간 메시징/재시도/장애 복구 전략

## 저장소 구조
```text
SmartDoc_AI/
├── README.md
├── smartdoc-ai/                # 기존 프론트엔드
├── backend/                    # Spring Boot MSA 템플릿
├── infra/                      # Docker + Kubernetes 템플릿
└── docs/                       # 아키텍처/ERD/API/UI 산출물
```

## 로컬 실행 참고
- 인프라 우선 절차: [`infra/README.md`](./infra/README.md)
- 백엔드 우선 절차: [`backend/README.md`](./backend/README.md)
- 문서 산출물 절차: [`docs/README.md`](./docs/README.md)
