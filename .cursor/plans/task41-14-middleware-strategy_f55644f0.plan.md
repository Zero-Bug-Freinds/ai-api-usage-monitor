---
name: task41-14-middleware-strategy
overview: "Define and apply per-service middleware strategy: add UI cookie middleware for notification-web, keep proxy-service API-only auth model, and verify no impact on usage stats/RabbitMQ event publishing."
todos:
  - id: add-notification-middleware
    content: Create notification-web middleware aligned with usage cookie policy and /notifications matcher.
    status: completed
  - id: review-proxy-auth-path
    content: Confirm proxy-service remains API-only and document JSON error response enhancement options without breaking CLI usage.
    status: completed
  - id: verify-no-event-regression
    content: Validate that middleware/auth strategy changes do not alter usage stats and RabbitMQ event publish flow.
    status: completed
  - id: run-checklist
    content: Execute and report notification cookie-flow checks and proxy CLI/auth checks.
    status: completed
isProject: false
---

# Task41-14: 서비스별 미들웨어 전략 수립 및 적용

## 현재 상태 요약 (코드 확인 결과)

- `notification-web`에는 미들웨어 파일이 없음 (쿠키 기반 보호 라우팅 미적용).
  - 확인 경로: `[services/notification-service/web](c:/Users/woori/Desktop/팀프로젝트1/services/notification-service/web)`
  - 앱 basePath: `[services/notification-service/web/next.config.ts](c:/Users/woori/Desktop/팀프로젝트1/services/notification-service/web/next.config.ts)`
- `usage-web`는 `access_token || is_logged_in` 패스를 이미 사용.
  - `[services/usage-service/web/middleware.ts](c:/Users/woori/Desktop/팀프로젝트1/services/usage-service/web/middleware.ts)`
- `proxy-service`는 UI가 없고, 이미 API 전용 인증 경계가 존재:
  - Spring Security로 `/proxy/**`만 인증 대상
  - `GatewayAuthWebFilter`가 `X-Gateway-Auth`를 검증(옵션)
  - 관련 파일:
    - `[services/proxy-service/src/main/java/com/eevee/proxyservice/security/SecurityConfiguration.java](c:/Users/woori/Desktop/팀프로젝트1/services/proxy-service/src/main/java/com/eevee/proxyservice/security/SecurityConfiguration.java)`
    - `[services/proxy-service/src/main/java/com/eevee/proxyservice/security/GatewayAuthWebFilter.java](c:/Users/woori/Desktop/팀프로젝트1/services/proxy-service/src/main/java/com/eevee/proxyservice/security/GatewayAuthWebFilter.java)`
    - `[services/proxy-service/src/main/resources/application.yml](c:/Users/woori/Desktop/팀프로젝트1/services/proxy-service/src/main/resources/application.yml)`

## 전략

- `notification-service (UI)`:
  - `identity/billing/usage`와 동일한 쿠키 기반 보호 정책 적용.
  - `access_token || is_logged_in === "true"`이면 통과, 아니면 identity 로그인으로 리다이렉트.
  - `basePath=/notifications` 특성에 맞게 matcher를 `/notifications`, `/notifications/`, `/notifications/((?!_next/|api/).+)`로 구성.
- `proxy-service (API 전용)`:
  - 브라우저 리다이렉트형 쿠키 미들웨어는 **적용하지 않음**.
  - 현재 헤더 기반 검증 구조 유지 + JSON 에러 응답 구조 필요성만 보강 검토.
  - CLI/터미널 직접 호출 호환성 보존(개발 모드에서 `require-auth=false` 경로 유지).

## 구현 계획

1. `notification-web` 미들웨어 신규 추가
  - 파일: `[services/notification-service/web/middleware.ts](c:/Users/woori/Desktop/팀프로젝트1/services/notification-service/web/middleware.ts)`
  - 내용: usage 패턴을 기준으로 `access_token/is_logged_in` 검사 + `/api/`** 예외 + identity 로그인 URL 구성.
2. `proxy-service` 인증 구조 유지 + 개선 제안 정리
  - 코드 변경은 최소화하고, 필요 시 “JSON 에러 body 표준화”만 별도 보강(컨트롤러/필터 영향도 점검 후).
3. 영향도 검증 (usage 통계/RabbitMQ)
  - `notification-web` 미들웨어 변경이 백엔드 이벤트 발행 경로를 건드리지 않음을 확인:
    - 이벤트 발행: `[services/proxy-service/src/main/java/com/eevee/proxyservice/mq/UsageEventPublisher.java](c:/Users/woori/Desktop/팀프로젝트1/services/proxy-service/src/main/java/com/eevee/proxyservice/mq/UsageEventPublisher.java)`
    - 릴레이 경로: `[services/proxy-service/src/main/java/com/eevee/proxyservice/relay/ProxyRelayService.java](c:/Users/woori/Desktop/팀프로젝트1/services/proxy-service/src/main/java/com/eevee/proxyservice/relay/ProxyRelayService.java)`

## 검증 체크리스트

- Notification(UI)
  - 로그인 후 `/notifications` 진입 시 리다이렉트 루프 없이 페이지 진입.
  - 쿠키 존재 시 `/notifications/`* 정상 통과.
  - 쿠키 제거 후 `/notifications/`* 접근 시 identity 로그인으로 리다이렉트.
- Proxy(API)
  - 터미널에서 `/proxy/`** 호출 시(개발 모드/허용 설정) 기존처럼 호출 가능.
  - `require-auth=true` 설정 시 `X-Gateway-Auth` 누락 요청이 정책대로 거부.
  - 정상 호출에서 usage 이벤트 발행이 기존과 동일하게 동작(큐/로그 확인).

## 산출물

- `notification-web` 미들웨어 코드 추가
- `proxy-service` 인증 구조 진단 리포트(유지/개선 포인트)
- 검증 결과 요약 (cookie flow + proxy CLI + usage/rabbit 영향 없음 확인)

