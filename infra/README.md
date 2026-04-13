# 인프라 온보딩

이 디렉토리는 로컬 개발 환경에서 배포 준비 흐름을 검증하기 위한 템플릿입니다.
우선순위는 `배포 준비 > 로컬 앱 실행 > 문서화`입니다.

## 목적
- Docker 기반 통합 실행 기준선 제공
- 로컬 K8s(kind/minikube) 배포 연습 기준 제공
- 운영 전 확장 포인트 식별

## 현재 상태
### 구현됨
- Compose 기반 placeholder 서비스 묶음
- Kubernetes base namespace/deployment/service 골격

### 미구현
- Secret/ConfigMap 분리
- Probe/HPA/Ingress/관측성
- 운영 보안 정책(IAM, 네트워크 정책, 시크릿 관리)

## 선행 조건
1. Docker 준비
2. `kubectl` 설치
3. `kind` 또는 `minikube` 설치

## 실행/검증 순서
1. Docker 통합 확인
   - [`infra/docker/README.md`](./docker/README.md)
2. Kubernetes base 적용
   - [`infra/k8s/README.md`](./k8s/README.md)
3. 결과를 루트 온보딩 순서와 교차 점검
   - [`README.md`](../README.md)

## 다음 단계
- Compose 이미지/명령을 실제 서비스 런타임으로 교체
- K8s에 Secret/ConfigMap, Probe, HPA, Ingress 추가
- CI에서 `docker compose config` + `kubectl apply --dry-run=client` 검증 자동화
