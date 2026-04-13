# Kubernetes 템플릿

이 폴더는 EKS 배포를 위한 시작용 매니페스트를 제공합니다.

- `base/namespace.yaml`: 공용 네임스페이스
- `base/*-deployment.yaml`: 서비스 배포 골격
- `base/*-service.yaml`: 내부 라우팅용 ClusterIP 서비스

현재 매니페스트는 최소 구성으로 작성되었으며, 아래 항목을 확장해야 합니다.
- ConfigMap/Secret
- 리소스 요청/제한
- Probes, HPA, Ingress/ALB 어노테이션
