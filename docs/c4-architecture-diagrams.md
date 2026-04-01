# AI Usage & Billing Platform - C4 Architecture Diagrams (Code As-Is)

이 문서는 목표 설계가 아니라, 현재 저장소에 존재하는 구현 코드 기준으로
시스템 아키텍처를 C4 모델(C1 → C4)로 정리한다.

분석 대상:
- `apps/web` (Next.js UI + BFF Route Handlers, 팀원 C · Frontend)
- `services/api-gateway-service`
- `services/proxy-service`
- `services/identity-service`
- `services/usage-service`
- `libs/usage-events`
- 서비스별 `application.yml`/`application.properties`

## C1 - System Context

화살표 라벨은 짧게 두었고, 상세는 노드 설명·본문을 보면 된다.

```mermaid
%%{init: {'flowchart': {'htmlLabels': true, 'nodeSpacing': 50, 'rankSpacing': 70, 'padding': 12}}}%%
flowchart TB
    browser["Browser"]
    client["Developer / Client App"]

    subgraph platform["AI Usage Platform (As-Is)"]
      direction TB
      web["Next.js Web (apps/web)"]
      gateway["API Gateway Service"]
      proxy["Proxy Service"]
      identity["Identity Service"]
      usage["Usage Service"]
    end

    subgraph ext["External systems"]
      direction LR
      openai["OpenAI"]
      anthropic["Anthropic"]
      google["Gemini"]
      keysvc["API Key Svc"]
    end

    subgraph data["Data & messaging"]
      direction LR
      rabbit["RabbitMQ"]
      appDb["PostgreSQL (app)"]
      usageDb["PostgreSQL (usage_db)"]
    end

    browser -->|HTTPS| web
    web -->|BFF| identity
    client -->|Auth| identity
    client -->|AI API| gateway
    gateway -->|/proxy| proxy

    proxy -->|relay| openai
    proxy -->|relay| anthropic
    proxy -->|relay| google
    proxy -->|key| keysvc
    proxy -->|publish| rabbit

    usage -->|consume| rabbit
    identity -->|JPA| appDb
    usage -->|JPA| usageDb
```

## C2 - Container Diagram

```mermaid
C4Container
title C2: Containers (Current Implementation)

Person(client, "Developer/User", "Auth and AI API consumer")
Person(browserUser, "Browser user", "Web UI and same-origin BFF")

System_Boundary(platform, "AI Usage Platform") {
    Container(webapp, "Web App (apps/web)", "Next.js", "UI + auth BFF, httpOnly cookie")
    Container(gateway, "API Gateway", "Spring Cloud Gateway", "JWT, /api/v1/ai → /proxy")
    Container(proxy, "Proxy Service", "Spring WebFlux", "Relay, usage parse, MQ publish")
    Container(identity, "Identity Service", "Spring + JPA", "Signup/login, JWT")
    Container(usage, "Usage Service", "Spring + MQ + JPA", "Consume usage events")

    ContainerQueue(rabbit, "RabbitMQ", "AMQP", "usage.events / usage.recorded")
    ContainerDb(appDb, "PostgreSQL (app)", "RDB", "Identity domain data")
    ContainerDb(usageDb, "PostgreSQL (usage_db)", "RDB", "Usage logs")
}

System_Ext(openai, "OpenAI API", "LLM provider")
System_Ext(anthropic, "Anthropic API", "LLM provider")
System_Ext(google, "Google Gemini API", "LLM provider")
System_Ext(keysvc, "API Key Service", "provider key source")

Rel(browserUser, webapp, "HTTPS UI + /api/auth/*", "HTTPS")
Rel(webapp, identity, "signup/login proxy", "HTTPS")
Rel(client, identity, "auth API direct", "HTTPS")
Rel(client, gateway, "AI request", "HTTPS")
Rel(gateway, proxy, "forward + trust headers", "HTTP")

Rel(proxy, keysvc, "provider API key", "HTTP")
Rel(proxy, openai, "relay", "HTTPS")
Rel(proxy, anthropic, "relay", "HTTPS")
Rel(proxy, google, "relay", "HTTPS")
Rel(proxy, rabbit, "publish events", "AMQP")

Rel(usage, rabbit, "consume events", "AMQP")
Rel(identity, appDb, "read/write", "JPA")
Rel(usage, usageDb, "read/write", "JPA")
```

