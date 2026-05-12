# Usage outbound AMQP events (subscriber summary)

usage-service publishes analytics signals on RabbitMQ for downstream consumers (notably **agent-service** budget/token prediction). This document is the contract summary for teams wiring subscribers.

Inbound streams (proxy `UsageRecordedEvent`, billing `UsageCostFinalizedEvent`, identity API-key events, etc.) are documented elsewhere — billing의 **그 외 발행**(예산 임계·정정 완료 등)은 [`docs/billing-outbound-events.md`](billing-outbound-events.md). Here we only describe **usage-service → outbound** traffic.

---

## Step 1 — Agent prediction logic vs usage data (reference)

**Source:** `services/agent-service` `BudgetForecastService` + `BudgetForecastRequest` + `GeminiAssistantService`.

**End-to-end behavior today:** `BudgetForecastService` **normalizes** the incoming `BudgetForecastRequest` (for example preferring **usage prediction snapshot** daily spend when present, rolling up **7-day token totals** from usage DB when the caller omits a daily token series). It then calls **Gemini** (`GeminiAssistantService.inferForecast`) with a JSON payload built from that request. The model returns a single JSON object (predicted run-out date, `healthStatus`, Korean copy, **`anomalySummary`**, routing fields, etc.). There is **no** separate Java-side anomaly scorer (no z-score, no fixed “last day ÷ prior-day average ≥ 1.5” threshold in code).

The model prompt instructs it to treat **`hourlyTokenUsage24h`** and **`recentDailyTokenUsage7d`** as the primary basis for **anomaly** narrative, and to consider **`modelUsageDistribution7d`** for routing text; **`recentDailySpendUsd`** and budget fields inform utilization and risk wording (e.g. WARNING when the prompt describes a sudden daily spend spike). Those rules are **prompt-level**, not duplicated as deterministic formulas in `BudgetForecastService`.

| Input | Role |
|--------|------|
| `monthlyBudgetUsd`, `currentSpendUsd` | Context for budget utilization and run-out narrative in the model output. |
| `averageDailySpendUsd` | Daily USD burn context (also normalized from usage snapshots when available). |
| `averageDailyTokenUsage` | Token burn context vs `remainingTokens`. |
| `remainingTokens` | Token depletion horizon in the narrative. |
| `billingCycleEndDate` | Cycle-end context for health vs run-out messaging. |
| `recentDailySpendUsd` | Daily USD series fed into the request; informs model + downstream **confidence** (`BudgetForecastService.assessConfidence` uses how many recent days are present); **not** a hardcoded spike detector in Java. |
| `recentDailyTokenUsage7d`, `hourlyTokenUsage24h` | Primary series the prompt ties to **anomalySummary** (filled or defaulted in `BudgetForecastService`). |
| `modelUsageDistribution7d` | Model mix for **routingRecommendation** / savings text in the model output. |

There is no embedded linear regression in agent-service today. **Enriched usage signals** (7d/14d windows from usage, daily series, provider/model breakdown) improve what the model sees; any “spike” or anomaly wording follows from **Gemini** interpretation of those fields, not from a second numeric engine in this repo.

**Fields not owned by usage:** `remainingTokens` and `billingCycleEndDate` are typically sourced from **billing/identity** policy. The usage event includes them as **optional null** so agent (or a facade) can merge with billing data without contradicting those systems.

**Additional data** (if needed later): request counts, error ratio, or richer hourly shape for stricter **server-side** alerts — would require new product rules or a `schemaVersion` bump if encoded on the wire beyond what the current prompt consumes.

---

## Event: `UsagePredictionSignalsEvent`

- **Java type:** `com.eevee.usage.events.UsagePredictionSignalsEvent` (`libs/usage-events`).
- **Schema:** `schemaVersion = 1` (bump only on incompatible JSON changes).
- **Wire:** JSON, camelCase, `Content-Type: application/json`.

### RabbitMQ

| Item | Value |
|------|--------|
| Exchange (topic) | `usage.events` — constant `UsageOutboundEventAmqp.TOPIC_EXCHANGE_NAME` |
| Routing key | `usage.prediction.signals` — `UsageOutboundEventAmqp.ROUTING_KEY_PREDICTION_SIGNALS` |
| Suggested agent queue | `agent-service.usage-prediction-signals.queue` — `UsageOutboundEventAmqp.SUGGESTED_AGENT_SERVICE_QUEUE` |

**Headers (optional, duplicated for routing filters):** `teamId`, `userId`, `schemaVersion`.

