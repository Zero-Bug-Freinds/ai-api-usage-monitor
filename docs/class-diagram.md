# Class Diagram

`docs/c4-architecture-diagrams.md`에 붙여 넣을 수 있도록 Mermaid 클래스 다이어그램을 정리합니다. 최근 변경(Usage 대시보드·분석 API, 게이트웨이 신뢰 필터, Identity 세션 API 등)을 반영했습니다.

**가독성:** 전 서비스를 한 그림에 넣으면 노드가 과다해지므로, **① 교차 서비스 개요** 후 **② 서비스·관심사별 서브다이어그램**으로 나눴습니다.

**이름 충돌:** Java 코드에서 `SecurityConfiguration`이 proxy-service와 api-gateway-service에 각각 있습니다. 아래 다이어그램에서는 **`ProxySecurityConfiguration`** / **`GatewaySecurityConfiguration`** 으로 구분합니다 (각각 `com.eevee.proxyservice.security.SecurityConfiguration`, `com.eevee.apigateway.config.SecurityConfiguration`).

---

## 1) Cross-service overview (경량)

```mermaid
classDiagram
direction LR

class AuthController
class UserService
class ProxyController
class ProxyRelayService
class UsageEventPublisher
class ProxyTrustHeadersWebFilter
class GatewaySecurityConfiguration
class JwtDecoderConfiguration
class GatewayProperties
class ReactiveJwtDecoder
class UsageAnalyticsController
class UsageDashboardService
class UsageRecordedEventListener
class UsageRecordedService
class UsageRecordedEvent

AuthController --> UserService
ProxyController --> ProxyRelayService
ProxyRelayService --> UsageEventPublisher
UsageEventPublisher ..> UsageRecordedEvent : publishes JSON
UsageRecordedEventListener --> UsageRecordedService
UsageRecordedEventListener ..> UsageRecordedEvent : deserializes

ProxyTrustHeadersWebFilter --> GatewayProperties
GatewaySecurityConfiguration --> GatewayProperties
GatewaySecurityConfiguration ..> ReactiveJwtDecoder
JwtDecoderConfiguration --> GatewayProperties
JwtDecoderConfiguration ..> ReactiveJwtDecoder

UsageAnalyticsController --> UsageDashboardService
```

---

## 2) Identity Service

```mermaid
classDiagram
direction TB

class AuthController
class GlobalExceptionHandler
class UserService
class UserRepository
class JpaRepository
class User
class Role
class SignupRequest
class SignupResponse
class LoginRequest
class LoginResponse
class SessionResponse
class SecurityConfig
class JwtAuthenticationFilter
class OncePerRequestFilter
class JwtTokenProvider
class RestAuthenticationEntryPoint
class AuthenticationEntryPoint

AuthController --> UserService
AuthController ..> SignupRequest
AuthController ..> SignupResponse
AuthController ..> LoginRequest
AuthController ..> LoginResponse
AuthController ..> SessionResponse
UserService --> UserRepository
UserService --> JwtTokenProvider
UserService ..> User
UserService ..> SignupRequest
UserService ..> SignupResponse
UserService ..> LoginRequest
UserService ..> LoginResponse
UserRepository --|> JpaRepository
User --> Role
JwtAuthenticationFilter --|> OncePerRequestFilter
JwtAuthenticationFilter --> JwtTokenProvider
RestAuthenticationEntryPoint --|> AuthenticationEntryPoint
SecurityConfig --> JwtAuthenticationFilter
SecurityConfig --> RestAuthenticationEntryPoint
```

---

## 3) Proxy Service

WebFlux 보안은 코드상 `com.eevee.proxyservice.security.SecurityConfiguration` 이 `SecurityWebFilterChain`을 등록합니다. 아래는 릴레이·프로바이더·신뢰 헤더 중심입니다.

```mermaid
classDiagram
direction TB

class ProxyController
class ProxyRelayService
class ProviderHandler
class ProviderRegistry
class OpenAiProviderHandler
class AnthropicProviderHandler
class GoogleProviderHandler
class ApiKeyClient
class UsageEventPublisher
class GatewayAuthWebFilter
class LocalUserHeadersWebFilter
class WebFilter
class UserContextResolver
class UserContext
class ProxyProperties
class UsageRecordedEvent
class TokenUsage
class AiProvider

ProxyController --> ProxyRelayService
ProxyRelayService --> ProviderRegistry
ProxyRelayService --> ApiKeyClient
ProxyRelayService --> UsageEventPublisher
ProxyRelayService --> UserContextResolver
ProxyRelayService ..> ProviderHandler
ProxyRelayService ..> UserContext
ProxyRelayService ..> UsageRecordedEvent
ProxyRelayService ..> TokenUsage
ProxyRelayService ..> AiProvider
ProviderRegistry --> ProviderHandler
OpenAiProviderHandler --|> ProviderHandler
AnthropicProviderHandler --|> ProviderHandler
GoogleProviderHandler --|> ProviderHandler
OpenAiProviderHandler ..> TokenUsage
AnthropicProviderHandler ..> TokenUsage
GoogleProviderHandler ..> TokenUsage
GatewayAuthWebFilter --|> WebFilter
LocalUserHeadersWebFilter --|> WebFilter
GatewayAuthWebFilter --> ProxyProperties
UserContextResolver ..> UserContext
UsageEventPublisher --> ProxyProperties
UsageEventPublisher ..> UsageRecordedEvent
```