## C3 - Component Diagram (Cross-Service Runtime Flow)

```mermaid
%%{init: {'flowchart': {'htmlLabels': true, 'nodeSpacing': 28, 'rankSpacing': 40, 'padding': 10}}}%%
flowchart TB
  subgraph GW["API Gateway"]
    direction TB
    gwSec["Security + JwtDecoder config"]
    gwFilter["ProxyTrustHeadersGatewayFilter"]
    gwRoute["Routes (application.yml)"]
    gwSec --> gwFilter --> gwRoute
  end

  subgraph PX["Proxy Service"]
    direction TB
    pxCtl["ProxyController"]
    pxRelay["ProxyRelayService"]
    pxReg["ProviderRegistry / Handlers"]
    pxRow["ApiKeyClient · UserContextResolver · UsageEventPublisher"]
    pxCtl --> pxRelay --> pxReg --> pxRow
  end

  RABBIT["RabbitMQ"]

  subgraph US["Usage Service"]
    direction TB
    usListener["UsageRecordedEventListener"]
    usSvc["UsageRecordedService"]
    usRepo["UsageRecordedLogRepository"]
    usEntity["UsageRecordedLogEntity"]
    usListener --> usSvc --> usRepo --> usEntity
  end

  subgraph ID["Identity Service"]
    direction TB
    idCtl["AuthController"]
    idSvc["UserService"]
    idRepo["User / Role repository"]
    idJwt["JwtTokenProvider · JwtAuthFilter"]
    idCtl --> idSvc --> idRepo
    idSvc --> idJwt
  end

  GW -->|AI path| PX
  PX -->|publish| RABBIT
  RABBIT -->|deliver| US
```

## C4 - Code Diagram (Proxy Relay Core)

```mermaid
classDiagram
direction TB

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

ProxyController --> ProxyRelayService : relay
ProxyRelayService --> ProviderRegistry : select
ProxyRelayService --> ProviderHandler : per provider
ProxyRelayService --> ApiKeyClient : key
ProxyRelayService --> UserContextResolver : user context
ProxyRelayService --> UsageEventPublisher : publish
UsageEventPublisher --> UsageRecordedEvent : payload
UsageRecordedEvent --> TokenUsage : stats
ProviderHandler --> AiProvider : strategy
```

## C4 - Code Diagram (Usage Persistence Core)

```mermaid
classDiagram
direction TB

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

UsageRecordedEventListener --> UsageRecordedService : on message
UsageRecordedService --> UsageRecordedLogRepository : persist
UsageRecordedService --> UsageRecordedLogEntity : map
```

## C4 - Code Diagram (Gateway Trust Header Flow)

```mermaid
classDiagram
direction TB

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

SecurityConfiguration --> JwtDecoderConfiguration : decoder
SecurityConfiguration --> ProxyTrustHeadersGatewayFilter : chain
ProxyTrustHeadersGatewayFilter --> JwtAuthenticationToken : JWT claims
ProxyTrustHeadersGatewayFilter --> GatewayProperties : dev / secret
ProxyTrustHeadersGatewayFilter --> GatewayFilterChain : forward
```

## C4 - Code Diagram (Identity Auth Core)

```mermaid
classDiagram
direction TB

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

AuthController --> UserService : signup / login
UserService --> UserRepository : users
UserService --> RoleRepository : roles
UserService --> PasswordEncoder : hash
UserService --> JwtTokenProvider : token
SecurityConfig --> JwtAuthenticationFilter : filter chain
JwtAuthenticationFilter --> JwtTokenProvider : validate
UserRepository --> User
RoleRepository --> Role
```

## Web Application (`apps/web`) — 구조·흐름 (팀원 C · Frontend)

