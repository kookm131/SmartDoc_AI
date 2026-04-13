# Architecture

## 핵심 컴포넌트
- 프론트엔드: 루트 앱(`src`, `vite`)
- Gateway: API 진입점/라우팅
- Document: 문서 수명주기/메타데이터
- Analysis: Textract/Comprehend 오케스트레이션
- Notification: 알림 디스패치
- 저장소: S3(원본), RDBMS(메타데이터)

## 배포 가정
- Amazon EKS + ALB
- CloudWatch 로그 수집
- 로컬 검증: Docker Compose + `infra/k8s/base`

## 데이터 흐름
1. 업로드 요청 수신
2. 문서 저장(S3)
3. AI 분석(Textract/Comprehend)
4. 결과 저장(RDBMS)
5. 규칙 기반 알림 발송
