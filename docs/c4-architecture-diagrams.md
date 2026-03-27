# AI Usage & Billing Platform - C4 Architecture Diagrams (Code As-Is)

이 문서는 목표 설계가 아니라, 현재 저장소에 존재하는 구현 코드 기준으로
시스템 아키텍처를 C4 모델(C1 → C4)로 정리한다.

분석 대상:
- `services/api-gateway-service`
- `services/proxy-service`
- `services/identity-service`
- `services/usage-service`
- `libs/usage-events`
- 서비스별 `application.yml`/`application.properties`

## C1 - System Context

```mermaid
flowchart TB
    client["Developer / Client App"]

    subgraph platform["AI Usage Platform (As-Is)"]
      gateway["API Gateway Service"]
      proxy["Proxy Service"]
      identity["Identity Service"]
      usage["Usage Service"]
    end

    openai["OpenAI API"]
    anthropic["Anthropic API"]
    google["Google Gemini API"]
    keysvc["API Key Service"]
    rabbit["RabbitMQ"]
    appDb["PostgreSQL (app)"]
    usageDb["PostgreSQL (usage_db)"]

    client -->|Auth API| identity
    client -->|/api/v1/ai/**| gateway
    gateway -->|rewrite to /proxy/**| proxy

    proxy -->|LLM request relay| openai
    proxy -->|LLM request relay| anthropic
    proxy -->|LLM request relay| google
    proxy -->|provider key lookup| keysvc
    proxy -->|publish usage.recorded| rabbit

    usage -->|consume usage.recorded| rabbit
    identity -->|JPA| appDb
    usage -->|JPA| usageDb
```

## C2 - Container Diagram

```mermaid
C4Container
title C2: Containers (Current Implementation)

Person(client, "Developer/User", "Auth and AI API consumer")

System_Boundary(platform, "AI Usage Platform") {
    Container(gateway, "API Gateway Service", "Spring Cloud Gateway (WebFlux)", "JWT security and route rewrite /api/v1/ai/** -> /proxy/**")
    Container(proxy, "Proxy Service", "Spring Boot WebFlux", "Provider relay, usage parsing, usage event publishing")
    Container(identity, "Identity Service", "Spring Boot + Spring Security + JPA", "Signup/Login, JWT issuance, user persistence")
    Container(usage, "Usage Service", "Spring Boot + RabbitMQ + JPA", "Consume usage events, idempotent persistence")

    ContainerQueue(rabbit, "RabbitMQ", "AMQP", "usage.events / usage.recorded")
    ContainerDb(appDb, "PostgreSQL (app)", "RDB", "Identity domain data")
    ContainerDb(usageDb, "PostgreSQL (usage_db)", "RDB", "Usage logs")
}

System_Ext(openai, "OpenAI API", "LLM provider")
System_Ext(anthropic, "Anthropic API", "LLM provider")
System_Ext(google, "Google Gemini API", "LLM provider")
System_Ext(keysvc, "API Key Service", "provider key source")

Rel(client, identity, "POST /api/auth/signup, /login", "HTTPS")
Rel(client, gateway, "AI request", "HTTPS")
Rel(gateway, proxy, "forward with trusted headers", "HTTP")

Rel(proxy, keysvc, "resolve provider API key", "HTTP")
Rel(proxy, openai, "relay", "HTTPS")
Rel(proxy, anthropic, "relay", "HTTPS")
Rel(proxy, google, "relay", "HTTPS")
Rel(proxy, rabbit, "publish usage.recorded", "AMQP")

Rel(usage, rabbit, "consume usage.recorded", "AMQP")
Rel(identity, appDb, "read/write user data", "JPA")
Rel(usage, usageDb, "read/write usage logs", "JPA")
```

## C3 - Component Diagram (Cross-Service Runtime Flow)

```mermaid
flowchart LR
  subgraph GW["API Gateway Service"]
    gwSec["SecurityConfiguration / JwtDecoderConfiguration"]
    gwFilter["ProxyTrustHeadersGatewayFilter"]
    gwRoute["Gateway Route (application.yml)"]
    gwSec --> gwFilter --> gwRoute
  end

  subgraph PX["Proxy Service"]
    pxCtl["ProxyController (/proxy/**)"]
    pxRelay["ProxyRelayService"]
    pxReg["ProviderRegistry + ProviderHandler*"]
    pxKey["ApiKeyClient"]
    pxCtx["UserContextResolver"]
    pxPub["UsageEventPublisher"]
    pxCtl --> pxRelay
    pxRelay --> pxReg
    pxRelay --> pxKey
    pxRelay --> pxCtx
    pxRelay --> pxPub
  end

  subgraph US["Usage Service"]
    usListener["UsageRecordedEventListener (@RabbitListener)"]
    usSvc["UsageRecordedService"]
    usRepo["UsageRecordedLogRepository"]
    usEntity["UsageRecordedLogEntity"]
    usListener --> usSvc --> usRepo --> usEntity
  end

  subgraph ID["Identity Service"]
    idCtl["AuthController"]
    idSvc["UserService"]
    idRepo["UserRepository / RoleRepository"]
    idJwt["JwtTokenProvider + JwtAuthenticationFilter"]
    idCtl --> idSvc --> idRepo
    idSvc --> idJwt
  end

  GW -->|forward AI path| PX
  PX -->|publish usage.recorded| RABBIT["RabbitMQ"]
  RABBIT -->|deliver usage.recorded| US
```

## C4 - Code Diagram (Proxy Relay Core)

