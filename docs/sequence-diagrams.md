# 시퀀스 다이어그램 (이벤트·조회 흐름)

본 문서는 `docs/architecture.md`의 도메인을 전제로, **사용량이 쌓이는 경로(이벤트)** 와 **대시보드 조회 경로**를 시퀀스 다이어그램으로 정리한다.  
다이어그램은 [Mermaid](https://mermaid.js.org/) 문법이며, GitHub·VS Code(Mermaid 확장) 등에서 렌더링할 수 있다.  
**여러 소비자가 같은 이벤트를 어떻게 나눠 소비하는지**(팬아웃·큐)는 [`docs/event-consumer-flow.md`](event-consumer-flow.md)를 참고한다.

---

## 1. AI 호출 시 — API Key 조회(동기) + `UsageRecordedEvent` 발행(비동기)

사용자가 **개인**이든 **조직·팀 소속**이든, 프록시는 JWT 등으로 `userId`·`organizationId`·`teamId` 컨텍스트를 확정한 뒤 Provider를 호출하고, 사용량이 확정되면 **`UsageRecordedEvent`** 를 RabbitMQ로 발행한다.

```mermaid
sequenceDiagram
    autonumber
    actor User as 사용자/클라이언트
    participant Gateway as api-gateway-service
    participant Proxy as proxy-service
    participant Identity as identity-service<br/>(API Key 조회)
    participant Provider as AI Provider<br/>(OpenAI 등)
    participant MQ as RabbitMQ
    participant Usage as usage-tracking-service<br/>(예시)
    participant Billing as billing-service<br/>(예시)
    participant Analytics as analytics-service<br/>(예시)
    participant Quota as quota-service<br/>(예시)

    User->>Gateway: HTTP AI 요청 (JWT, /api/v1/ai/... )
    Gateway->>Proxy: 라우팅·신뢰 헤더 (내부 /proxy/... )
    Proxy->>Identity: 동기 HTTP — API Key 조회 (userId, provider)
    Identity-->>Proxy: API Key (응답에만 존재, 이벤트 미포함)
    Proxy->>Provider: 업스트림 호출 (스트리밍 가능)
    Provider-->>Proxy: 응답 (토큰/usage 확정)
    Proxy->>MQ: publish UsageRecordedEvent (JSON)
    Note over Proxy,MQ: eventId, userId, organizationId, teamId,<br/>provider, model, tokenUsage, estimatedCost,<br/>requestPath, correlationId 등<br/>(API Key 평문은 포함하지 않음)

    par 소비자 (각각 독립 구독)
        MQ-->>Usage: consume → usage_log 저장
        MQ-->>Billing: consume → 비용/정산 집계
        MQ-->>Analytics: consume → 대시보드용 집계 갱신
        MQ-->>Quota: consume → 한도 초과 판단
    end
    Proxy-->>User: AI 응답 (본문)
```

---

## 2. (선택) Quota 임계치 도달 시 — 추가 이벤트

팀 설계에 따라 `quota-service`가 **별도 이벤트**를 발행할 수 있다. (이름·페이로드는 팀 합의)

```mermaid
sequenceDiagram
    autonumber
    participant MQ as RabbitMQ
    participant Quota as quota-service
    participant Notify as notification-service

    Quota->>MQ: publish quota-warning / quota-exceeded (선택)
    MQ-->>Notify: consume → Slack/Email 등
```

---

## 3. 대시보드 조회 시 — HTTP 조회만 (일반적으로 MQ 이벤트 없음)

대시보드에 보이는 수치는 **이미 ①에서 적재·집계된 데이터**이다. 사용자가 화면을 열 때는 보통 **REST 조회**만 수행한다.

```mermaid
sequenceDiagram
    autonumber
    actor User as 사용자/브라우저
    participant FE as 프론트엔드
    participant Analytics as analytics-service<br/>(또는 billing-service)
    participant DB as PostgreSQL / Redis<br/>(집계·캐시)

    User->>FE: 대시보드 페이지 접속
    FE->>Analytics: GET (기간, 스코프: 개인/팀/조직)
    Analytics->>DB: SELECT (집계·캐시 조회)
    DB-->>Analytics: 집계 결과
    Analytics-->>FE: JSON (차트/표용 DTO)
    FE-->>User: 화면 렌더링
```

---

## 4. 문서 유지

- `UsageRecordedEvent` 필드 변경 시 `libs/usage-events` 및 본 문서를 함께 갱신한다.
- 소비자 서비스가 아직 없으면 다이어그램의 participant 이름을 “예정”으로 표기하고, 구현 후 정리한다.
