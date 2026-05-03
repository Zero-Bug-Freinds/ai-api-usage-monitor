# Billing 서비스 발행(outbound) AMQP 이벤트

다른 마이크로서비스(usage, notification, analytics 등)를 구현하는 팀원이 **billing-service가 RabbitMQ로 발행하는 메시지**를 구독할 때 참고하는 계약 문서입니다.

- **정본 구현**: `services/billing-service` (발행 시점·헤더·설정)
- **공유 Java 타입**: `UsageCostFinalizedEvent`만 `libs/usage-events`에 존재합니다. 나머지는 billing 모듈 내 record이며, **다른 JVM 서비스는 JSON 스키마 + `schemaVersion`으로 맞추거나** 필요 시 동일 필드의 로컬 DTO를 두면 됩니다.
- **설정 키**: `services/billing-service/src/main/resources/application.yml` 의 `billing.rabbit.*` 및 동일 키의 환경변수 오버라이드(아래 표 참고).

## 공통 전제

| 항목 | 값 |
|------|-----|
| 브로커 | 환경의 RabbitMQ (`spring.rabbitmq.*`) |
| Exchange 종류 | **Topic** (durable) |
| 기본 exchange 이름 | 대부분 **`billing.events`** (환경별로 `BILLING_RABBIT_*_EXCHANGE`로 변경 가능) |
| 본문 인코딩 | UTF-8 JSON 문자열 |
| `Content-Type` | `application/json` |
| 버전 | 각 페이로드의 **`schemaVersion`** 필드로 판별. 미지원 버전은 소비 측에서 거부·DLQ 정책을 정합니다. |
| 알 수 없는 JSON 필드 | billing 발행 측 Jackson 설정은 역직렬화 시 unknown 무시 패턴과 맞추는 것이 좋습니다. |

**중요 (usage.cost.finalized)**: 비용 확정 스트림은 **`usage.events` / `usage.recorded`와 다른 exchange·routing key**를 씁니다. 동일 큐를 `usage.recorded`에 바인딩해 두었다가 비용 이벤트까지 받으려 하면 **루프·오역직렬화** 위험이 있으므로, **전용 큐**를 `billing.events` + 아래 routing key로 바인딩하세요. 상수 정의는 `libs/usage-events`의 `com.eevee.usage.events.UsageCostEventAmqp`를 참고하면 됩니다.

## 발행 이벤트 요약

| 이벤트 (논리명) | Exchange (기본) | Routing key (기본) | 발행 비활성화 |
|-----------------|-----------------|-------------------|---------------|
| Usage 비용 확정 | `billing.events` | `usage.cost.finalized` | `BILLING_RABBIT_COST_OUT_ENABLED=false` |
| 월 예산 임계 도달 | `billing.events` | `billing.budget.threshold.reached` | `BILLING_RABBIT_BUDGET_OUT_ENABLED=false` |
| 비용 정정 반영 완료 | `billing.events` | `billing.cost.corrected` | `BILLING_RABBIT_CORRECTION_OUT_ENABLED=false` |

환경별 exchange/routing key는 다음과 같이 덮어쓸 수 있습니다.

- 비용 확정: `BILLING_RABBIT_COST_OUT_EXCHANGE`, `BILLING_RABBIT_COST_OUT_ROUTING_KEY`
- 예산 임계: `BILLING_RABBIT_BUDGET_OUT_EXCHANGE`, `BILLING_RABBIT_BUDGET_OUT_ROUTING_KEY`
- 정정 완료: `BILLING_RABBIT_CORRECTION_OUT_EXCHANGE`, `BILLING_RABBIT_CORRECTION_OUT_ROUTING_KEY`

---

## 1) `UsageCostFinalizedEvent` (usage → billing 처리 후)

**의미**: `UsageRecordedEvent`와 동일한 **`eventId`**에 대해 billing이 가격표를 적용해 산출한 **추정 비용(USD)** 을 알립니다. usage DB 등을 billing이 직접 읽지 않고도 downstream이 `estimated_cost` 등을 맞출 수 있게 합니다.