```mermaid
classDiagram
direction LR

class ProxyController {
  +Mono~ResponseEntity~ proxy(ServerWebExchange)
}

class ProxyRelayService {
  +Mono~ResponseEntity~ relay(ServerWebExchange)
  -Mono~Void~ publishUsage(...)
}

class ProviderRegistry {
  +ProviderHandler get(AiProvider)
}

class ProviderHandler {
  <<interface>>
  +AiProvider provider()
  +URI buildUpstreamUri(...)
  +void applyUpstreamAuth(...)
  +TokenUsage parseUsageFromResponseJson(...)
}

class ApiKeyClient {
  +Mono~String~ resolveApiKey(String, AiProvider)
}

class UserContextResolver {
  +Mono~UserContext~ fromExchange(ServerWebExchange)
}

class UsageEventPublisher {
  +Mono~Void~ publish(UsageRecordedEvent)
}

class UsageRecordedEvent
class TokenUsage
class AiProvider

ProxyController --> ProxyRelayService : relay()
ProxyRelayService --> ProviderRegistry : select handler
ProxyRelayService --> ProviderHandler : provider-specific behavior
ProxyRelayService --> ApiKeyClient : resolve key
ProxyRelayService --> UserContextResolver : resolve user/org/team
ProxyRelayService --> UsageEventPublisher : publish usage event
UsageEventPublisher --> UsageRecordedEvent : serialize payload
UsageRecordedEvent --> TokenUsage : includes token stats
ProviderHandler --> AiProvider : strategy by provider
```

## C4 - Code Diagram (Usage Persistence Core)

```mermaid
classDiagram
direction LR

class UsageRecordedEventListener {
  +void onMessage(String)
}

class UsageRecordedService {
  +void persist(UsageRecordedEvent)
}

class UsageRecordedLogRepository {
  <<interface>>
  +boolean existsByEventId(String)
  +UsageRecordedLogEntity save(UsageRecordedLogEntity)
}

class UsageRecordedLogEntity
class UsageRecordedEvent

UsageRecordedEventListener --> UsageRecordedService : deserialize + persist
UsageRecordedService --> UsageRecordedLogRepository : existsByEventId / save
UsageRecordedService --> UsageRecordedLogEntity : map domain -> entity
```

## C4 - Code Diagram (Gateway Trust Header Flow)

```mermaid
classDiagram
direction LR

class ProxyTrustHeadersGatewayFilter {
  +Mono~Void~ filter(ServerWebExchange, GatewayFilterChain)
  -Mono~Void~ forwardWithJwt(...)
  -Mono~Void~ forwardDevHeaders(...)
}

class SecurityConfiguration
class JwtDecoderConfiguration
class GatewayProperties
class JwtAuthenticationToken
class GatewayFilterChain

SecurityConfiguration --> JwtDecoderConfiguration : jwt decoder
SecurityConfiguration --> ProxyTrustHeadersGatewayFilter : security chain
ProxyTrustHeadersGatewayFilter --> JwtAuthenticationToken : read sub/org_id/team_id
ProxyTrustHeadersGatewayFilter --> GatewayProperties : devMode/sharedSecret
ProxyTrustHeadersGatewayFilter --> GatewayFilterChain : mutate headers + forward
```

## C4 - Code Diagram (Identity Auth Core)

```mermaid
classDiagram
direction LR

class AuthController {
  +ApiResponse~SignupResponse~ signup(SignupRequest)
  +ApiResponse~LoginResponse~ login(LoginRequest)
}

class UserService {
  +SignupResponse signup(SignupRequest)
  +LoginResponse login(LoginRequest)
}

class UserRepository {
  <<interface>>
  +Optional~User~ findByEmail(String)
  +boolean existsByEmail(String)
  +User save(User)
}

class RoleRepository {
  <<interface>>
  +Optional~Role~ findByName(RoleName)
}

class JwtTokenProvider {
  +String createAccessToken(Long, String)
  +Authentication getAuthentication(String)
  +boolean validateToken(String)
}

class JwtAuthenticationFilter {
  +void doFilterInternal(HttpServletRequest, HttpServletResponse, FilterChain)
}

class SecurityConfig
class PasswordEncoder
class User
class Role

AuthController --> UserService : signup/login delegated
UserService --> UserRepository : user lookup/save
UserService --> RoleRepository : default role resolve
UserService --> PasswordEncoder : password hash/verify
UserService --> JwtTokenProvider : issue access token
SecurityConfig --> JwtAuthenticationFilter : register filter chain
JwtAuthenticationFilter --> JwtTokenProvider : parse/validate token
UserRepository --> User : persistence
RoleRepository --> Role : persistence
```

## 참고 코드/문서

- `services/api-gateway-service/src/main/resources/application.yml`
- `services/api-gateway-service/src/main/java/com/eevee/apigateway/filter/ProxyTrustHeadersGatewayFilter.java`
- `services/proxy-service/src/main/java/com/eevee/proxyservice/web/ProxyController.java`
- `services/proxy-service/src/main/java/com/eevee/proxyservice/relay/ProxyRelayService.java`
- `services/proxy-service/src/main/java/com/eevee/proxyservice/mq/UsageEventPublisher.java`
- `services/usage-service/src/main/java/com/eevee/usageservice/consumer/UsageRecordedEventListener.java`
- `services/usage-service/src/main/java/com/eevee/usageservice/service/UsageRecordedService.java`
- `services/identity-service/src/main/java/com/zerobugfreinds/identity_service/controller/AuthController.java`
- `services/identity-service/src/main/java/com/zerobugfreinds/identity_service/service/UserService.java`
- `services/identity-service/src/main/java/com/zerobugfreinds/identity_service/security/JwtTokenProvider.java`
- `services/identity-service/src/main/java/com/zerobugfreinds/identity_service/security/JwtAuthenticationFilter.java`