### Payload (logical fields)

| Field | Description |
|-------|-------------|
| `schemaVersion` | `1` |
| `eventId` | Unique id per message (UUID). |
| `publishedAt` | UTC instant when usage-service published. |
| `asOfDateKst` | KST calendar date used as “today” for windows (aligned with dashboard analytics). |
| `teamId` | Team scope; **empty string** for personal (no team) rows in `daily_usage_summary`. |
| `userId` | User id for the slice. |
| `averageDailySpendUsd7d` | Sum of `total_cost` over the last **7** KST days ÷ 7. |
| `averageDailySpendUsd14d` | Sum over the last **14** KST days ÷ 14. |
| `averageDailyTokenUsage7d` | Sum of `total_tokens` over 7d ÷ 7. |
| `averageDailyTokenUsage14d` | Sum of `total_tokens` over 14d ÷ 14. |
| `recentDailySpendUsd` | **7** elements, **oldest → newest** KST day, each day’s summed `total_cost` for the slice (0 if no rows). Map into `BudgetForecastRequest.recentDailySpendUsd` for Gemini + **confidence** heuristics in `BudgetForecastService.assessConfidence` (e.g. more days → higher stated confidence); there is **no** fixed spend-spike multiplier implemented in agent Java. |
| `remainingTokens` | `null` from usage-service (quota not stored here). |
| `billingCycleEndDate` | `null` from usage-service; fill from billing when building `BudgetForecastRequest`. |
| `providerModelBreakdown7d` | List of `{ provider, model, totalCostUsd, totalTokens }` for the **7d** window, ordered by cost descending. |

**Consistency:** Averages and `recentDailySpendUsd` are derived from the same `daily_usage_summary` table and KST boundaries as the usage dashboard (`UsageAnalyticsJdbcRepository` / BFF), so **reported usage stats and prediction inputs stay aligned**.

**Fan-out:** One message per **(teamId, userId)** pair that had **any** `total_cost` or `total_tokens` in the **last 14** KST days (see publisher). Pairs with **no** activity in that window are skipped to limit noise.

---

## Step 3 — Publication strategy (implemented + future)

### Implemented: daily batch (KST)

- **When:** Spring `@Scheduled` **00:15 Asia/Seoul** (default `usage.prediction-signals.cron`), after the previous KST day’s data is available in `daily_usage_summary` (same source as analytics).
- **Why:** Balances freshness with bounded load: one pass over distinct slices, no per-request fan-out.
- **Configuration (usage-service):** `usage.rabbit.outbound-prediction.*`, `usage.prediction-signals.cron`, `usage.prediction-signals.schedule.enabled`.

### Future (not implemented)

- **Threshold / burst:** Re-publish for a slice when **projected** daily cost or token usage crosses a policy threshold (requires lightweight rolling state or stream processing).
- **Billing cycle anchor:** Optional extra publish on invoice cycle rollover if billing emits a marker event — merge design with billing without changing billing logic.

### Single envelope vs split events

- **Chosen:** **One envelope event** (`UsagePredictionSignalsEvent`) so consumers map directly to `BudgetForecastRequest`-style inputs and keep ordering/versioning simple.
- **Alternative:** Split “compact ticker” vs “heavy breakdown” — useful only if payload size or independent scaling becomes an issue; revisit if messages exceed broker/client limits.

---

## Subscriber checklist (e.g. agent-service)

1. Declare queue bound to `usage.events` + routing key `usage.prediction.signals`.
2. Deserialize JSON → `UsagePredictionSignalsEvent`.
3. Merge `averageDailySpendUsd7d`, `averageDailyTokenUsage7d`, `recentDailySpendUsd` with **billing** values for `monthlyBudgetUsd`, `currentSpendUsd`, `remainingTokens`, `billingCycleEndDate`.
4. Call `BudgetForecastService` (or a future model) with a **single consistent** `BudgetForecastRequest` snapshot.

**Note:** The **agent-service** consumer (queue, config, listener) is owned by that team; implement there when ready using the same exchange, routing key, and payload types above.

---

## Related code

- Contracts: `libs/usage-events` — `UsagePredictionSignalsEvent`, `ProviderModelUsageBreakdown`, `UsageOutboundEventAmqp`.
- Publisher: `usage-service` — `UsagePredictionSignalsPublisher`, `UsagePredictionSignalsBuilder`, `UsageAnalyticsJdbcRepository` (slice + aggregations).
