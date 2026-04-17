# billing-service 모델 단가(Provider catalog) 운영

비용은 [`ExpenditureCostCalculator`](../services/billing-service/src/main/java/com/eevee/billingservice/service/ExpenditureCostCalculator.java)가 `provider_model_price` 행을 조회해 USD로 계산한다. 초기 행은 [`ProviderModelPriceSeed`](../services/billing-service/src/main/java/com/eevee/billingservice/config/ProviderModelPriceSeed.java)와 [`OfficialProviderModelPriceCatalog`](../services/billing-service/src/main/java/com/eevee/billingservice/pricing/OfficialProviderModelPriceCatalog.java)에서 채운다.

## Seed 동작(로컬/개발)과 운영 주의

`ProviderModelPriceSeed`는 기본적으로 **`provider_model_price`가 완전히 비어 있을 때만** 카탈로그 행을 삽입한다.

이미 테이블에 일부 행이 존재하는 환경(운영/스테이징/로컬 DB 재사용)에서 **카탈로그에 신규 모델 row를 추가해도 자동으로 반영되지 않을 수 있다.**

개발/로컬에서 “카탈로그에 있는데 DB에 없는 row만” 보강 삽입하고 싶다면 아래 옵션을 켠다:

- **설정 키**: `billing.pricing.seed-missing=true`
- **환경 변수**: `BILLING_PRICING_SEED_MISSING=true`

> 운영에서는 DB 마이그레이션/데이터 관리 정책에 따라 명시적으로 단가 row를 추가·검증하는 것을 권장한다(Seed 옵션에 의존하지 않기).

## 운영 시 권장

1. **공급사 단가 변경** 시 카탈로그·시드에 반영하고, 필요하면 `valid_from` / `valid_to`로 이력을 남긴다.
2. **새 모델** 추가 시 동일하게 `provider_model_price`에 행을 넣는다.
3. 변경 후 **로컬·스테이징**에서 지출 집계 샘플 이벤트로 금액을 검증한다.

## OpenAI 스냅샷 모델(날짜 suffix) 주의

OpenAI 이벤트의 `model`이 `gpt-5.4-mini-2026-03-17`처럼 **날짜 suffix**를 포함할 수 있다.
`provider_model_price`는 `(provider, model)` **완전 일치**로 매칭하므로, DB에 `gpt-5.4-mini`만 있고 스냅샷 ID가 들어오면 단가를 못 찾아 비용이 `0`이 될 수 있다.

로컬/개발 편의를 위해 billing은 다음 정책을 지원한다:

- OpenAI의 `-YYYY-MM-DD` suffix 패턴이 들어온 모델은 base 모델(`gpt-5.4-mini`) 단가를 먼저 조회하고,
- base 단가가 존재하면 스냅샷 모델명으로 **alias 단가 row를 자동으로 upsert**해서 이후 이벤트부터는 DB가 따라가도록 한다.

> 운영 환경에서는 가격 정책(스냅샷 모델이 base와 동일 단가인지)을 팀 기준으로 확인하고, 필요하면 별도 row를 명시적으로 관리하는 것을 권장한다.

## 참고

- 집계 일자·월 경계는 **KST**를 사용한다 (`BillingRecordedService`, `MonthlyExpenditureFinalizeScheduler`).
- billing이 산출한 USD는 `UsageCostFinalizedEvent`로 usage-service에 전달되어 `usage_recorded_log.estimated_cost`와 정합할 수 있다(개요: `docs/billing-service-overview-20260412.md` 부록 A).
- 지출 UI의 “비결제 시뮬레이션” 고지는 `services/billing-service/web` 지출 화면에 표시한다.
