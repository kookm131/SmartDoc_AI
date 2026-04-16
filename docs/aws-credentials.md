# AWS Credential Injection

SmartDoc AI의 AWS 연동(`SPRING_PROFILES_ACTIVE=aws`)은 **AWS SDK v2의 기본 자격증명 체인(DefaultCredentialsProvider)** 을 기본으로 사용합니다.

## 원칙
- 기본값은 `DefaultCredentialsProvider`를 사용합니다.
- 로컬(LocalStack)에서는 `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`를 `.env.local`로 주입합니다.
- 운영(EKS/ECS/EC2)에서는 “코드/환경변수에 장기 키를 넣는 방식”을 지양하고, 플랫폼이 제공하는 자격증명 주입을 사용합니다.

## DefaultCredentialsProvider가 찾는 순서(요약)
환경에 따라 SDK가 아래 중 사용 가능한 것을 자동으로 선택합니다.
- 환경변수: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` (선택: `AWS_SESSION_TOKEN`)
- Web Identity(주로 EKS IRSA): `AWS_ROLE_ARN`, `AWS_WEB_IDENTITY_TOKEN_FILE`
- ECS Task Role / EC2 Instance Profile (메타데이터)
- 로컬 개발자 환경의 설정 파일(선택): `~/.aws/credentials`, `~/.aws/config` (선택: `AWS_PROFILE`)

## 로컬(LocalStack) 권장
- `.env.local`에 아래 값을 둡니다.
  - `SPRING_PROFILES_ACTIVE="aws"`
  - `SMARTDOC_AWS_S3_ENDPOINT="http://localhost:4566"`
  - `AWS_ACCESS_KEY_ID="test"`
  - `AWS_SECRET_ACCESS_KEY="test"`
  - `SMARTDOC_S3_AUTO_CREATE_BUCKET="true"` (로컬 편의)

주의:
- endpoint override(`SMARTDOC_AWS_S3_ENDPOINT`)가 비어 있으면 “실제 AWS”로 요청이 나갑니다.
- 비용/보안을 위해 로컬 테스트 시에는 반드시 LocalStack endpoint를 지정합니다.

## 운영(EKS) 권장: IRSA
- Kubernetes ServiceAccount에 IAM Role을 연결(IRSA)하고, Pod에 Web Identity 환경변수가 주입되도록 구성합니다.
- 이 경우 애플리케이션은 `AWS_ACCESS_KEY_ID` 같은 값을 몰라도 동작합니다.

## 앱에서 사용하는 환경변수
- 공통
  - `SMARTDOC_AWS_REGION`
- S3(document)
  - `SMARTDOC_S3_BUCKET`
  - `SMARTDOC_AWS_S3_ENDPOINT` (LocalStack일 때만)
  - `SMARTDOC_AWS_S3_PATH_STYLE` (LocalStack일 때 보통 true)
  - `SMARTDOC_S3_AUTO_CREATE_BUCKET` (기본 false, 로컬 편의)
- Textract/Comprehend(analysis)
  - `SMARTDOC_TEXTRACT_ENABLED` (기본 false)
  - `SMARTDOC_AWS_TEXTRACT_ENDPOINT` (LocalStack일 때만)
  - `SMARTDOC_COMPREHEND_ENABLED` (기본 false)
  - `SMARTDOC_AWS_COMPREHEND_ENDPOINT` (선택)

