# `UsageRecordedEvent` 소비 흐름 (proxy-service 발행 → 다중 서비스)

`proxy-service`가 RabbitMQ로 **`UsageRecordedEvent`** 를 **한 번** 발행할 때, 각 도메인 서비스가 **어떻게 소비하는지**를 다이어그램으로 정리한다.  
이벤트 페이로드·필드는 `libs/usage-events`의 `UsageRecordedEvent` 및 [`docs/sequence-diagrams.md`](sequence-diagrams.md)를 따른다.

---

## 1. 전체 구조: Topic Exchange + 서비스별 큐 (팬아웃)

동일한 라우팅 키로 발행된 메시지를 **여러 큐**에 바인딩하면, 각 소비자는 **자기 큐**에서 **독립적으로** 메시지를 받는다. 서비스 간 DB는 공유하지 않는다.

```mermaid
flowchart LR
    subgraph Publisher
        P[proxy-service]
    end

    subgraph RabbitMQ
        X((usage exchange<br/>Topic))
        Q1[[usage-service.queue]]
        Q2[[analytics-service.queue]]
        Q3[[billing-service.queue]]
        Q4[[alarm-service.queue]]
    end

    P -->|publish UsageRecordedEvent| X
    X --> Q1
    X --> Q2
    X --> Q3
    X --> Q4

    Q1 --> US[usage-service]
    Q2 --> AN[analytics-service]
    Q3 --> BI[billing-service]
    Q4 --> AL[alarm-service]

    US --- D1[(usage DB)]
    AN --- D2[(analytics DB)]
    BI --- D3[(billing DB)]
    AL --- D4[(alarm / rules DB)]
```

**요약**

| 소비자 | 큐(예시 이름) | 소비 후 하는 일(도메인) |
|--------|----------------|-------------------------|
| **usage-service** | `usage-service.queue` | 사용량 **원장** INSERT, 일·월 집계 갱신 |
| **analytics-service** | `analytics-service.queue` | 대시보드용 **OLAP/집계** 적재·스냅샷 갱신 |
| **billing-service** | `billing-service.queue` | 플랜·단가 기준 **과금 단위** 반영, 청구 입력 데이터 |
| **alarm-service** | `alarm-service.queue` | 임계치·스파이크 **규칙 평가**, 알림 트리거 |

각 서비스는 **멱등 처리**(예: `eventId` 기준)로 중복 전달을 안전하게 처리한다.

**발행·바인딩(본 저장소와 정합):** Topic Exchange 이름 **`usage.events`**, 라우팅 키 **`usage.recorded`** (`proxy-service`의 `proxy.rabbit.*`와 동일). `usage-service`는 큐 **`usage-service.queue`** 를 선언하고 위 Exchange에 해당 키로 바인딩한다(`services/usage-service`).

---

## 2. 시퀀스: 발행 1회 → 소비자 N (병렬)

```mermaid
sequenceDiagram
    autonumber
    participant Proxy as proxy-service
    participant MQ as RabbitMQ<br/>(Topic Exchange)
    participant US as usage-service
    participant AN as analytics-service
    participant BI as billing-service
    participant AL as alarm-service

    Proxy->>MQ: publish UsageRecordedEvent (JSON)
    Note over Proxy,MQ: eventId, userId, organizationId, teamId,<br/>provider, model, tokenUsage, estimatedCost 등

    par 팬아웃 — 각 큐로 복제
        MQ-->>US: @RabbitListener consume
        MQ-->>AN: @RabbitListener consume
        MQ-->>BI: @RabbitListener consume
        MQ-->>AL: @RabbitListener consume
    end

    US->>US: INSERT usage_log / rollup
    AN->>AN: 집계 테이블·시계열 갱신
    BI->>BI: 과금 단위·크레딧 차감 후보 반영
    AL->>AL: 규칙 엔진 → 알림 필요 시 발송 파이프라인
```

---

## 3. 서비스별 소비 관점 정리

```mermaid
flowchart TB
    E[UsageRecordedEvent]

    E --> US[usage-service]
    E --> AN[analytics-service]
    E --> BI[billing-service]
    E --> AL[alarm-service]

    US --> R1["저장: 요청 단위 원시/집계<br/>소유: 사용량 진실 원장"]
    AN --> R2["분석: 시각화·트렌드용<br/>소유: 읽기 최적화 스토어"]
    BI --> R3["과금: 단가·플랜·청구 후보<br/>소유: 인보이스·구독 컨텍스트"]
    AL --> R4["알림: 임계치·이상 탐지<br/>소유: 알림 설정·발송 상태"]
```

---

## 4. (선택) usage-service가 추가 이벤트를 발행하는 패턴

모든 소비자가 **원시 이벤트**를 구독하면 부하가 커질 수 있어, 팀 합의 하에 **usage-service**만 `UsageRecordedEvent`를 소비하고, 집계·한도 판단 후 **`UsageThresholdBreached`** 같은 **도메인 이벤트**를 다시 발행해 **alarm-service**만 구독하게 할 수 있다. 이 경우에도 **다른 서비스가 usage DB에 직접 접속하지는 않는다.**

```mermaid
flowchart LR
    Proxy[proxy-service] --> MQ1[RabbitMQ]
    MQ1 --> US[usage-service]
    US --> D[(usage DB)]
    US -->|publish 선택| MQ2[RabbitMQ]
    MQ2 --> AL[alarm-service]

    MQ1 --> AN[analytics-service]
    MQ1 --> BI[billing-service]
```

---

## 5. 대시보드·월별 비용 조회 (이벤트 외)

화면에서 **기간별 합계·차트**를 볼 때는 보통 **HTTP GET**으로 `analytics-service` 또는 `usage-service`·`billing-service`의 **조회 API**를 호출한다. 이 경로는 [`docs/sequence-diagrams.md`](sequence-diagrams.md) §3과 같다. **usage-service와 analytics-service의 역할·REST vs 이벤트 선택**은 [`docs/usage-analytics-relationship.md`](usage-analytics-relationship.md)를 참고한다.

---

## 6. 문서 유지

- Exchange 이름·라우팅 키·큐 이름은 **환경 설정·운영 합의**에 맞춘다.
- `UsageRecordedEvent` 변경 시 `libs/usage-events`, [`docs/sequence-diagrams.md`](sequence-diagrams.md), 본 문서를 함께 갱신한다.
