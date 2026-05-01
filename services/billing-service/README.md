# billing-service

타 서비스가 RabbitMQ로 **billing이 발행하는(outbound) 이벤트**를 구독할 때는 저장소 루트의 [`docs/billing-outbound-events.md`](../../docs/billing-outbound-events.md) 를 본다.

## Identity 예산(월 예산 USD) 연동 설정

Billing UI/집계 응답에 표시되는 **월 예산(USD)** 은 `billing-service`가 Identity로부터 HTTP로 선택적으로 조회합니다.

- Identity 연동이 **비활성화** 되어 있거나, Identity가 **404** 를 반환하거나, 설정이 비어있으면 예산은 **없음(빈 값)** 으로 처리됩니다.
- 예산이 없더라도 Billing 기능 자체는 동작하며, UI에서는 “예산 없음”으로 표시될 수 있습니다.

### 설정 키 (환경변수)

`services/billing-service/src/main/resources/application.yml` 에서 아래 환경변수를 읽습니다.

- **`BILLING_IDENTITY_ENABLED`**: `true`로 설정 시 예산 조회 활성화 (기본 `false`)
- **`BILLING_IDENTITY_BASE_URL`**: Identity base URL (예: `http://localhost:8081`)
- **`BILLING_IDENTITY_BUDGET_PATH`**: 예산 조회 path 템플릿
  - `{userId}` 토큰을 포함할 수 있습니다. 포함하면 해당 토큰이 실제 userId로 치환됩니다.
  - `{userId}`가 없다면, 그대로 path로 사용됩니다.
  - 선행 `/` 유무는 상관없습니다(자동 정규화).

예시:

```text
BILLING_IDENTITY_ENABLED=true
BILLING_IDENTITY_BASE_URL=http://localhost:8081
BILLING_IDENTITY_BUDGET_PATH=/api/identity/v1/users/{userId}/budget
```

### Identity 응답 형식(중요)

`billing-service`는 Identity 응답 body를 JSON으로 파싱합니다.

- **기본 예산 조회(기존)**: 최상위 `monthlyBudgetUsd` 를 사용합니다.
- **키별 예산(선택)**: `monthlyBudgetsByKey` 배열이 있으면 `ExpenditureQueryService` / `IdentityBudgetClient.fetchMonthlyBudgetUsdForKey` 등에서 사용합니다.

예시:

```json
{
  "monthlyBudgetUsd": 100.00,
  "monthlyBudgetsByKey": [
    { "externalApiKeyId": 123, "provider": "OPENAI", "monthlyBudgetUsd": 25.00 }
  ]
}
```

## 월 예산 권위 API (Phase A)

`GET /api/v1/expenditure/monthly-budget-authority` 는 게이트웨이가 주입한 `X-User-Id` 기준으로 **과금·알림에 쓸 단일 effective 월 예산(USD)** 과 Identity 스냅샷을 돌려줍니다.

- **쿼리**
  - `scope`: `USER` 또는 `API_KEY` (필수)
  - `provider`, `apiKeyId`: `API_KEY` 스코프일 때 필요 (`apiKeyId`는 Identity의 `externalApiKeyId`와 매칭되는 **숫자 문자열**이어야 함)
  - `month`: 선택, 있으면 **해당 달의 1일(YYYY-MM-01)** 이어야 함. 생략 시 Asia/Seoul 기준 “이번 달 1일”을 응답의 `month` 필드에 사용(Phase A에서 Identity 호출 자체는 월 파라미터를 넘기지 않음).
- **Phase A 정책**: `effectiveMonthlyBudgetUsd` 는 Identity가 내려준 값을 그대로 권위로 노출합니다(플랜/팀 캡 병합은 billing에 아직 없음).

## 비용 정정 (AMQP) — finalized 월 정책

### finalized 월에 대한 정책

`monthly_expenditure_agg.is_finalized = true` 인 월/사용자/API 키 조합에 대해서는 **정정을 적용하지 않습니다**(집계 변경 없음, `BillingCostCorrectedEvent` 미발행).  
처리 시도는 `billing_cost_correction_processed` 로 **멱등 처리**되어 재전송 시에도 부작용이 없어야 합니다.

### 인바운드: `BillingCostCorrectionAmqp` (JSON)

Topic 라우팅 기본값은 `billing.rabbit.correction-in.*` (`application.yml`) 를 따릅니다.

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `schemaVersion` | number | 예 | 현재 `1` |
| `correctionEventId` | string (UUID) | 예 | 멱등 키 |
| `userId` | string | 예 | 집계 사용자 |
| `apiKeyId` | string | 예 | 집계 API 키 id |
| `monthStartDate` | string (ISO date) | 예 | 월 집계 키의 달 시작(반드시 해당 월 1일) |
| `deltaCostUsd` | number | 예 | 월(및 선택적 일) 집계에 더할 USD 델타(음수 허용, 단 아래 “음수 가드” 참고) |
| `aggDate` | string (ISO date) | 조건부 | 일 집계까지 적용할 때 `provider`·`model` 과 함께 필수 |
| `provider` | string enum | 조건부 | `OPENAI` / `ANTHROPIC` / `GOOGLE` (`usage-events` 의 `AiProvider`) |
| `model` | string | 조건부 | 일 집계 모델 키 |
| `promptTokenDelta` | number | 아니오 | 기본 `0` |
| `completionTokenDelta` | number | 아니오 | 기본 `0` |
| `optionalCorrectedTotalUsdForScope` | number | 아니오 | 소비자/감사용 메타(집계 검증에 필수 아님) |
| `relatedUsageEventId` | string (UUID) | 아니오 | 원 usage 이벤트 연결용 |

