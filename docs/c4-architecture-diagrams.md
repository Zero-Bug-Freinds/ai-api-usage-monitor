# AI Usage & Billing Platform - C4 Architecture Diagrams (Code As-Is)

이 문서는 설계 문서의 목표 구조가 아니라, 현재 저장소에 존재하는 코드만 기준으로
시스템 아키텍처를 C4 모델(C1 → C2 → C3 → C4)로 표현한다.

분석 대상(코드 기준):
- `services/api-gateway-service`
- `services/proxy-service`
- `services/identity-service`
- `services/usage-service`
- `docker-compose.yml`

## C1 - System Context

```mermaid
flowchart TB
    %% C1 의도를 유지하면서 겹침을 줄이기 위해 수동 배치
    title["C1: AI Usage & Analytics Platform - System Context (As-Is)"]

    dev["Developer/User<br/>플랫폼 API 호출 사용자"]

    openai["OpenAI API<br/>LLM Provider"]
    anthropic["Anthropic API<br/>LLM Provider"]
    google["Google Gemini API<br/>LLM Provider"]
    keyService["API Key Service<br/>Proxy 키 조회 대상"]

    platform["Platform (As-Is)<br/>Gateway + Proxy + Identity + Usage"]

    dev -->|인증/프록시 API 호출<br/>HTTPS| platform
    platform -->|AI 요청 중계<br/>HTTPS| openai
    platform -->|AI 요청 중계<br/>HTTPS| anthropic
    platform -->|AI 요청 중계<br/>HTTPS| google
    platform -->|Provider API Key 조회<br/>HTTP/internal| keyService

    %% 외부 시스템을 상단열로 정렬하고 Platform을 하단 중앙으로 유도
    openai --- anthropic
    anthropic --- google
    google --- keyService
```

## C2 - Container Diagram

```mermaid
C4Container
title C2: Containers (Code As-Is Only)

Person(devUser, "Developer/User", "플랫폼 API 호출 사용자")

System_Boundary(platform, "AI Usage & Billing Platform") {
    Container(gateway, "API Gateway Service", "Spring Cloud Gateway (WebFlux)", "JWT 검증, /api/v1/ai -> /proxy 라우팅")
    Container(proxy, "Proxy Service", "Spring Boot WebFlux", "Provider 중계, 사용량 추출, usage 이벤트 발행")
    Container(identity, "Identity Service", "Spring Boot + JPA", "회원가입/Auth API, 사용자 데이터 관리")
    Container(usage, "Usage Tracking Service", "Spring MVC + JPA", "usage 이벤트 소비 및 usage 로그 저장")

    ContainerDb(identityDb, "PostgreSQL (app)", "RDB", "Identity/Org 데이터")
    ContainerDb(usageDb, "PostgreSQL (usage_db)", "RDB", "Usage 로그 데이터")
    ContainerQueue(rabbit, "RabbitMQ", "Message Broker", "usage-recorded 등 이벤트")
}

System_Ext(openai, "OpenAI API", "LLM Provider")
System_Ext(anthropic, "Anthropic API", "LLM Provider")
System_Ext(google, "Google Gemini API", "LLM Provider")
System_Ext(keySvc, "API Key Service (External/Internal)", "Proxy key lookup target")

Rel(devUser, gateway, "AI 프록시 API 호출", "HTTPS")
Rel(devUser, identity, "회원가입 호출", "HTTPS")
Rel(gateway, proxy, "프록시 라우팅 + 신뢰 헤더 전달", "HTTP")
Rel(proxy, keySvc, "사용자별 Provider API Key 조회", "HTTP/internal")
Rel(proxy, openai, "Provider 호출", "HTTPS")
Rel(proxy, anthropic, "Provider 호출", "HTTPS")
Rel(proxy, google, "Provider 호출", "HTTPS")
Rel(proxy, rabbit, "usage-recorded 발행", "AMQP")

Rel(usage, rabbit, "usage-recorded 소비", "AMQP")
Rel(usage, usageDb, "usage 로그 저장", "JPA")
Rel(identity, identityDb, "사용자/조직/팀 저장", "JPA")
```

## C3 - Component Diagram (Proxy Service)

