# billing-service ↔ identity-service 월 예산 HTTP (선택)

`billing-service`의 [`IdentityBudgetClient`](../services/billing-service/src/main/java/com/eevee/billingservice/integration/IdentityBudgetClient.java)는 `billing.identity.enabled=true`일 때만 identity에 GET으로 예산을 조회한다.

## Spring 설정 (`application.yml`)

[`IdentityProperties`](../services/billing-service/src/main/java/com/eevee/billingservice/config/IdentityProperties.java)는 **`billing.identity.*`** prefix만 바인딩한다. 설정은 `services/billing-service/src/main/resources/application.yml`의 **`billing:`** 블록 아래에 둔다 (top-level `identity:` 와 혼동하지 않는다).

```yaml
billing:
  identity:
    enabled: ${BILLING_IDENTITY_ENABLED:false}
    base-url: ${BILLING_IDENTITY_BASE_URL:}
    budget-path-template: ${BILLING_IDENTITY_BUDGET_PATH:/api/identity/v1/users/budget?email={userId}}
  pricing:
    seed-missing: ${BILLING_PRICING_SEED_MISSING:false}
```

- **`billing.identity.enabled`**: env `BILLING_IDENTITY_ENABLED`와 매핑. `application.yml` 기본은 `false`(호스트 단독 bootRun). **Docker Compose 로컬**은 `docker-compose.yml`에서 `${BILLING_IDENTITY_ENABLED:-true}`로 기본 ON(`.env.example`과 동일).
- **`billing.identity.base-url`**: 비어 있으면 HTTP 호출을 스킵한다(`enabled=true`여도 예산 필드는 `null`).
- **`billing.identity.budget-path-template`**: env 미설정 시 위 이메일 쿼리 path가 기본값. `{userId}`는 게이트웨이가 주입한 `X-User-Id`(이메일 또는 숫자 id)로 치환된다.

회귀 테스트: [`IdentityPropertiesBindingTest`](../services/billing-service/src/test/java/com/eevee/billingservice/config/IdentityPropertiesBindingTest.java) — `billing.identity.*` 바인딩 및 잘못된 prefix 미바인딩 검증.

## 환경 변수 (루트 `.env`)

- `BILLING_IDENTITY_ENABLED` — `true`로 켠다. Compose 로컬 기본 `true`, `application.yml` 단독 기동 기본 `false`.
- `BILLING_IDENTITY_BASE_URL` — identity HTTP 베이스 (예: Compose `http://identity-service:8080`, 호스트 bootRun `http://localhost:8090`).
- `BILLING_IDENTITY_BUDGET_PATH` — Spring 설정 `billing.identity.budget-path-template`에 매핑된다. `{userId}` 플레이스홀더를 쓸 수 있다.
  - 이메일 기반(권장, `X-User-Id`가 이메일인 구성): `/api/identity/v1/users/budget?email={userId}` (`.env.example`·`application.yml` 기본)
  - 숫자 userId 기반: `/api/identity/v1/users/{userId}/budget`

## 응답 JSON

Identity 예산 API 응답은 아래 필드를 포함한다.

- `monthlyBudgetUsd` (number, USD): 사용자 활성 키들의 월 예산 합계
- `monthlyBudgetsByKey` (array): 키별 월 예산 목록
  - `externalApiKeyId` (number)
  - `provider` (string)
  - `alias` (string)
  - `monthlyBudgetUsd` (number, USD)

예시:

```json
{
  "monthlyBudgetUsd": 45.5,
  "monthlyBudgetsByKey": [
    {
      "externalApiKeyId": 101,
      "provider": "OPENAI",
      "alias": "개인키-1",
      "monthlyBudgetUsd": 20.5
    },
    {
      "externalApiKeyId": 102,
      "provider": "GOOGLE",
      "alias": "개인키-2",
      "monthlyBudgetUsd": 25.0
    }
  ]
}
```

`billing-service`는 현재 하위 호환을 위해 루트의 `monthlyBudgetUsd`를 계속 사용한다.

### API 응답에서 `monthlyBudgetUsd` 의미

