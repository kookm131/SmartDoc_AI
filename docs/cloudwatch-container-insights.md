# CloudWatch Container Insights (EKS)

이 저장소는 EKS에서 CloudWatch Container Insights를 **매니페스트로 설치**하는 방식을 사용합니다.

포함 리소스(overlay에 자동 포함):
- `cloudwatch-agent` DaemonSet (metrics)
- `aws-for-fluent-bit` DaemonSet (logs)
- `amazon-cloudwatch` namespace 및 최소 RBAC

## 배포 전 교체해야 하는 값
`infra/k8s/addons/container-insights`에는 placeholder가 있습니다.
- `REPLACE_ME_CLUSTER_NAME`
- `REPLACE_ME_IRSA_ROLE_ARN`

## 권장: IRSA 사용
- EKS에서 ServiceAccount에 IAM Role을 연결(IRSA)해 CloudWatch 권한을 주입합니다.
- 이때 `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`를 K8s Secret에 넣지 않습니다.

## 적용 위치
EKS overlay에 포함되어 있습니다.
- `infra/k8s/overlays/eks-alb`
- `infra/k8s/overlays/eks-nginx`

## 참고
이 매니페스트는 “기본 동작”을 목표로 한 최소 구성이며,
로그 그룹/스트림 네이밍, 수집 범위, 필터링은 운영 요구사항에 맞게 조정해야 합니다.