```mermaid
C4Component
title C3: Proxy Service Components

Container_Boundary(proxyBoundary, "Proxy Service (Spring WebFlux)") {
    Component(proxyController, "ProxyController", "WebFlux Controller", "/proxy/{provider}/** 엔드포인트")
    Component(authFilter, "GatewayAuthWebFilter", "WebFilter", "X-Gateway-Auth 검증")
    Component(userContextResolver, "UserContextResolver", "Security Component", "X-User-Id/X-Org-Id/X-Team-Id 해석")
    Component(relayService, "ProxyRelayService", "Service", "요청/응답 중계, usage 파싱")
    Component(providerRegistry, "ProviderRegistry", "Registry", "ProviderHandler 라우팅")
    Component(providerHandler, "ProviderHandler (OpenAI/Google/Anthropic)", "Adapter", "Provider별 URL/인증/usage 파싱")
    Component(apiKeyClient, "ApiKeyClient", "Service", "API Key Service 조회 + Caffeine 캐시")
    Component(eventPublisher, "UsageEventPublisher", "Messaging", "RabbitMQ usage 이벤트 발행")
}

Container_Ext(gateway, "API Gateway Service", "Spring Cloud Gateway")
Container_Ext(keySvc, "API Key Service", "Internal API")
Container_Ext(providerApis, "AI Providers", "OpenAI/Gemini/Anthropic")
Container_Ext(rabbit, "RabbitMQ", "Message Broker")

Rel(gateway, authFilter, "신뢰 헤더 포함 요청 전달", "HTTP")
Rel(authFilter, proxyController, "검증 통과 시 요청 전달", "WebFlux chain")
Rel(proxyController, relayService, "relay()", "Reactive call")
Rel(relayService, userContextResolver, "사용자 컨텍스트 해석", "Internal call")
Rel(relayService, apiKeyClient, "Provider 키 조회", "Reactive call")
Rel(relayService, providerRegistry, "Provider handler 조회", "Internal call")
Rel(providerRegistry, providerHandler, "provider별 구현 선택", "Internal call")
Rel(relayService, providerHandler, "업스트림 URL/usage 파싱", "Internal call")
Rel(relayService, providerApis, "요청 중계", "HTTPS (WebClient)")
Rel(relayService, eventPublisher, "UsageRecordedEvent 생성/발행", "Reactive call")
Rel(eventPublisher, rabbit, "usage.recorded 발행", "AMQP")
Rel(apiKeyClient, keySvc, "internal/api-keys/{provider}", "HTTP")
```

## C3 - Component Diagram (Usage Service)

```mermaid
C4Component
title C3: Usage Service Components (As-Is)

Container_Boundary(usageBoundary, "Usage Service (Spring MVC + JPA)") {
    Component(eventListener, "UsageRecordedEventListener", "@RabbitListener", "usage.rabbit.queue 메시지 소비")
    Component(recordedService, "UsageRecordedService", "Application Service", "중복 eventId 검사 + 엔티티 매핑")
    Component(recordedRepo, "UsageRecordedLogRepository", "Spring Data JPA", "UsageRecordedLogEntity 저장/조회")
    Component(recordedEntity, "UsageRecordedLogEntity", "JPA Entity", "usage 로그 영속 모델")
}

Container_Ext(rabbit, "RabbitMQ", "Message Broker")
ContainerDb_Ext(usageDb, "PostgreSQL (usage_db)", "RDB")

Rel(rabbit, eventListener, "usage.recorded 전달", "AMQP")
Rel(eventListener, recordedService, "onMessage(json)", "Internal call")
Rel(recordedService, recordedRepo, "existsByEventId/save", "JPA")
Rel(recordedRepo, recordedEntity, "ORM 매핑", "JPA")
Rel(recordedRepo, usageDb, "INSERT/SELECT", "SQL")
```

## C3 - Component Diagram (API Gateway Service)

```mermaid
C4Component
title C3: API Gateway Components (As-Is)

Container_Boundary(gatewayBoundary, "API Gateway Service (Spring Cloud Gateway)") {
    Component(securityConfig, "SecurityConfiguration", "Spring Security", "리소스 서버/JWT 보안 설정")
    Component(jwtDecoderConfig, "JwtDecoderConfiguration", "Config", "JWT secret 기반 디코더 구성")
    Component(trustFilter, "ProxyTrustHeadersGatewayFilter", "GlobalFilter", "X-User-Id/X-Gateway-Auth 헤더 부착")
    Component(routeConfig, "Gateway Routes (application.yml)", "SCG Route", "/api/v1/ai/** -> proxy rewrite")
}

Container_Ext(client, "Developer/User", "HTTP client")
Container_Ext(proxy, "Proxy Service", "Spring WebFlux")

Rel(client, securityConfig, "Bearer JWT 요청", "HTTPS")
Rel(securityConfig, jwtDecoderConfig, "JWT 검증 위임", "Internal")
Rel(securityConfig, trustFilter, "인증 컨텍스트 전달", "Reactive security context")
Rel(trustFilter, routeConfig, "헤더 보강 후 라우팅", "Gateway chain")
Rel(routeConfig, proxy, "RewritePath + forward", "HTTP")
```

