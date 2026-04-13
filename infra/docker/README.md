# Docker 통합 템플릿

`docker-compose.yml`은 로컬 통합 테스트를 위한 임시 기준점입니다.

## 목적
- DB + 백엔드 서비스 컨테이너 조합의 기본 기동 순서 확인
- 포트 충돌/네트워크 연결 등 초기 이슈 조기 발견

## 현재 상태
### 구현됨
- MSSQL 컨테이너
- gateway/document/analysis/notification placeholder 컨테이너

### 미구현
- 실제 서비스 이미지 빌드/배포
- AWS 대체 에뮬레이션(LocalStack 등)
- 운영 수준 시크릿/보안 설정

## 선행 조건
1. Docker 데몬 실행
2. 포트 `1433`, `8080`~`8083` 사용 가능

## 실행/검증 순서
1. 설정 검증
   - `docker compose -f docker-compose.yml config`
2. 컨테이너 기동
   - `docker compose -f docker-compose.yml up -d`
3. 상태 확인
   - `docker compose -f docker-compose.yml ps`
4. 로그 샘플 확인
   - `docker compose -f docker-compose.yml logs gateway --tail=20`
5. 종료
   - `docker compose -f docker-compose.yml down`

## 다음 단계
- placeholder command를 실제 앱 실행 명령으로 교체
- `.env` 분리 및 시크릿 외부화
- 헬스체크와 재시작 정책 강화
