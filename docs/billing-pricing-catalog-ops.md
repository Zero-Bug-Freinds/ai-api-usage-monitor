# billing-service 모델 단가(Provider catalog) 운영

비용은 [`ExpenditureCostCalculator`](../services/billing-service/src/main/java/com/eevee/billingservice/service/ExpenditureCostCalculator.java)가 `provider_model_price` 행을 조회해 USD로 계산한다. 초기 행은 [`ProviderModelPriceSeed`](../services/billing-service/src/main/java/com/eevee/billingservice/config/ProviderModelPriceSeed.java)와 [`OfficialProviderModelPriceCatalog`](../services/billing-service/src/main/java/com/eevee/billingservice/pricing/OfficialProviderModelPriceCatalog.java)에서 채운다.

## Seed 동작(로컬/개발)과 운영 주의

`ProviderModelPriceSeed`는 기본적으로 **`provider_model_price`가 완전히 비어 있을 때만** 카탈로그 행을 삽입한다.

이미 테이블에 일부 행이 존재하는 환경(운영/스테이징/로컬 DB 재사용)에서 **카탈로그에 신규 모델 row를 추가해도 자동으로 반영되지 않을 수 있다.**

개발/로컬에서 “카탈로그에 있는데 DB에 없는 row만” 보강 삽입하고 싶다면 아래 옵션을 켠다:

- **설정 키**: `billing.pricing.seed-missing=true`
- **환경 변수**: `BILLING_PRICING_SEED_MISSING=true`

루트 **`docker-compose.yml`** 의 `billing-service` 는 기본으로 `BILLING_PRICING_SEED_MISSING=${BILLING_PRICING_SEED_MISSING:-true}` 를 넘기므로, 로컬 Compose만으로도 위 “누락 row 보강”이 켜진 상태로 기동한다(운영·스테이징에서는 `.env` 등으로 `false`를 줄 수 있음).

> 운영에서는 DB 마이그레이션/데이터 관리 정책에 따라 명시적으로 단가 row를 추가·검증하는 것을 권장한다(Seed 옵션에 의존하지 않기).

## 운영 시 권장

1. **공급사 단가 변경** 시 카탈로그·시드에 반영하고, 필요하면 `valid_from` / `valid_to`로 이력을 남긴다.
2. **새 모델** 추가 시 동일하게 `provider_model_price`에 행을 넣는다.
3. 변경 후 **로컬·스테이징**에서 지출 집계 샘플 이벤트로 금액을 검증한다.

## 모델 문자열 별칭(자동 단가 매칭) 주의

`provider_model_price`는 `(provider, model)` **완전 일치**로 매칭한다. usage·proxy가 넣는 `model` 문자열이 카탈로그·DB 키와 다르면 단가를 못 찾아 비용이 **0**이 될 수 있다.

로컬/개발 편의를 위해 `BillingRecordedService`는 **정확 일치가 실패한 뒤** 아래 후보를 순서대로 조회하고, 카탈로그에 있는 **베이스 모델** 단가가 있으면 이벤트에 쓰인 모델명으로 **alias `provider_model_price` 행을 자동 삽입**해 이후 동일 문자열도 매칭되게 한다.

| 공급사 | 대표적인 변형(예) | 별칭 후보로 시도하는 규칙(요약) |
|--------|-------------------|----------------------------------|
| **OPENAI** | `gpt-5.4-mini-2026-03-17` | 접미사 `-YYYY-MM-DD`(dated snapshot)를 떼어 base 모델로 조회 |
| **GOOGLE** | `models/gemini-1.5-flash`, `gemini-1.5-flash-002` | `models/` 접두 제거, 끝의 `-NNN`(3자리) 제거, 필요 시 inner 경로에 대해 OpenAI와 동일한 날짜 접미사 제거 |
| **ANTHROPIC** | `claude-3-5-sonnet-20240620` | 끝의 `-YYYYMMDD`(8자리) 제거 |

별칭으로도 단가가 없으면 비용은 **0**이며, 운영 관측을 위해 **동일 `(provider, model)` 조합에 대해 한 번** `WARN` 로그를 남기고 이후 동일 키는 `DEBUG`로만 남긴다(키 수 상한 있음).

> 운영 환경에서는 공급사별 가격 정책(스냅샷·리전 접미사가 base와 동일 단가인지)을 팀 기준으로 확인하고, 카탈로그에 없는 **완전 신규 모델 문자열**은 마이그레이션 또는 카탈로그 확장이 필요하다.

## Gemini 단가(컨텍스트·오디오 등 조건부 단가) 운영 룰

Gemini API pricing은 동일한 `model`이라도 아래처럼 **조건에 따라 단가가 달라질 수 있다**:

- **프롬프트(컨텍스트) 길이 구간**: 특정 토큰 수(예: 200K) 초과 시 input/output 단가 상승
- **입력 타입**: Flash/Flash-Lite 계열은 **audio input** 단가가 text/image/video input보다 높을 수 있음

현재 billing의 가격 테이블(`provider_model_price`)은 모델별로 아래 2개 값만 보관한다:

- `input_usd_per_million_tokens`
- `output_usd_per_million_tokens`

따라서 **옵션 A(대표 단가 1쌍만 유지)** 운영 룰은 다음과 같다:

- billing은 Gemini 비용을 계산할 때 **“대표 단가(기본 구간 + text/image/video input 기준)”** 1쌍으로만 계산한다.
- 긴 컨텍스트 구간/오디오 입력 등으로 인해 공급사 청구서에서 더 높은 단가가 적용되는 경우가 있어도, billing 계산 결과는 **추정치(estimated)**로 취급한다.
- 정확한 반영이 필요하면 `provider_model_price`의 스키마 확장(입력 타입/컨텍스트 구간/processing mode 등)과 usage 이벤트 페이로드 확장까지 포함해 설계를 진행한다.

## 참고

- 집계 일자·월 경계는 **KST**를 사용한다 (`BillingRecordedService`, `MonthlyExpenditureFinalizeScheduler`).
- billing이 산출한 USD는 `UsageCostFinalizedEvent`로 usage-service에 전달되어 `usage_recorded_log.estimated_cost`와 정합할 수 있다(개요: `docs/billing-service-overview-20260412.md` 부록 A).
- 지출 UI의 “비결제 시뮬레이션” 고지는 `services/billing-service/web` 지출 화면에 표시한다.