`aggDate`가 있으면 `aggDate`가 속한 달의 시작일이 `monthStartDate` 와 같아야 합니다.

**음수 가드:** 델타 적용 후 `monthly_expenditure_agg` 또는 해당 일 `daily_expenditure_agg` 의 비용 합이 0 미만이 되면 정정은 적용하지 않고(멱등 행은 이미 찍힌 상태로) 조용히 종료합니다.

### 아웃바운드: `BillingCostCorrectedEvent` (JSON)

성공적으로 집계에 반영된 경우에만(그리고 `billing.rabbit.correction-out.enabled=true` 인 경우) 커밋 이후 발행됩니다.

| 필드 | 타입 | 설명 |
|------|------|------|
| `schemaVersion` | number | 현재 `1` |
| `occurredAt` | string (ISO-8601 instant) | 발행 시각 |
| `correctionEventId` | UUID | 인바운드와 동일 |
| `userId` / `apiKeyId` | string | |
| `monthStartDate` | ISO date | |
| `appliedDeltaCostUsd` | number | 실제 적용된 델타 |
| `aggDate` / `provider` / `model` | optional | 일 집계가 없었으면 null |
| `optionalCorrectedTotalUsdForScope` | number? | |
| `relatedUsageEventId` | UUID? | |

### DDL 참고 (운영 수동 반영용)

엔티티 기본 생성 외에, 명시적 DDL이 필요하면 아래 파일을 참고하세요.

- `services/billing-service/src/main/resources/db/migration/V20260501120000__billing_cost_correction_processed.sql`

### 주의사항 / 트러블슈팅

- **인증 헤더 미전송**
  - 현재 구현(`IdentityBudgetClient`)은 예산 조회 요청에 별도 인증 헤더/토큰을 붙이지 않습니다.
  - Identity가 인증을 요구하는 엔드포인트라면, 이 연동은 예산을 “없음”으로 처리하게 됩니다.

- **조용히 실패할 수 있음**
  - 404는 정상적으로 “예산 없음”으로 취급합니다.
  - 그 외 상태코드/예외도 예산을 “없음”으로 처리하며, 로그는 `debug` 레벨로만 남습니다.
  - 예산이 계속 안 뜬다면, 우선 `BILLING_IDENTITY_*` 값이 실제로 주입됐는지 확인한 뒤,
    Identity 쪽 예산 엔드포인트가 공개 접근/응답 형식이 맞는지 점검하세요.

## 비용이 0 USD로 집계될 때(DB 점검 SQL 체크리스트)

`billing-service`의 비용 계산은 **`provider_model_price`에서 (provider, model, occurredAt)에 맞는 가격 row를 찾지 못하면 0 USD로 떨어질 수 있습니다.**
또한 가격이 정상이어도 집계 upsert가 실패/누락되면 `daily_expenditure_agg` / `monthly_expenditure_agg`가 증가하지 않습니다.

아래 SQL은 **PostgreSQL 기준**이며, 운영/로컬에서 “가격 정책(row) 문제인지 / 집계 파이프라인 문제인지”를 빠르게 구분하기 위한 체크리스트입니다.

### 1) 먼저, 어떤 provider/model이 들어오고 있는지 확인

최근 집계 데이터에서 provider/model 조합을 확인합니다.

```sql
-- 최근 14일 동안 들어온 provider/model 목록
SELECT
  provider,
  model,
  COUNT(*) AS rows,
  SUM(total_prompt_tokens) AS prompt_tokens,
  SUM(total_completion_tokens) AS completion_tokens,
  SUM(total_cost_usd) AS cost_usd
FROM daily_expenditure_agg
WHERE agg_date >= (CURRENT_DATE - INTERVAL '14 days')
GROUP BY provider, model
ORDER BY cost_usd DESC, rows DESC;
```

특정 사용자/키 기준으로 좁히고 싶다면:

```sql
-- 특정 userId + apiKeyId로 최근 50개 일 집계 확인
SELECT *
FROM daily_expenditure_agg
WHERE user_id = :userId
  AND api_key_id = :apiKeyId
ORDER BY agg_date DESC
LIMIT 50;
```

### 2) 가격 테이블(`provider_model_price`)에 row가 있는지 확인

provider/model이 확인되면, 가격 row 존재 여부와 유효기간을 확인합니다.

