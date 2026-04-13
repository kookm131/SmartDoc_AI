SmartDoc AI: MSA 기반 지능형 문서 분석 및 자동화 플랫폼
SmartDoc AI는 기업 내 방대한 비정형 문서(영수증, 계약서 등)를 AI로 자동 분석하여 데이터베이스화하고, 특정 조건 발생 시 업무 담당자에게 즉시 알림을 보내는 지능형 자동화 워크플로우 플랫폼입니다
.
1. 프로젝트 개요 (Overview)
본 프로젝트는 단순한 문서 저장을 넘어, AWS Textract와 Comprehend를 활용해 문서 내 핵심 정보를 추출하고 비즈니스 인사이트를 도출하는 것을 목적으로 합니다
. **MSA(Microservices Architecture)**를 채택하여 서비스 간 독립성을 확보하고, 급증하는 문서 처리 수요에 유연하게 대응할 수 있도록 설계되었습니다
.
2. 주요 기능 (Key Features)
지능형 문서 업로드 및 저장: 사용자가 PDF나 이미지 문서를 업로드하면 Amazon S3에 안전하게 보관됩니다
.
AI 기반 데이터 추출 및 분석: AWS Textract를 통해 문서 내 텍스트와 테이블 구조를 인식하고, Amazon Comprehend로 주요 키워드 및 감성을 정밀 분석합니다
.
고성능 데이터 시각화: 분석된 메타데이터는 AG Grid를 통해 리스트화되어 대량의 데이터를 효율적으로 검색 및 필터링할 수 있습니다
.
업무 자동화 알림: '긴급', '결재' 등 특정 키워드 감지 시 Slack을 통해 담당자에게 실시간 알림을 발송하여 업무 누락을 방지합니다
.
3. 기술 스택 (Tech Stack)
Backend
Language & Framework: Java 17, Spring Boot 3.x
Architecture: MSA (Microservices Architecture), Spring Cloud Gateway
ORM: JPA (Hibernate)
Design Pattern: DDD (Domain-Driven Design), CQRS (Command Query Responsibility Segregation)
Frontend
Library: React
Architecture: FSD (Feature-Sliced Design) 디렉토리 구조 적용
UI Components: AG Grid Community, Pretendard Font
Infra & DevOps
Compute: Amazon EKS (Kubernetes)
AI Services: Amazon Textract, Amazon Comprehend
Storage: Amazon S3 (원본 문서), RDBMS (메타데이터/MSSQL 등)
Network: ALB (Application Load Balancer), RESTful API
Security: JWT 기반 토큰 인증, 2Factor 인증
Monitoring: Amazon CloudWatch
CI/CD: GitHub Actions, Short-lived Feature Branch 전략
4. 시스템 아키텍처 (Architecture)
본 시스템은 확장성과 고가용성을 최우선으로 설계되었습니다.
Traffic Control: ALB를 통해 들어온 요청은 Spring Cloud Gateway를 거쳐 각 마이크로서비스로 라우팅됩니다
.
Data Flow: S3에 저장된 문서는 AI 리소스(Textract, Comprehend)에 의해 분석되며, 결과값은 RDBMS에 저장됩니다
.
Orchestration: 모든 서비스 모듈은 Amazon EKS 내에서 컨테이너 단위로 관리 및 오케스트레이션됩니다
.
5. 프로젝트 산출물 (Deliverables)
Architecture Diagram: AWS 리소스 간 데이터 흐름도
ERD & Domain Model: DA# 또는 ER Master를 활용한 정교한 데이터 모델
API Documentation: Swagger 기반의 RESTful API 명세서
UI/UX Screenshot: AG Grid가 적용된 실시간 분석 대시보드