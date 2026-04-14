# billing-service

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

`billing-service`는 Identity 응답 body를 JSON으로 파싱하고, 최상위 필드 `monthlyBudgetUsd` 만 읽습니다.

예시:

```json
{
  "monthlyBudgetUsd": 100.00
}
```

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

