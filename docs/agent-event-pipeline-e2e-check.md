# Agent Event Pipeline E2E Check (Local)

`agent-service` 퍼센트/예측 값이 0으로 보일 때, 상류 트래픽 부족인지 파이프라인 문제인지 빠르게 분리하는 점검 가이드입니다.

## 1) 실트래픽으로 Proxy 타격 (권장)

게이트웨이 개발 모드(`GATEWAY_DEV_MODE=true`) 기준 예시:

```bash
curl -X POST "http://localhost:8080/api/v1/ai/openai/v1/chat/completions" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d '{
    "model":"gpt-4o-mini",
    "messages":[{"role":"user","content":"Say hello in one short sentence."}],
    "max_tokens":16,
    "temperature":0
  }'
```

참고:
- 운영 모드(`GATEWAY_DEV_MODE=false`)에서는 Gateway JWT 정책에 맞춰 `Authorization: Bearer <token>`이 필요합니다.
- 로컬에서 외부 API 키/네트워크 상태에 따라 호출이 실패할 수 있으므로, 아래 2번 합성 이벤트 방식으로도 체인을 검증할 수 있습니다.

## 2) 자동 검증 스크립트 실행

PowerShell:

```powershell
.\scripts\verify-agent-event-pipeline.ps1 -ProxyLiveTraffic
```

실트래픽 없이 파이프라인만 검증:

```powershell
.\scripts\verify-agent-event-pipeline.ps1
```

스크립트 동작:
- `Before traffic` 스냅샷
- (옵션) Proxy live 호출
- `usage.events`로 `usage.recorded` 합성 1건 발행
- `After traffic` 스냅샷
- 아래 핵심 지표 출력:
  - `debug/events` 이벤트 타입 분포
  - `billing-signals` 내 대상 `apiKeyId` 비용
  - `available-context`의 `budgetUsagePercent`, `currentSpendUsd`, `providerStats`

## 3) 수동 API 확인 순서

```bash
curl "http://localhost:8097/api/v1/agents/debug/events?limit=50"
curl "http://localhost:8097/api/v1/agents/billing-signals"
curl "http://localhost:3005/agent/api/v1/agents/available-context" -H "x-user-id: 1"
```

호스트에서 `agent-service`는 보통 **8097**로 노출된다(`docker-compose`: 알림 등과 8096 충돌 방지). 컨테이너 내부·서비스 간 호출은 `8096`이다.

정상 기준:
- `debug/events`: `UsageCostFinalizedEvent`, `DailyCumulativeTokensUpdatedEvent` 존재
- `billing-signals`: 대상 키의 `latestEstimatedCostUsd` 0 초과
- `available-context`: `budgetStats.currentSpendUsd`, `budgetStats.budgetUsagePercent` 갱신

## 4) 자주 헷갈리는 포인트

- `usage.prediction.signals`는 실시간이 아니라 스케줄 발행입니다.
  - 기본 cron: `0 15 0 * * *` (KST)
- 실시간 체인은 `usage.recorded`를 시작으로 진행됩니다.
  - `usage.recorded` -> billing 처리 -> `usage.cost.finalized`
  - `usage.recorded` -> usage 누적 처리 -> `usage.daily.cumulative.tokens`
- **`billing-signals`가 비어 있는데 billing DB에는 지출이 있는 경우**
  - RabbitMQ 큐(`ai-agent.billing.cost-finalized.queue`)에 메시지가 안 쌓이거나, 재시작 직후 이벤트가 아직 없을 수 있습니다.
  - 바인딩은 `billing.events` + 라우팅 키 `usage.cost.finalized`가 정상인지 RabbitMQ Management에서 확인합니다.
  - 완화: `BillingSignalSnapshotService`는 **Redis**에 스냅샷을 저장·복구하고, `BillingSignalReconciliationService`가 billing **`expenditure/summary`**(수정 불가 전제의 공개 API)로 주기 보정합니다. 상세는 `docs/agent-service-overview-20260430.md` §6·§9.
- **Billing 지출 % vs Agent 키 카드 %**
  - Billing **상단** 개인 배너는 **모든 키 지출 합 / 사용자 단위 월 예산**입니다.
  - Billing에서 **특정 키·프로바이더**를 고른 뒤의 요약 %와 Agent 키 카드는 둘 다 **`expenditure/summary` 키 단위**에 맞추는 것이 목표입니다. 상단 %와 키 카드 %를 직접 비교하면 어긋날 수 있습니다.
- **Gateway JWT 모드**에서 Proxy 실트래픽·일부 스크립트는 `Authorization: Bearer`가 필요합니다. `scripts/verify-expenditure-chain.ps1` 주석의 `EXPENDITURE_VERIFY_GATEWAY_JWT` 등을 참고합니다.
