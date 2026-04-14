# PRD 요약

## 프로젝트 개요
- 프로젝트명: SmartDoc AI
- 목표: 비정형 문서를 AI로 분석해 구조화 데이터로 전환하고, 규칙 기반 알림으로 후속 업무를 자동화

## 핵심 시나리오
1. 문서 업로드 및 S3 저장
2. Textract로 텍스트/테이블 추출
3. Comprehend로 키워드/감성 분석
4. 메타데이터 저장/조회
5. 키워드 조건 충족 시 알림 발송

## 기술 스택 방향
- Backend: Spring Boot 3.x, Kotlin, Java 17
- Frontend: React, Vite
- Infra: EKS(Kubernetes), ALB, CloudWatch
- Storage: S3 + RDBMS(MSSQL 가정)
- Security: JWT/2FA(계획)

## 구현 단계(현재 기준)
1. 로컬 기능 단계: API/도메인/JPA(H2) 검증
2. 인프라 단계: Kubernetes base 확장(Probe/HPA/Ingress/Secret)
3. AWS 연동 단계: S3/Textract/Comprehend + MSSQL + EKS 배포
