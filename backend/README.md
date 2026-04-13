# 백엔드 서비스

SmartDoc AI용 Spring Boot 마이크로서비스 스캐폴딩입니다.

## 서비스 목록
- `gateway` (포트 `8080`): API 진입점 및 라우팅
- `document` (포트 `8081`): 문서 수명주기 관리
- `analysis` (포트 `8082`): AI 분석 오케스트레이션
- `notification` (포트 `8083`): 알림 및 디스패치 워크플로우

## 공통 규칙
패키지/프로필/로깅 정책은 [`CONVENTIONS.md`](./CONVENTIONS.md)를 참고하세요.
