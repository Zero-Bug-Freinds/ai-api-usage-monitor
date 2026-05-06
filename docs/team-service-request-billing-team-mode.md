# Team Service 요청서: Billing 팀 모드·팀 키 예산 알림 지원

버전: 1.0  
작성일: 2026-05-06  
요청 대상: Team Service 팀  
요청 배경: Billing 팀 모드에서 팀 등록 키 단위 집계 및 예산 임계 알림 구현 필요

관련 문서:

- `docs/contracts/gateway-proxy.md`
- `docs/contracts/team-api-key-event-contract.md`
- `libs/usage-events/src/main/java/com/eevee/usage/events/UsageRecordedEvent.java`

---

## 1) 필수 요청: UsageRecordedEvent에서 팀 키 식별 가능 보장

Billing은 `UsageRecordedEvent`만 보고 집계·임계치 판단을 수행한다. 현재 `teamApiKeyId` 전용 필드가 없으므로, 팀 키 식별은 `apiKeySource` + `apiKeyId` 조합으로 보장되어야 한다.

### 요청 규약 (팀 키 요청인 경우)

| 필드 | 요구 값 |
|---|---|
| `apiKeySource` | `"team"` |
| `apiKeyId` | `String(teamApiKeyId)` (`team-service`의 팀 키 PK 문자열) |

### 목적

- Billing이 특정 팀 키 단위로 비용 집계 가능
- 예산 임계 알림에서 어떤 키가 임계를 넘겼는지 식별 가능

---

## 2) 요청: 팀 키 메타/예산 변경 이벤트 강화

Billing/Notification이 HTTP 반복 조회 없이 로컬 read model을 유지하려면, 팀 키 상태 이벤트에 예산 정보가 포함되거나 예산 변경 전용 이벤트가 필요하다.

현재 계약:

- Event Type: `TEAM_API_KEY_STATUS_CHANGED`
- Exchange: `team.api-key.exchange`
- Routing Key: `team.api-key.status.changed`
- Contract: `docs/contracts/team-api-key-event-contract.md`

### 권장 요청안

1. `TEAM_API_KEY_STATUS_CHANGED` payload에 `monthlyBudgetUsd` 필드 추가 (필수 권장)
2. 또는 예산 변경 전용 이벤트(`TEAM_API_KEY_BUDGET_UPDATED`) 신규 발행
3. 팀 키 예산 변경 시 상태 변경이 없어도 이벤트 반드시 발행

### payload 확장 권장 필드

| 필드 | 타입 | 비고 |
|---|---|---|
| `teamApiKeyId` | Long | 기존 식별자 |
| `teamId` | Long | 기존 팀 식별자 |
| `alias` | String | 기존 별칭 |
| `provider` | String | 기존 공급자 |
| `status` | String | 기존 상태 |
| `monthlyBudgetUsd` | BigDecimal | 예산 임계 판단 기준 |
| `updatedAt` | ISO-8601 | 변경 시각 |

---

## 3) 선택 요청: Notification 수신 대상 동기화 지원

예산 임계 알림 fan-out을 위해 다음 중 하나를 지원 요청한다.

- 팀 멤버십 변경(추가/삭제/권한 변경) 이벤트 발행
- 또는 팀 ID 기반 고성능 멤버 조회 API 제공

참고: 이는 선택 사항이며, 핵심 기능은 1) + 2)로 구현 가능하다.

---

## 최종 요약 (Team Service 액션 아이템)

1. 팀 키 요청의 `UsageRecordedEvent`에 `apiKeySource="team"` + `apiKeyId=teamApiKeyId` 문자열 보장
2. 팀 키 예산 변경이 Billing/Notification에 전파되도록 이벤트 계약 확장(`monthlyBudgetUsd` 또는 전용 이벤트)
3. (선택) 알림 대상 동기화를 위한 멤버십 이벤트 또는 조회 API 제공

위 1, 2가 충족되어야 Billing 팀 모드의 팀 키 단위 집계 및 예산 임계 알림이 안정적으로 동작한다.
