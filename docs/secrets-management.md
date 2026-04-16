# Secrets Manager / Parameter Store Review (Deploy-Time Injection)

이 프로젝트는 **애플리케이션 코드는 env만 읽고**, 운영 환경(EKS/ECS/VM 배포 스크립트)이 **Secret/Config를 주입**하는 방식을 기준선으로 잡습니다.

## 원칙
- “민감정보(Secret)”는 Git에 넣지 않습니다.
- 앱은 `DefaultCredentialsProvider`를 기본으로 사용하고(운영은 IRSA/instance profile), 로컬/개발에서만 dummy key를 env로 주입합니다.
- 런타임에 Secrets Manager/SSM을 직접 호출해서 값을 가져오는 방식은(권한/장애/캐시/타임아웃) 복잡도가 커서 기본 전략에서 제외합니다.

## Config vs Secret 분리 기준
- ConfigMap(비민감): base URL, timeout, feature flag, profile, 업로드 경로 등
- Secret(민감): JWT secret, DB password, API key, AWS static credentials(로컬 전용) 등

## Kubernetes 적용 방식(현재 repo 기준)
`infra/k8s/base`는 아래 형태를 사용합니다.
- ConfigMap: `infra/k8s/base/app-configmap.yaml`
- Secret: `infra/k8s/base/app-secret.yaml`
- 각 서비스 Deployment는 `envFrom`으로 ConfigMap/Secret을 함께 읽습니다.

주의:
- `infra/k8s/base/app-secret.yaml`에는 **placeholder**만 둡니다. 실제 운영 값은 배포 시점에 교체합니다.

## AWS에서의 실제 주입(추천)
- EKS: IRSA(ServiceAccount ↔ IAM Role)로 AWS 권한을 주입하고, S3/Textract/Comprehend는 SDK가 Web Identity로 인증합니다.
- 이 경우 `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`는 필요 없습니다.

## SSM/Secrets Manager를 “배포에서 주입”하는 방법(예시)
- Parameter Store/Secrets Manager에서 값을 읽어 CI/CD에서 `kubectl create secret` 또는 helm values로 주입
- 또는 External Secrets Operator(ESO)를 사용해 AWS ↔ K8s Secret 동기화

이번 로드맵 항목의 완료 기준은 “어떤 값이 Secret인지/Config인지, 어떤 경로로 주입하는지”가 문서와 매니페스트에 반영되는 것입니다.