## C4 - Code Diagram (Proxy Relay Core)

```mermaid
classDiagram
direction LR

class ProxyRelayService {
  +Mono~ResponseEntity~ relay(ServerWebExchange exchange)
  -Mono~ResponseEntity~ forward(...)
  -Mono~ResponseEntity~ mapResponse(...)
  -Mono~Void~ publishUsage(...)
}

class ProviderRegistry {
  -Map~AiProvider, ProviderHandler~ handlers
  +ProviderHandler get(AiProvider provider)
}

class ProviderHandler {
  <<interface>>
  +AiProvider provider()
  +String baseUrl()
  +URI buildUpstreamUri(...)
  +void applyUpstreamAuth(HttpHeaders headers, String apiKey)
  +TokenUsage parseUsageFromResponseJson(String body)
  +TokenUsage parseUsageFromSse(String sseBody)
}

class ApiKeyClient {
  +Mono~String~ resolveApiKey(String userId, AiProvider provider)
  -String loadKeyBlocking(String cacheKey)
}

class UserContextResolver {
  +Mono~UserContext~ fromExchange(ServerWebExchange exchange)
}

class UsageEventPublisher {
  +Mono~Void~ publish(UsageRecordedEvent event)
}

class UsageRecordedEvent
class TokenUsage
class AiProvider

ProxyRelayService --> ProviderRegistry : selects handler
ProxyRelayService --> ApiKeyClient : resolves API key
ProxyRelayService --> UserContextResolver : resolves tenant/user context
ProxyRelayService --> ProviderHandler : forwards/parses usage
ProxyRelayService --> UsageEventPublisher : publishes usage event
UsageEventPublisher --> UsageRecordedEvent : serializes
UsageRecordedEvent --> TokenUsage : contains
ProviderHandler --> AiProvider : per-provider strategy
```

## C4 - Code Diagram (Usage Event Persist Flow)

```mermaid
classDiagram
direction LR

class UsageRecordedEventListener {
  +void onMessage(String json)
}

class UsageRecordedService {
  +void persist(UsageRecordedEvent event)
  -UsageRecordedLogEntity map(UsageRecordedEvent event)
}

class UsageRecordedLogRepository {
  <<interface>>
  +boolean existsByEventId(String eventId)
  +UsageRecordedLogEntity save(UsageRecordedLogEntity entity)
}

class UsageRecordedLogEntity
class UsageRecordedEvent
class TokenUsage

UsageRecordedEventListener --> UsageRecordedEvent : deserialize(ObjectMapper)
UsageRecordedEventListener --> UsageRecordedService : persist(event)
UsageRecordedService --> UsageRecordedLogRepository : existsByEventId/save
UsageRecordedService --> UsageRecordedLogEntity : map(event)
UsageRecordedEvent --> TokenUsage : contains usage fields
```

## C4 - Code Diagram (Gateway Trust Header Flow)

```mermaid
classDiagram
direction LR

class ProxyTrustHeadersGatewayFilter {
  +Mono~Void~ filter(ServerWebExchange, GatewayFilterChain)
  -Mono~Void~ forwardWithJwt(...)
  -Mono~Void~ forwardDevHeaders(...)
  -void attachGatewayAuth(...)
}

class GatewayProperties {
  +boolean isDevMode()
  +String getSharedSecret()
}

class JwtAuthenticationToken
class ReactiveSecurityContextHolder
class GatewayFilterChain

ReactiveSecurityContextHolder --> ProxyTrustHeadersGatewayFilter : supplies Authentication
ProxyTrustHeadersGatewayFilter --> JwtAuthenticationToken : reads sub/org_id/team_id
ProxyTrustHeadersGatewayFilter --> GatewayProperties : reads dev-mode/shared-secret
ProxyTrustHeadersGatewayFilter --> GatewayFilterChain : forwards mutated request
```

## 참고 코드/문서

- `services/api-gateway-service`
- `services/proxy-service`
- `services/identity-service`
- `services/usage-service`
- `docker-compose.yml`
- `docs/contracts/gateway-proxy.md`
