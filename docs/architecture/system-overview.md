# 시스템 개요 (템플릿)

## 목표
SmartDoc AI는 비정형 문서를 수집해 검색 가능한 업무 메타데이터로 변환하고, 규칙 기반 알림으로 후속 업무를 자동화하는 것을 목표로 합니다.

## 핵심 컴포넌트
- 프론트엔드 (`smartdoc-ai`): 업로드, 모니터링, 검색 대시보드
- Gateway 서비스: API 진입점 및 라우팅
- Document 서비스: 문서 수명주기 및 메타데이터 관리
- Analysis 서비스: Textract/Comprehend 오케스트레이션
- Notification 서비스: Slack/이벤트 전달
- 저장소: S3(원본 파일), RDBMS(메타데이터)

## 배포 가정
- Amazon EKS 기반 Kubernetes
- 외부 유입 트래픽은 ALB 사용
- 서비스/플랫폼 로그는 CloudWatch 수집