---

## 4) API Gateway Service

```mermaid
classDiagram
direction TB

class ProxyTrustHeadersWebFilter
class WebFilter
class GatewaySecurityConfiguration
class JwtDecoderConfiguration
class GatewayProperties
class ReactiveJwtDecoder

ProxyTrustHeadersWebFilter --|> WebFilter
ProxyTrustHeadersWebFilter --> GatewayProperties
GatewaySecurityConfiguration --> GatewayProperties
GatewaySecurityConfiguration ..> ReactiveJwtDecoder
JwtDecoderConfiguration --> GatewayProperties
JwtDecoderConfiguration ..> ReactiveJwtDecoder
```

---

## 5) Usage Service — Gateway trust & dashboard HTTP

게이트웨이가 넘긴 `X-User-Id` / `X-Gateway-Auth`를 **`UsageGatewayTrustFilter`**에서 검증·요청 속성으로 두고, **`UsageAnalyticsController`**가 **`UsageDashboardService`**로 대시보드·로그 조회를 위임합니다.

```mermaid
classDiagram
direction TB

class UsageGatewayTrustFilter
class OncePerRequestFilter
class UsageServiceProperties
class UsageAnalyticsController
class UsageDashboardService
class UsageAnalyticsJdbcRepository
class UsageRecordedLogRepository
class JdbcTemplate
class UsageApiExceptionHandler

UsageApiExceptionHandler ..> UsageAnalyticsController

UsageGatewayTrustFilter --|> OncePerRequestFilter
UsageGatewayTrustFilter --> UsageServiceProperties
UsageAnalyticsController --> UsageDashboardService
UsageAnalyticsController ..> UsageGatewayTrustFilter : ATTR_USER_ID
UsageDashboardService --> UsageAnalyticsJdbcRepository
UsageDashboardService --> UsageRecordedLogRepository
UsageDashboardService --> UsageServiceProperties
UsageAnalyticsJdbcRepository ..> JdbcTemplate
```

---

## 6) Usage Service — Rabbit ingestion & persistence

```mermaid
classDiagram
direction TB

class UsageRecordedEventListener
class UsageRecordedService
class UsageRecordedLogRepository
class UsageRecordedLogEntity
class JpaRepository
class UsageRabbitConfiguration
class UsageRabbitProperties
class UsageJacksonConfiguration
class ObjectMapper
class UsageRecordedEvent
class TokenUsage
class AiProvider

UsageJacksonConfiguration ..> ObjectMapper
UsageRecordedEventListener --> ObjectMapper
UsageRecordedEventListener --> UsageRecordedService
UsageRecordedEventListener ..> UsageRecordedEvent
UsageRecordedService --> UsageRecordedLogRepository
UsageRecordedService ..> UsageRecordedLogEntity
UsageRecordedService ..> UsageRecordedEvent
UsageRecordedService ..> TokenUsage
UsageRecordedLogRepository --|> JpaRepository
UsageRecordedLogEntity --> AiProvider
UsageRabbitConfiguration --> UsageRabbitProperties
UsageRecordedEvent --> TokenUsage
UsageRecordedEvent --> AiProvider
```

---

## 7) Lib — `usage-events` (공유 계약)

```mermaid
classDiagram
direction TB

class UsageRecordedEvent
class TokenUsage
class AiProvider

UsageRecordedEvent --> TokenUsage
UsageRecordedEvent --> AiProvider
```

---

## 8) Web (`apps/web`) — 다이어그램 (팀원 C · Frontend)

Java 백엔드 절(1–7)과 달리, **Next.js 앱 Mermaid 도식은 `docs/c4-architecture-diagrams.md` 한 곳에만 두고** 디렉터리·BFF·미들웨어가 바뀔 때 그 절(W1–W4)을 갱신한다.

| 구분 | 문서 위치 |
|------|-----------|
| 디렉터리 맵·시퀀스·레이어·미들웨어 | [c4-architecture-diagrams.md](./c4-architecture-diagrams.md) 의 **「Web Application (`apps/web`)」** 절 (W1–W4) |
| C1/C2의 Browser / Web 컨테이너 | 동 파일 상단 C1·C2 |

**동기화:** `app/` 라우트·`api/auth/*/route.ts`·`middleware.ts`·`components/`·`lib/api/` 변경 시 위 앵커 절의 다이어그램·설명을 코드와 맞춘다. 구현과 `docs/contracts/web-identity-bff.md` 가 다르면 **다이어그램은 코드 우선**으로 수정하고 계약 문서는 별도로 정리한다. 팀 역할은 `docs/architecture.md` **§13(팀원 C·프론트)**·**§12(집계·알림 백엔드)** 를 본다.
