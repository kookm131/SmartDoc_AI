# SmartDoc Document 서비스

## 목적
- 문서 업로드 및 메타데이터 수명주기 관리
- 분석 파이프라인 진입 전 원본/상태 정보 관리

## 현재 상태
### 구현됨
- Spring Boot 템플릿 앱
- 기본 문서 등록 엔드포인트 골격

### 미구현
- S3 업로드 실연동
- DB 스키마/트랜잭션 정책 확정

## 선행 조건
1. Java 17
2. `.env.example` 복사
   - `cp .env.example .env`
3. 공통 규칙 확인
   - [`backend/CONVENTIONS.md`](../../CONVENTIONS.md)

## 실행/검증 순서
1. 실행
   - `gradle bootRun` (또는 `./gradlew bootRun`)
2. 기본 엔드포인트 확인
   - `POST /api/v1/documents`

## 표준 설정
- 포트: `8081`
- 프로필: `local`
- 패키지: `com.smartdoc.document`
- 환경변수 접두사: `SMARTDOC_DOCUMENT_*`

## 다음 단계
- 파일 업로드/메타데이터 저장 트랜잭션 연결
- 분석 서비스 연계 이벤트 발행 구조 추가
