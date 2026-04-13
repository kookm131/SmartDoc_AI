1. 프로젝트 개요 (PRD)
프로젝트명: SmartDoc AI - MSA 기반 지능형 문서 분석 및 자동화 플랫폼
목적: 기업 내 방대한 비정형 문서(영수증, 계약서 등)를 AI로 자동 분석하여 데이터베이스화하고, 특정 조건 발생 시 업무 담당자에게 알림을 보내는 자동화 워크플로우 구축
주요 기능 시나리오:
문서 업로드: 사용자가 웹 화면을 통해 PDF나 이미지 문서를 업로드하면 S3에 저장됩니다
.
AI 분석: AWS Textract를 통해 문서 내 텍스트와 테이블을 추출하고, Comprehend가 핵심 키워드와 감성을 분석합니다
.
데이터 시각화: 분석 결과는 AG Grid 리스트로 시각화되어 사용자가 검색 및 필터링할 수 있습니다
.
자동 알림: '긴급'이나 '결재' 등의 키워드가 감지되면 Slack으로 자동 알림을 발송합니다
.

--------------------------------------------------------------------------------
2. 소스 코드 수준 기술 스택 (KEG 표준 반영)
과제 평가 시 '구현 모듈의 안정성'을 위해 최신 표준 스택을 채택합니다
.
Backend (Java/Spring Boot):
Framework: Spring Boot 3.x 및 JDK 17 (표준 자바 버전)
.
ORM: JPA를 사용하여 생산성 높은 데이터 접근 계층 구축
.
Architecture: 서비스 간 독립성을 위해 MSA 구조를 채택하고 Spring Cloud Gateway를 활용합니다
.
Frontend (React):
Structure: 대규모 프로젝트 유지보수를 위해 FSD(Feature-Sliced Design) 디렉토리 구조 적용
.
UI Components: 기업형 데이터 처리에 최적화된 AG Grid Community 사용
.
Font: 가독성을 위한 Pretendard 폰트 적용
.
DevOps & Security:
Auth: JWT 기반 토큰 인증 및 2Factor 인증으로 보안 강화
.
CI/CD: GitHub Actions와 Short-lived Feature Branch 전략을 통한 자동 배포
.

--------------------------------------------------------------------------------
3. AWS 인프라 및 AI 아키텍처 (평가 핵심 포인트)
배점이 가장 높은 AWS 리소스 간 관계 설정 및 AI 서비스 활용 능력을 강조한 설계입니다
.
컴퓨팅(Compute): Amazon EKS를 활용하여 마이크로서비스를 컨테이너 단위로 관리하고 오케스트레이션합니다
.
스토리지(Storage): 원본 문서 보관은 S3, 문서 상태 및 메타데이터 저장은 **RDBMS(MSSQL 등)**를 사용합니다
.
AI 리소스 (핵심 활용):
AWS Textract: 스캔된 문서나 PDF에서 텍스트 및 테이블 구조를 자동으로 인식하고 추출합니다
.
Amazon Comprehend: 추출된 텍스트에서 주요 개체(인물, 조직, 장소 등)를 인식하고 문서의 감성을 분석합니다
.
네트워크 및 보안: **ALB(Application Load Balancer)**를 통해 트래픽을 분산하며, 외부 통신은 RESTful API 방식을 따릅니다
.
모니터링: CloudWatch를 통해 시스템 로그를 실시간 분석하고 장애에 대응합니다
.

--------------------------------------------------------------------------------
4. 포트폴리오(Github Repo) 구성 전략
평가 당일 3시간 동안 효율적인 문서화를 위해 준비해야 할 산출물 리스트입니다
.
Architecture Diagram: S3 -> Textract -> EKS -> RDBMS로 이어지는 데이터 흐름도.
ERD & Domain Model: DA# 또는 ER Master를 활용한 데이터 모델 설계도
.
API Documentation: Swagger 등을 활용한 Restful API 명세서
.
UI/UX Screenshot: React와 AG Grid가 실제 적용된 대시보드 및 결과 화면 캡처본
.