| 구분 | 내용 |
|------|------|
| Java 타입 | `libs/usage-events`: `com.eevee.usage.events.UsageCostFinalizedEvent` |
| `schemaVersion` (현재) | `1` (`UsageCostFinalizedEvent.CURRENT_SCHEMA_VERSION`) |
| 발행 시점 | billing이 사용 이벤트를 처리·집계한 트랜잭션 **커밋 이후** (`UsageCostFinalizedEventPublisher`) |

### JSON 필드 (camelCase)

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `schemaVersion` | number | 예 | `1` |
| `eventId` | string (UUID) | 예 | 원본 `UsageRecordedEvent.eventId` |
| `estimatedCostUsd` | number | 예 | USD |
| `finalizedAt` | string (ISO-8601 instant) | 예 | 발행 시각에 가까운 값 |
| `pricingRuleVersion` | string | 아니오 | 카탈로그/정책 버전 힌트(없을 수 있음) |
| `provider` | string enum | 아니오 | `OPENAI`, `ANTHROPIC`, `GOOGLE` (`AiProvider` 이름) |
| `model` | string | 아니오 | 집계에 사용된 모델 키 |

### AMQP 메시지 헤더 (프로퍼티)

billing이 함께 넣는 헤더입니다. **본문만 파싱하지 말고** 라우팅·필터에 활용할 수 있습니다.

| 헤더 | 예시 | 설명 |
|------|------|------|
| `subjectType` | `API_KEY` / `TEAM` / `USER` | `apiKeyId` → `teamId` → 그 외 `USER` 순으로 결정 |
| `userId` | string | 있으면 설정 |
| `teamId` | string | 있으면 설정 |
| `apiKeyId` | string | 있으면 설정 |

### 소비자 구현 체크리스트

- **`eventId` 멱등**: 동일 `eventId` 재전송에 안전하게 처리합니다.
- **`schemaVersion`**: `1`만 지원한다면 그 외는 로그·DLQ.
- **전용 큐**: `UsageCostEventAmqp.TOPIC_EXCHANGE_NAME` + `ROUTING_KEY_COST_FINALIZED` 로 바인딩 (기본값은 위 표와 동일).

---

## 2) `BillingBudgetThresholdReachedEvent` (월 예산 임계 구간 통과)

**의미**: 특정 달(`monthStart`)에 대해 **월 누적 지출**이 **월 예산** 대비 **10%, 20%, …, 100%** 구간을 **각각 처음 넘는 순간**마다 알림용으로 발행됩니다.

| 구분 | 내용 |
|------|------|
| Java 타입 | `services/billing-service`: `com.eevee.billingservice.events.BillingBudgetThresholdReachedEvent` (다른 서비스는 JSON 계약만 맞춰도 됨) |
| `schemaVersion` (현재) | `1` |
| 발행 시점 | 사용 이벤트 처리 트랜잭션 **커밋 이후** (`BudgetThresholdEventPublisher`) |

### JSON 필드 (camelCase)

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `schemaVersion` | number | 예 | `1` |
| `occurredAt` | string (ISO-8601 instant) | 예 | 발행 시각 |
| `monthStart` | string (ISO date) | 예 | 해당 월의 1일 |
| `thresholdPct` | number | 예 | 예: `0.1` … `1` (예산 대비 비율 임계값, 10% 단위) |
| `monthlyTotalUsd` | number | 예 | 임계 통과 후 월 합계 USD |
| `monthlyBudgetUsd` | number | 예 | Identity 등에서 조회한 월 예산 USD |

### AMQP 메시지 헤더

| 헤더 | 설명 |
|------|------|
| `subjectType` | `API_KEY` / `TEAM` / `USER` |
| `userId`, `teamId`, `apiKeyId` | 값이 있으면 설정 |

### 소비자 구현 체크리스트

