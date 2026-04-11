# billing-service ↔ identity-service 월 예산 HTTP (선택)

`billing-service`의 [`IdentityBudgetClient`](../services/billing-service/src/main/java/com/eevee/billingservice/integration/IdentityBudgetClient.java)는 `billing.identity.enabled=true`일 때만 identity에 GET으로 예산을 조회한다.

## 환경 변수 (루트 `.env`)

- `BILLING_IDENTITY_ENABLED` — `true`로 켠다.
- `BILLING_IDENTITY_BASE_URL` — identity HTTP 베이스 (예: `http://localhost:8090`).
- `BILLING_IDENTITY_BUDGET_PATH` — `{userId}` 플레이스홀더를 쓸 수 있다. 예: `/api/v1/users/{userId}/budget`.

## 응답 JSON

클라이언트는 응답 루트에 **`monthlyBudgetUsd`** (number, USD) 필드가 있으면 그 값을 지출 요약 API에 붙인다. 404·비활성·오류 시 예산 필드는 생략된다.

## MSA 원칙

billing은 **identity_db에 JDBC로 붙지 않고**, 위와 같은 **공개·내부 HTTP API**만 사용한다 (`docs/msa-database-and-service-integration.md`).