```sql
-- provider/model에 대한 가격 row 전체 확인(최근 valid_from 우선)
SELECT
  provider,
  model,
  valid_from,
  valid_to,
  input_usd_per_million_tokens,
  output_usd_per_million_tokens
FROM provider_model_price
WHERE provider = :provider
  AND model = :model
ORDER BY valid_from DESC;
```

“현재 시점” 기준으로 활성(active) 가격 row가 있는지:

```sql
-- 현재 시점 활성 가격(row) 확인
SELECT
  provider,
  model,
  valid_from,
  valid_to,
  input_usd_per_million_tokens,
  output_usd_per_million_tokens
FROM provider_model_price
WHERE provider = :provider
  AND model = :model
  AND valid_from <= NOW()
  AND (valid_to IS NULL OR NOW() < valid_to)
ORDER BY valid_from DESC
LIMIT 5;
```

가격 row가 “아예 없음”을 빠르게 찾고 싶다면:

```sql
-- 집계에 등장했지만(provider/model) 가격 row가 전혀 없는 조합 찾기
SELECT DISTINCT d.provider, d.model
FROM daily_expenditure_agg d
LEFT JOIN provider_model_price p
  ON p.provider = d.provider
 AND p.model = d.model
WHERE d.agg_date >= (CURRENT_DATE - INTERVAL '30 days')
  AND p.id IS NULL
ORDER BY d.provider, d.model;
```

### 3) “토큰은 있는데 비용이 0”인지 확인(가격 매칭 실패 신호)

```sql
-- 토큰은 누적되는데 비용이 0인 row(최근 14일)
SELECT *
FROM daily_expenditure_agg
WHERE agg_date >= (CURRENT_DATE - INTERVAL '14 days')
  AND (total_prompt_tokens > 0 OR total_completion_tokens > 0)
  AND total_cost_usd = 0
ORDER BY agg_date DESC
LIMIT 200;
```

이 경우 `provider_model_price`의 (provider/model/valid_from~valid_to)가 실제 이벤트 발생 시각과 맞는지(특히 `valid_to`)를 우선 점검하세요.

### 4) 월 집계(`monthly_expenditure_agg`)가 같이 증가하는지 확인

```sql
-- 최근 6개월 월 집계(특정 userId + apiKeyId)
SELECT *
FROM monthly_expenditure_agg
WHERE user_id = :userId
  AND api_key_id = :apiKeyId
  AND month_start_date >= (DATE_TRUNC('month', CURRENT_DATE) - INTERVAL '6 months')::date
ORDER BY month_start_date DESC;
```

`is_finalized=true`인 row는 월 비용이 더 이상 증가하지 않습니다(집계가 막힌 것처럼 보일 수 있음).

### 5) billing이 API Key를 “봤는지” 확인(`billing_user_api_key_seen`)

이 테이블은 billing이 특정 (userId, apiKeyId, provider)의 이벤트를 처리하며 “처음 본 시각”을 기록합니다.

```sql
-- 특정 userId + apiKeyId가 어떤 provider에서 관측되었는지
SELECT *
FROM billing_user_api_key_seen
WHERE user_id = :userId
  AND api_key_id = :apiKeyId
ORDER BY first_seen_at ASC;
```

### 6) Seed 관련 주의(로컬/개발에서 특히 중요)

- `provider_model_price`는 Seed가 존재하더라도, **테이블이 이미 일부라도 채워져 있으면** 환경에 따라 신규 모델 가격이 자동으로 들어오지 않을 수 있습니다.
- 집계에 등장한 provider/model인데 `provider_model_price`에 row가 없으면, DB에 가격 row를 추가한 뒤 다시 집계가 누적되는지 확인하세요.

### 7) `provider_model_price` Seed 모드(누락 row 보강 옵션)

기본 동작은 “테이블이 완전히 비어 있을 때만” Seed가 실행됩니다. 로컬/개발 환경에서 카탈로그에 추가된 신규 모델 row를 빠르게 보강하려면 아래 옵션을 켤 수 있습니다.

- **환경 변수**: `BILLING_PRICING_SEED_MISSING=true`
- **설정 키**: `billing.pricing.seed-missing=true` (`services/billing-service/src/main/resources/application.yml`)

이 모드에서는 카탈로그의 row 중 **DB에 없는(provider/model/valid_from/valid_to=null) row만 추가 삽입**합니다(멱등).

## billing-web USD 표시 규칙(아주 작은 값)

지출 화면에서 USD 금액이 아주 작을 때 `toFixed(4)`로 인해 `$0.0000`으로 보이는 혼동을 줄이기 위해, billing-web은 아래 정책으로 표시를 통일합니다.

- **기본**: 4자리 고정 \(예: `$12.3457`\)
- **예외(임계값 라벨)**: \(0 < value < 0.0001\)이면 **`"<$0.0001"`**
- **null/NaN**: `—`

구현은 `services/billing-service/web/src/lib/expenditure/money.ts`의 `formatUsd` / `formatUsdTooltip`을 사용합니다.

