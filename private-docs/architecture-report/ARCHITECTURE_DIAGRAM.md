# Project Architecture Diagram (Mermaid)

> 이 다이어그램은 현재 저장소에 존재하는 서비스(`services/*`)와, 문서에 의해 “개념상/향후”로 분리되는 역할을 함께 고려해서 작성했습니다.
> 
> - 현재 저장소 실제 서비스 폴더: `api-gateway-service`, `proxy-service`, `identity-service`, `usage-service`
> - 문서에서 언급되는 `Billing/Analytics/Quota/Notification/Frontend`는 “개념 노드(향후)”로 표기합니다. (현재 폴더가 저장소에 없으므로 구현 세부는 생략)

## 1) High-Level Request / Event Flow

```mermaid
flowchart LR
  %% Client / Frontend (concept)
  User[사용자] --> FE[Frontend (개념: Next.js/App Router)]
  FE -->|HTTP| GW[api-gateway-service<br/>Spring Cloud Gateway (WebFlux)<br/>Routes: /api/v1/ai/** -> /proxy/**<br/>Auth: oauth2 resource-server]

  %% AI Proxy
  GW -->|HTTP| PROXY[proxy-service<br/>Spring WebFlux + AMQP<br/>AI Provider 중계(WebClient)<br/>UsageRecordedEvent 발행]
  PROXY -->|WebClient| PROVIDER[AI Provider<br/>OpenAI / Google(Gemini) / Anthropic]

  %% API Key resolution (expected Key Service API)
  PROXY -->|WebClient GET<br/>/internal/api-keys/{provider}?userId=...| KEYAPI[Key Service API (개념/기대)<br/>Identity/Key 도메인에서 제공 예정]

  %% Event broker
  PROXY -->|AMQP convertAndSend| RABBIT[RabbitMQ<br/>Exchange: usage.events<br/>RoutingKey: usage.recorded]

  %% Usage persistence
  RABBIT -->|consume usage.recorded| USAGE[usage-service (호스트 실행)<br/>Spring AMQP @RabbitListener<br/>JPA로 저장]
  USAGE -->|JPA save| PG_USAGE[(PostgreSQL: usage_db<br/>Table: usage_recorded_log)]

  %% Identity persistence (only for auth/signup in this repo)
  User -->|/signup| IDENTITY[identity-service (호스트 실행)<br/>Spring MVC(내장 Tomcat) + Spring Security + JPA<br/>Open API: /api/auth/signup]
  IDENTITY -->|JPA save| PG_ID[(PostgreSQL: app<br/>Table: users)]

  %% Downstream event-driven roles (concept)
  RABBIT -->|consume usage.recorded| ANALYTICS[Analytics/Reporting (개념)<br/>집계/리포트 생성]
  RABBIT -->|consume usage.recorded| BILLING[Billing Service (개념)<br/>비용 계산/정산 저장]
  RABBIT -->|consume usage.recorded + quota logic (개념)| QUOTA[Quota Service (개념)<br/>soft/hard 제한 판단]
  RABBIT -->|quota-warning/exceeded (개념)| NOTIF[Notification (개념)<br/>Slack/Email 등 발송]
```

## 2) Service-by-Service: Backend / DB / 메시지 구조

```mermaid
flowchart TB
  subgraph COMPOSE[Docker Compose (인프라)]
    PG[PostgreSQL 컨테이너<br/>DB: app, usage_db]
    MQ[RabbitMQ 컨테이너<br/>Exchange/Queue 기반 이벤트 연계]
    REDIS[Redis 컨테이너<br/>(현재 repo에선 직접 사용 확인은 제한적: Caffeine 캐시 존재)]
  end

  subgraph GATEWAY[api-gateway-service]
    GW_BE[Backend: Spring Cloud Gateway (WebFlux)]
    GW_DB[(DB: 없음 in current repo)]
  end

  subgraph PROXY_SVC[proxy-service]
    PROXY_BE[Backend: Spring WebFlux<br/>Provider 중계 + streaming 응답 처리]
    PROXY_MQ[RabbitMQ publish: usage.events / usage.recorded]
    PROXY_KEY[Key Service 호출(개념): /internal/api-keys/{provider}]
    PROXY_DB[(DB: 없음 in current repo)]
  end

  subgraph ID_SVC[identity-service]
    ID_BE[Backend: Spring MVC (내장 Tomcat)<br/>Spring Security (signup은 permitAll)]
    ID_REPO[JPA Repository: UserRepository]
    ID_DB[(PostgreSQL: app<br/>Table: users)]
  end

  subgraph USAGE_SVC[usage-service]
    USAGE_BE[Backend: Spring Boot Web (내장 Tomcat)<br/>RabbitListener 소비 + JPA 저장]
    USAGE_AMQP[Spring AMQP (@RabbitListener)]
    USAGE_DB[(PostgreSQL: usage_db<br/>Table: usage_recorded_log)]
  end

  FE[Frontend (개념)] --> GW_BE
  GW_BE --> PROXY_BE

  %% Message broker linkage
  PROXY_BE --> PROXY_MQ --> MQ
  USAGE_BE --> USAGE_AMQP --> MQ

  %% DB linkage
  ID_BE --> ID_DB --> PG
  USAGE_BE --> USAGE_DB --> PG

  %% Notes for future roles
  MQ --> ANALYTICS_FUTURE[Analytics/Billing/Quota/Notification (개념 노드)]
```

## 3) 다이어그램 근거(코드/설정 기준)

- `api-gateway-service`
  - `services/api-gateway-service/build.gradle` : `spring-cloud-starter-gateway-server-webflux`, `spring-boot-starter-oauth2-resource-server`
  - `services/api-gateway-service/src/main/resources/application.yml` : `server.webflux.routes` / `RewritePath`
- `proxy-service`
  - `services/proxy-service/build.gradle` : `spring-boot-starter-webflux`, `spring-boot-starter-amqp`, `spring-boot-starter-cache`
  - `services/proxy-service/src/main/resources/application.yml` : `proxy.rabbit.usage-exchange/routing-key`, `proxy.key-service.base-url`
  - `services/proxy-service/src/main/java/com/eevee/proxyservice/mq/UsageEventPublisher.java` : `RabbitTemplate.convertAndSend(...)`
  - `services/proxy-service/src/main/java/com/eevee/proxyservice/relay/ProxyRelayService.java` : Provider 호출/응답 파싱 후 이벤트 발행
- `identity-service`
  - `services/identity-service/build.gradle` : `spring-boot-starter-web`, `spring-boot-starter-data-jpa`
  - `services/identity-service/src/main/resources/application.properties` : `spring.datasource.url/jdbc:postgresql://...`
  - `services/identity-service/src/main/java/.../controller/AuthController.java` : `POST /api/auth/signup`
  - `services/identity-service/src/main/java/.../repository/UserRepository.java` : `JpaRepository`
  - `services/identity-service/src/main/java/.../entity/User.java` : `@Table(name = "users")`
- `usage-service`
  - `services/usage-service/build.gradle` : `spring-boot-starter-web`, `spring-boot-starter-amqp`, `spring-boot-starter-data-jpa`
  - `services/usage-service/src/main/resources/application.yml` : `USAGE_POSTGRES_*` + `rabbitmq` host/port
  - `services/usage-service/src/main/java/.../consumer/UsageRecordedEventListener.java` : `@RabbitListener(queues=...)`로 JSON 소비
  - `services/usage-service/src/main/java/.../domain/UsageRecordedLogEntity.java` : `@Table(name = "usage_recorded_log")`