**목적:** 브라우저 대상 UI와 인증 BFF를 **현재 저장소 트리 기준(As-Is)** 으로 시각화한다. **팀원 C(프론트)** 가 라우트·컴포넌트·BFF를 바꿀 때 **이 절의 다이어그램과 불릿 목록을 함께 갱신**한다. (집계·알림 **백엔드**는 `docs/architecture.md` §12.)

**동기화 체크리스트 (PR 또는 주기적으로):**

1. `apps/web/src/app` 아래 **새 `page.tsx` / `route.ts` / 동적 세그먼트**가 생기면 디렉터리 맵·흐름도에 반영한다.
2. `middleware.ts`의 **`config.matcher`** 가 바뀌면 미들웨어 다이어그램과 설명을 맞춘다.
3. BFF가 Identity·게이트웨이를 호출하는 방식이 바뀌면 시퀀스 다이어그램을 수정한다(계약은 `docs/contracts/web-identity-bff.md`, Usage 프록시는 `docs/contracts/web-gateway-bff.md`).
4. **구현과 계약 문서가 어긋나면 다이어그램은 코드 우선**으로 두고, 계약 문서 정리는 별도 작업으로 남긴다.

### W1 — 디렉터리·파일 맵 (논리 트리)

> 파일 단위 나열. `*.test.ts` 는 같은 폴더에 두는 패턴을 유지한다.

```mermaid
%%{init: {'flowchart': {'nodeSpacing': 22, 'rankSpacing': 36, 'padding': 14}}}%%
flowchart TB
  subgraph AW["apps/web"]
    direction TB
    MW["middleware.ts<br/>쿠키 검사 · matcher"]
    NC["next.config.ts"]
    VC["vitest.config.ts"]
    subgraph APP["src/app"]
      direction TB
      ROOT["layout · page · globals · favicon"]
      LOGIN["login/page.tsx"]
      SIGNUP["signup/page.tsx"]
      subgraph PROT["보호 catch-all 페이지"]
        DASH["dashboard/[[...path]]"]
        ORG["organizations/[[...path]]"]
        TEAM["teams/[[...path]]"]
        SET["settings/[[...path]]"]
      end
      subgraph API["api Route Handlers"]
        RAL["auth/login/route.ts + route.test.ts"]
        RAS["auth/signup/route.ts + route.test.ts"]
        RASE["auth/session/route.ts + route.test.ts"]
        RAEK["auth/external-keys/route.ts + route.test.ts"]
        RU["usage/[[...path]] + test"]
        RI["identity/[[...path]] + test"]
      end
    end
    subgraph COMP["src/components"]
      direction TB
      LF["login/login-form.tsx"]
      SF["signup/signup-form.tsx"]
      UD["usage/usage-dashboard"]
      ACCT["account/ organizations-view · account-settings-view.tsx 등"]
      UI["ui/ shadcn"]
    end
    subgraph LIB["src/lib"]
      direction TB
      CF["api/client-fetch (+ test)"]
      FU["usage/fetch-usage"]
      IDT["api/identity/types"]
      IDS["login.schema (+ test)"]
      IDSU["signup.schema (+ test)"]
      UT["utils.ts"]
    end
    TS["src/test/setup.ts"]
  end
```

### W2 — 런타임 흐름 (브라우저 ↔ BFF ↔ Identity·게이트웨이)

> **현행 구현 기준:** `POST` 로그인·회원가입은 Identity로 프록시 후 httpOnly `access_token` 쿠키 설정. **`GET /api/auth/session`** 은 BFF가 **Identity `GET /api/auth/session`** 을 Bearer로 호출해 본문을 검증·전달한다. 대시보드 사용량은 브라우저가 **`GET /api/usage/...`** 로 호출하면 Usage BFF가 **`{API_GATEWAY_URL}/api/v1/usage/...`** 로 프록시한다(`GATEWAY_DEV_MODE` 시 Identity 세션으로 `X-User-Id` 보강). 계약: `docs/contracts/web-identity-bff.md`, `docs/contracts/web-gateway-bff.md`, `docs/contracts/gateway-proxy.md`.

