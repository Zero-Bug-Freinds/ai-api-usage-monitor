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
