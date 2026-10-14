# src/api

`fetch`를 직접 호출할 수 있는 유일한 위치다 (ESLint `no-restricted-globals` 예외).
CSRF 헤더(`X-XSRF-TOKEN`) 부착과 오류 처리는 여기서 한 번만 구현한다. — 기능 PR에서 테스트와 함께 추가.
