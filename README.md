# SmartDoc AI

SmartDoc AI는 MSA 기반 지능형 문서 분석 및 자동화 플랫폼입니다. 기업의 비정형 문서(영수증, 계약서, 스캔 파일 등)를 AI로 분석해 업무 데이터로 전환하고, 규칙 기반 알림을 통해 후속 업무를 자동화합니다.

이 저장소는 기존 프론트엔드(`smartdoc-ai`)를 유지하면서, 백엔드 서비스/인프라/문서 산출물의 모노레포 스캐폴딩을 함께 제공합니다.

## PRD 문맥
- 원본 PRD: [`smartdoc-ai/docu/PRD.md`](./smartdoc-ai/docu/PRD.md)
- 제품 설명: [`smartdoc-ai/docu/README.md`](./smartdoc-ai/docu/README.md)

목표 처리 흐름:
1. 사용자가 웹 UI에서 문서를 업로드합니다.
2. 문서는 S3에 저장됩니다.
3. Textract와 Comprehend가 내용을 분석합니다.
4. 메타데이터를 저장하고 시각화합니다.
5. 키워드/규칙 매칭 시 알림을 발송합니다.

## 저장소 구조

```text
SmartDoc_AI/
├── README.md
├── smartdoc-ai/                  # 기존 React 프론트엔드(유지)
│   ├── src/
│   ├── docu/
│   │   ├── PRD.md
│   │   └── README.md
│   └── ...
├── backend/
│   ├── CONVENTIONS.md
│   ├── README.md
│   └── services/
│       ├── gateway/
│       ├── document/
│       ├── analysis/
│       └── notification/
├── infra/
│   ├── README.md
│   ├── docker/
│   │   ├── README.md
│   │   └── docker-compose.yml
│   └── k8s/
│       ├── README.md
│       └── base/
└── docs/
    ├── README.md
    ├── architecture/
    ├── erd/
    ├── api/
    └── ui/
```

## 백엔드 템플릿 구성
`backend/services/*` 각 서비스에는 다음이 포함됩니다.
- `README.md`: 서비스 책임, 로컬 실행, 엔드포인트 안내
- `build.gradle.kts`: Spring Boot 3.x + Kotlin + Java 17 기본 설정
- `settings.gradle.kts`
- `src/main/kotlin/.../Application.kt`: 부트스트랩 애플리케이션 + 헬스 엔드포인트
- `src/main/resources/application.yml`
- `.env.example`

공통 규칙:
- 패키지 규칙: `com.smartdoc.<service>`
- 기본 프로필: `local`
- 기본 포트: `8080` ~ `8083`
- 상세 문서: [`backend/CONVENTIONS.md`](./backend/CONVENTIONS.md)

## 로컬 개발 시작

### 프론트엔드 (현재 동작 중)
사전 요구사항: Node.js 20+

1. 의존성 설치
   - `cd smartdoc-ai && npm install`
2. 환경 변수 설정
   - `.env.local`에 필요한 값을 입력 (`.env.example` 참고)
3. 개발 서버 실행
   - `npm run dev`

### 백엔드 템플릿 (스캐폴딩 단계)
사전 요구사항: Java 17, Gradle

1. 서비스 디렉토리 이동
   - `cd backend/services/gateway` (`document`, `analysis`, `notification` 동일)
2. 환경 변수 템플릿 복사
   - `cp .env.example .env`
3. 서비스 실행
   - `gradle bootRun`

참고: 현재 백엔드 서비스는 초기 템플릿이며 DB/AWS/인증 연동은 아직 완성되지 않았습니다.

## 인프라 템플릿
- 로컬 통합용 Compose: [`infra/docker/docker-compose.yml`](./infra/docker/docker-compose.yml)
- EKS 배포용 Kubernetes 골격: [`infra/k8s/base`](./infra/k8s/base)

위 파일들은 초기 뼈대이며, 시크릿/헬스체크/리소스 제한/인그레스/관측성 설정을 단계적으로 강화해야 합니다.

## 산출물 문서 템플릿
- 아키텍처: [`docs/architecture`](./docs/architecture)
- ERD/도메인 모델: [`docs/erd`](./docs/erd)
- API 문서: [`docs/api`](./docs/api)
- UI 캡처 산출물: [`docs/ui`](./docs/ui)

## 현재 상태
- 프론트엔드는 실행 가능한 상태를 유지합니다.
- 백엔드/인프라/문서 영역은 다음 구현 단계를 위한 스캐폴딩이 완료된 상태입니다.