| 값 | 의미 |
|----|------|
| **`null`** (JSON 필드 생략 포함) | Identity HTTP **미호출**(`enabled=false`, `base-url` blank), **호출 실패**, **404**(활성 키 없음 등). 지출 `totalCostUsd`는 billing DB에서 정상 반환. |
| **`0`** | 연동 **성공**, 활성 키는 있으나 **키별 월 예산이 모두 0**이거나 합계가 0. |
| **`> 0`** | 연동 성공, 월 예산 합계 표시·진행률 계산 가능. |

404·비활성·오류 시 billing은 optional empty로 처리해 지출 API의 `monthlyBudgetUsd`를 **`null`** 로 내려보낸다. 연동은 됐지만 예산이 없으면 **`0`** 을 반환한다.

## billing 내부에서의 쓰임 (요약)

- **지출 요약 등**: 한 사용자에 대한 합계 `monthlyBudgetUsd`를 그대로 활용할 수 있다.
- **예산 임계 AMQP** (`billing.budget.threshold.reached`): 이벤트 한 건마다 `IdentityBudgetClient.fetchMonthlyBudgetKeyRow(userId, provider, apiKeyId)`로 **해당 키 한 줄**만 고르고,
  - 월 예산(`monthlyBudgetUsd`) 뿐 아니라 표시용 별칭(`alias`)도 함께 활용해 이벤트 페이로드의 `apiKeyAlias`로 전달할 수 있다.
  - `UsageRecordedEvent.apiKeyId`는 Identity의 **`externalApiKeyId`와 동일한 숫자**로 파싱 가능해야 한다(문자열이어도 내용이 long이어야 매칭).
  - **프로바이더 이름**: billing `AiProvider`와 Identity JSON의 `provider`는 `OPENAI` / `ANTHROPIC` / `GOOGLE`를 사용한다. 레거시 `GEMINI` 데이터가 남아 있으면 내부에서 `GOOGLE`로 호환 매핑한다.
  - 위 매칭 행이 없거나 예산이 0 이하면 해당 사용 이벤트에 대해 **임계 이벤트를 발행하지 않는다**.

## Billing 지출 UI (`/billing`)

[`expenditure-dashboard.tsx`](../services/billing-service/web/src/components/expenditure/expenditure-dashboard.tsx)는 API 스키마 변경 없이 위 표와 동일하게 구분한다.

| `monthlyBudgetUsd` | 헤더 「예산 연동」 | 본문 안내 |
|--------------------|-------------------|-----------|
| `null` | 연동 없음 — `BILLING_IDENTITY_*` 확인 | 예산 조회 불가 — billing Identity 연동(`BILLING_IDENTITY_*`) 확인 |
| `0` | 연동됨 (예산 미설정) | 월 예산 미설정 — Identity 계정 설정에서 키별 예산 입력 |
| `> 0` | 표시됨 | 지출 대비 월 예산·진행률·잔여 표시 |

## 트러블슈팅

1. **`monthlyBudgetUsd`가 계속 `null`**
   - `billing.identity.*`가 **`billing:` 블록** 아래인지 확인 (`identity.identity.*` 등 잘못된 경로는 바인딩되지 않음).
   - `BILLING_IDENTITY_ENABLED=true`, `BILLING_IDENTITY_BASE_URL`이 identity에 **reachable**한지 확인 (Compose: `http://identity-service:8080`, 호스트 bootRun: `http://localhost:8090` 등).
   - Identity 직접 호출: `GET /api/identity/v1/users/budget?email={email}` → 200인지.
   - (선택) `GET /api/v1/expenditure/monthly-budget-authority?scope=USER` 의 `notes`에 `"Identity budget envelope unavailable..."` 가 없어야 함.

2. **연동은 됐는데 UI에 「예산 미설정」**
   - API `monthlyBudgetUsd === 0` — Identity 계정 설정에서 키별 `monthlyBudgetUsd` 입력.

3. **조용한 실패**
   - `IdentityBudgetClient`는 인증 헤더를 붙이지 않는다. Identity가 인증을 요구하면 예산은 `null`로 처리된다.
   - HTTP 오류는 `debug` 로그만 남기고 예산 필드는 `null`.

## MSA 원칙

billing은 **identity_db에 JDBC로 붙지 않고**, 위와 같은 **공개·내부 HTTP API**만 사용한다 (`docs/msa-database-and-service-integration.md`).