```mermaid
%%{init: {'sequence': {'actorMargin': 36, 'messageMargin': 18}}}%%
sequenceDiagram
  participant B as Browser
  participant P as Pages
  participant H as Auth BFF
  participant U as Usage BFF
  participant GW as API Gateway
  participant I as Identity

  B->>P: GET login signup dashboard
  P->>H: POST login or signup
  H->>I: POST login or signup
  I-->>H: tokens
  H-->>B: Set-Cookie access_token

  B->>H: GET /api/auth/session
  H->>I: GET session + Bearer
  I-->>H: ApiResponse
  H-->>B: 200 or 401

  B->>H: POST /api/auth/external-keys
  Note over B,H: settings 화면에서 개인 키 등록(상태 변경)
  H->>I: POST external-keys + Bearer
  I-->>H: 201/400/401/409 ApiResponse
  H-->>B: ApiResponse (no-store)

  B->>U: GET /api/usage/dashboard/...
  Note over U: Bearer·dev 시 X-User-Id
  U->>GW: /api/v1/usage/...
  GW-->>U: upstream
  U-->>B: usage JSON

  Note over B: middleware dashboard settings orgs teams
```

### W3 — 레이어 관계 (UI · 공용 클라이언트 · BFF · 도메인 라이브러리)

```mermaid
%%{init: {'flowchart': {'nodeSpacing': 30, 'rankSpacing': 40, 'padding': 12}}}%%
flowchart TB
  subgraph FE["Client UI"]
    direction LR
    LF["login-form"]
    SF["signup-form"]
  end
  subgraph PG["Pages"]
    direction LR
    LP["login/page"]
    SP["signup/page"]
  end
  subgraph RH["Route Handlers"]
    direction TB
    RL["POST login"]
    RS["POST signup"]
    RQ["GET session"]
    RE["POST external-keys"]
  end
  subgraph LB["lib"]
    CF["client-fetch"]
    ZL["Zod + types"]
  end
  IDN["Identity<br/>IDENTITY_SERVICE_URL"]

  LP --> LF
  SP --> SF
  LF --> CF
  SF --> CF
  CF --> RL
  CF --> RS
  CF --> RE
  RL --> ZL
  RS --> ZL
  RQ --> ZL
  RE --> ZL
  RL --> IDN
  RS --> IDN
  RE --> IDN
```

### W4 — 미들웨어와 보호 경로

```mermaid
%%{init: {'flowchart': {'nodeSpacing': 28, 'rankSpacing': 44, 'padding': 12}}}%%
flowchart TD
  REQ["Incoming request"]
  MW["middleware.ts"]
  COOK{"access_token<br/>있음?"}
  NEXT["next()"]
  REDIR["redirect /login?next=…"]

  REQ --> MW
  MW --> COOK
  COOK -->|Y| NEXT
  COOK -->|N| REDIR

  subgraph MT["matcher (현행)"]
    direction TB
    M1["/dashboard/:path*"]
    M2["/settings/:path*"]
    M3["/organizations/:path*"]
    M4["/teams/:path*"]
  end

  MW -.-> MT
```

`matcher` 가 가리키는 경로에 대응하는 **`app/dashboard/...` 등 페이지**를 추가·이동하면, W1 트리와 `docs/repository-structure.md` 도 함께 업데이트한다.

## 참고 코드/문서

- `apps/web/middleware.ts`
- `apps/web/src/app/api/auth/login/route.ts`
- `apps/web/src/app/api/auth/login/route.test.ts`
- `apps/web/src/app/api/auth/signup/route.ts`
- `apps/web/src/app/api/auth/signup/route.test.ts`
- `apps/web/src/app/api/auth/session/route.ts`
- `apps/web/src/app/api/auth/session/route.test.ts`
- `apps/web/src/app/api/auth/external-keys/route.ts`
- `apps/web/src/app/api/auth/external-keys/route.test.ts`
- `apps/web/src/app/api/usage/[[...path]]/route.ts`
- `apps/web/src/app/api/identity/[[...path]]/route.ts`
- `docs/contracts/web-identity-bff.md`
- `docs/contracts/web-gateway-bff.md`
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
