# SmartDoc Analysis 서비스

## 목적
- 문서 분석 오케스트레이션 수행
- Textract/Comprehend 결과를 도메인 데이터로 변환

## 현재 상태
### 구현됨
- Spring Boot 템플릿 앱
- 분석 작업 요청 엔드포인트 골격

### 미구현
- AWS Textract/Comprehend API 실연동
- 분석 실패 재시도/보상 처리

## 선행 조건
1. Java 17
2. `.env.example` 복사
   - `cp .env.example .env`
3. 공통 규칙 확인
   - [`backend/CONVENTIONS.md`](../../CONVENTIONS.md)

## 실행/검증 순서
1. 실행
   - `./gradlew bootRun`
2. 기본 엔드포인트 확인
   - `POST /api/v1/analysis/jobs`

## 표준 설정
- 포트: `8082`
- 프로필: `local`
- 패키지: `com.smartdoc.analysis`
- 환경변수 접두사: `SMARTDOC_ANALYSIS_*`

## 다음 단계
- 비동기 작업 큐/상태 관리 도입
- 분석 결과 정규화 및 저장 전략 확정
