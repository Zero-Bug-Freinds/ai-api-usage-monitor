# billing-service ↔ identity-service 월 예산 HTTP (선택)

`billing-service`의 [`IdentityBudgetClient`](../services/billing-service/src/main/java/com/eevee/billingservice/integration/IdentityBudgetClient.java)는 `billing.identity.enabled=true`일 때만 identity에 GET으로 예산을 조회한다.

## 환경 변수 (루트 `.env`)

- `BILLING_IDENTITY_ENABLED` — `true`로 켠다.
- `BILLING_IDENTITY_BASE_URL` — identity HTTP 베이스 (예: `http://localhost:8090`).
- `BILLING_IDENTITY_BUDGET_PATH` — Spring 설정 `billing.identity.budget-path-template`에 매핑된다. `{userId}` 플레이스홀더를 쓸 수 있다.
  - 이메일 기반(권장, `X-User-Id`가 이메일인 구성): `/api/identity/v1/users/budget?email={userId}`
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
      "provider": "GEMINI",
      "alias": "개인키-2",
      "monthlyBudgetUsd": 25.0
    }
  ]
}
```

`billing-service`는 현재 하위 호환을 위해 루트의 `monthlyBudgetUsd`를 계속 사용한다. 404·비활성·오류 시 예산 필드는 생략된다.

## MSA 원칙

billing은 **identity_db에 JDBC로 붙지 않고**, 위와 같은 **공개·내부 HTTP API**만 사용한다 (`docs/msa-database-and-service-integration.md`).
