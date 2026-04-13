# Kubernetes 배포 템플릿 (로컬 K8s 기준)

이 폴더는 kind/minikube 환경에서 배포 준비 흐름을 검증하기 위한 시작 매니페스트를 제공합니다.

## 목적
- 로컬 클러스터에서 namespace/deployment/service 적용 절차 표준화
- 이후 EKS 전환 전 필요한 확장 포인트 정리

## 현재 상태
### 구현됨
- `base/namespace.yaml`
- `base/*-deployment.yaml`
- `base/*-service.yaml`

### 미구현
- ConfigMap/Secret
- Liveness/Readiness Probe
- HPA
- Ingress(ALB 연계 전 단계)

## 선행 조건
1. `kubectl` 설치
2. kind 또는 minikube 클러스터 준비
3. 현재 컨텍스트 확인
   - `kubectl config current-context`

## 실행/검증 순서
1. 네임스페이스 적용
   - `kubectl apply -f base/namespace.yaml`
2. 전체 base 리소스 적용
   - `kubectl apply -f base/`
3. 배포 상태 확인
   - `kubectl get deploy,svc -n smartdoc`
4. 롤아웃 확인
   - `kubectl rollout status deployment/gateway -n smartdoc`

## base 매니페스트 역할
- `namespace.yaml`: 공용 네임스페이스 정의
- `*-deployment.yaml`: 서비스별 Pod 배포 골격
- `*-service.yaml`: 내부 통신용 ClusterIP 서비스

## 다음 단계
- 환경별 오버레이(kustomize) 도입
- Secret/ConfigMap 및 리소스 제한 추가
- Ingress, 모니터링, 로그 수집 체계 연결
