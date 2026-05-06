# Team API Key Status Changed Event Contract

버전: 1.1  
대상: Usage Service, Billing Service

---

## 1. 목적

- Team 컨텍스트에서 API Key의 메타데이터(별칭, provider)와 라이프사이클 상태를 Usage/Billing으로 동기화하기 위한 이벤트 계약이다.
- 소비 서비스는 이 이벤트를 기반으로 Team API Key 상태를 로컬 read model에 반영하고, 비용 집계/청구 파이프라인에서 삭제 예정/삭제 상태를 일관되게 처리한다.

---

## 2. RabbitMQ 라우팅 정보

- Exchange: `team.api-key.exchange`
- Routing Key: `team.api-key.status.changed`
- Queue (권장 예시): `team.api-key.status.changed.queue`

> 개인 키(Identity `ExternalApiKey...`) 이벤트와 충돌하지 않도록 `team.api-key.*` 네임스페이스를 사용한다.

---

## 3. 이벤트 식별자

- Event Type: `TEAM_API_KEY_STATUS_CHANGED`
- DTO 타입: `TeamDomainOutboundEvent.TeamApiKeyStatusChangedEvent`
- Schema Version: `"v1"` (필드 확장 포함)

---

## 4. 필드 정의

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `schemaVersion` | `String` | Y | 계약 버전. 현재 `"v1"` 고정값으로 발행한다. |
| `eventId` | `String` | Y | 이벤트 고유 식별자(UUID 권장). 멱등성 처리 기준 키. |
| `eventType` | `String` | Y | 이벤트 타입. 항상 `TEAM_API_KEY_STATUS_CHANGED`. |
| `occurredAt` | `String(ISO-8601 Instant)` | Y | 이벤트 발생 시각(UTC). |
| `teamId` | `Long` | Y | Team 식별자. |
| `teamApiKeyId` | `Long` | Y | Team API Key 식별자. |
| `alias` | `String` | Y | Team API Key 별칭. |
| `provider` | `String` | Y | Key 공급자 식별자(예: `OPENAI`). |
| `monthlyBudgetUsd` | `BigDecimal` | Y | Team API Key 월 예산(USD). Billing 임계치 판단 기준. |
| `status` | `Enum` | Y | Team API Key 상태. `ACTIVE`, `DELETION_REQUESTED`, `DELETED`. |
| `retainLogs` | `Boolean` | N | 삭제 시점 로그 보존 여부. 상태가 `DELETED`일 때 주로 의미가 있으며, 값이 없을 수 있다. |

핵심 필드 설명:

- `schemaVersion`: 스키마 확장/변경 시 소비자 분기 처리 기준으로 사용한다.
- `eventId`: 중복 발행/재전송 상황에서 소비자 dedupe 키로 사용한다.
- `status`: API Key 라이프사이클 상태 동기화의 중심 필드다.
- `monthlyBudgetUsd`: 상태 변경과 별개로 예산 변경이 전파되므로 read model 동기화에 사용한다.
- `retainLogs`: 삭제 이후 로그 보존 정책 전달용 필드다.

---

## 5. 멱등성 처리 가이드 (소비자 필수)

- 소비자는 `eventId`를 기준으로 이미 처리한 이벤트인지 확인해야 한다.
- 동일 `eventId`가 재수신되면 부작용 없이 무시(또는 no-op)해야 한다.
- `eventId` dedupe 저장소는 서비스 로컬 DB/Redis 등 내구 저장소를 사용한다.

---

## 6. JSON 페이로드 샘플

```json
{
  "schemaVersion": "v1",
  "eventId": "2d90d9a6-3190-4170-becf-2837ec6f7065",
  "eventType": "TEAM_API_KEY_STATUS_CHANGED",
  "occurredAt": "2026-04-26T04:10:27.918Z",
  "teamId": 42,
  "teamApiKeyId": 1007,
  "alias": "production-openai-key",
  "provider": "OPENAI",
  "monthlyBudgetUsd": 120.00,
  "status": "DELETION_REQUESTED",
  "retainLogs": true
}
```

---

## 7. 상태값 의미

- `ACTIVE`: 활성 상태, 사용 가능
- `DELETION_REQUESTED`: 삭제 예약(유예 기간 진행 중)
- `DELETED`: 물리 삭제 완료