- **중복·재시도**: 동일 임계를 여러 번 넘지 않는 한 발행 빈도는 제한적이나, **알림 멱등 키**(예: `userId` + `monthStart` + `thresholdPct`)를 두는 것을 권장합니다.
- **예산 출처**: 금액은 billing이 조회한 예산 스냅샷 기준이며, 상세 출처는 `GET /api/v1/expenditure/monthly-budget-authority`(billing) 등과 별개입니다.

---

## 3) `BillingCostCorrectedEvent` (비용 정정 반영 후)

**의미**: 인바운드 정정 명령(`billing.cost.correct` 등)이 **집계에 성공적으로 반영**된 뒤, 트랜잭션 **커밋 이후** 발행됩니다. **확정 월(`is_finalized`) 정책으로 스킵된 경우**·**음수 가드로 스킵된 경우** 등에는 발행되지 않습니다.

| 구분 | 내용 |
|------|------|
| Java 타입 | billing-service 내부: `com.eevee.billingservice.events.BillingCostCorrectedEvent` (`libs` 없음) |
| `schemaVersion` (현재) | `1` |
| 상세·인바운드 스키마 | `services/billing-service/README.md` 의 “비용 정정 (AMQP)” 절 |

### JSON 필드 (camelCase)

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `schemaVersion` | number | 예 | `1` |
| `occurredAt` | string (ISO-8601 instant) | 예 | 발행 시각 |
| `correctionEventId` | string (UUID) | 예 | 정정 명령과 동일 ID |
| `userId` | string | 예 | |
| `apiKeyId` | string | 예 | |
| `monthStartDate` | string (ISO date) | 예 | 월 집계 키 (해당 월 1일) |
| `appliedDeltaCostUsd` | number | 예 | 실제 반영된 USD 델타 |
| `aggDate` | string (ISO date) | 아니오 | 일 집계가 없었으면 null |
| `provider` | string enum | 아니오 | `OPENAI` / `ANTHROPIC` / `GOOGLE` |
| `model` | string | 아니오 | |
| `optionalCorrectedTotalUsdForScope` | number | 아니오 | 감사·표시용 |
| `relatedUsageEventId` | string (UUID) | 아니오 | 원 usage 이벤트 연결 |

### AMQP 메시지 헤더

| 헤더 | 설명 |
|------|------|
| `userId` | 본문과 동일 |
| `apiKeyId` | 본문과 동일 |

### 소비자 구현 체크리스트

- **`correctionEventId` 멱등** 권장.
- **미발행 케이스**: 정정이 거절되면 이벤트가 없을 수 있으므로, “반드시 한 번은 온다” 가정을 두지 않습니다.

---

## 로컬·스테이징에서 빠르게 확인하는 방법

1. RabbitMQ Management 또는 `rabbitmqadmin`으로 **`billing.events`** topic exchange 존재 여부 확인.
2. 소비 서비스용 **durable 큐**를 만들고, 구독할 routing key만 **exact**로 바인딩 (topic 패턴 남용 지양).
3. `billing.rabbit.*-out.enabled=false` 인 환경에서는 해당 스트림이 조용히 꺼져 있을 수 있음.

## 관련 코드 위치 (참고용)

| 역할 | 경로 |
|------|------|
| 발행 설정 | `services/billing-service/.../config/BillingRabbitProperties.java` |
| Exchange/Queue 바인딩 | `services/billing-service/.../config/BillingRabbitConfiguration.java` |
| 비용 확정 발행 | `.../service/UsageCostFinalizedEventPublisher.java` |
| 예산 임계 발행 | `.../service/BudgetThresholdEventPublisher.java` |
| 정정 완료 발행 | `.../service/BillingCostCorrectedEventPublisher.java` |
| 공유 cost 이벤트 타입 | `libs/usage-events/.../UsageCostFinalizedEvent.java`, `UsageCostEventAmqp.java` |

문서와 구현이 어긋나면 **billing-service 구현과 `application.yml`을 우선**하고, 이 문서는 PR로 갱신합니다.
