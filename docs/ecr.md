# ECR Image Push Flow

기준선:
- Private ECR
- 서비스별 repository: `smartdoc-gateway`, `smartdoc-document`, `smartdoc-analysis`, `smartdoc-notification`
- 태그: `latest` + `git sha`

## 사전 준비
- AWS CLI 로그인(권장: SSO 또는 `aws configure`)
- Docker 로그인 상태 확인
- ECR 권한(최소):
  - `ecr:GetAuthorizationToken`
  - `ecr:DescribeRepositories`
  - `ecr:CreateRepository`
  - `ecr:BatchCheckLayerAvailability`
  - `ecr:InitiateLayerUpload`
  - `ecr:UploadLayerPart`
  - `ecr:CompleteLayerUpload`
  - `ecr:PutImage`

## 환경변수
`.env.local` 또는 CI 환경에서 설정합니다.
- `SMARTDOC_ECR_REGISTRY`
  - 예: `123456789012.dkr.ecr.us-east-1.amazonaws.com`
- `AWS_REGION` 또는 `SMARTDOC_AWS_REGION`

## 푸시
스크립트는 repository가 없으면 자동 생성합니다.

```bash
scripts/push-ecr.sh
```

특정 서비스만:
```bash
scripts/push-ecr.sh gateway document
```

## K8s/EKS에서 사용할 이미지
예시:
- `123456789012.dkr.ecr.us-east-1.amazonaws.com/smartdoc-gateway:latest`
- `123456789012.dkr.ecr.us-east-1.amazonaws.com/smartdoc-gateway:<git-sha>`

