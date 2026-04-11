# billing-service 개요 (구현 스냅샷, 2026-04-12)

## 1) 목적·범위

- **목적**: 본 문서는 현재 저장소에 구현된 `billing-service`의 **디렉터리 구조**, **기능(처리 규칙·API·스케줄러·BFF 포함)**, **`billing_db` 테이블 구조**, **로컬 인프라**, **연관 서비스와의 연동**, **통신 방식**을 한곳에 정리한다.
- **범위**: `services/billing-service` Spring Boot 애플리케이션 및 `services/billing-service/web`(Next.js 지출 대시보드 BFF)까지 포함한다.
- **참고**: 도메인·지출 설계의 상세 논의는 `private-docs/architecture-report/billing-service-expenditure-design-20260410.md` 등을 본다.

---

## 2) 저장소·모듈 위치

| 항목 | 경로 |
|------|------|
| 백엔드 (Spring Boot) | `services/billing-service/` (Gradle 단일 모듈, `usage-events` 로컬 프로젝트 의존) |
| 프론트 (Next.js, 선택) | `services/billing-service/web/` |
| 공유 이벤트 타입 | `libs/usage-events` (`UsageRecordedEvent`, `TokenUsage`, `AiProvider` 등) |

---

## 3) 패키지·구조 (백엔드)

대략적인 책임 분리:

| 패키지/영역 | 역할 |
|-------------|------|
| `config` | `application.yml` 바인딩(`BillingProperties`, `BillingRabbitProperties`, `IdentityProperties`), RabbitMQ 큐/교환기 바인딩, Jackson, 시드(`ProviderModelPriceSeed`) |
| `consumer` | RabbitMQ에서 `UsageRecordedEvent` JSON 수신·역직렬화 |
| `service` | 비용 계산(`ExpenditureCostCalculator`), 이벤트 처리·집계(`BillingRecordedService`, `BillingAggregationJdbc`), 지출 조회(`ExpenditureQueryService`) |
| `repository` / `domain` | JPA 엔티티·리포지토리 (`provider_model_price`, 집계·멱등 테이블 등) |
| `pricing` | 초기 단가 시드용 `OfficialProviderModelPriceCatalog`(공식 URL·as-of 메타와 금액 스냅샷) |
| `api` | REST `ExpenditureController` 및 DTO |
| `security` | API Gateway 뒤에서 `X-User-Id`·`X-Gateway-Auth`를 신뢰하는 필터(`BillingGatewayTrustFilter`) |
| `integration` | Identity 월 예산 조회용 HTTP 클라이언트(`IdentityBudgetClient`, 기능 플래그로 비활성 가능) |
| `schedule` | 월별 집계 확정 스케줄러(`MonthlyExpenditureFinalizeScheduler`, KST 기준 매월 1일) |

---

## 4) 기능 요약

### 4.1 사용량 이벤트 소비·비용 계산·집계

- **입력**: RabbitMQ에서 `UsageRecordedEvent` 수신 (`BillingUsageRecordedEventListener`).
- **단가**: `ProviderModelPriceRepository.findActivePrices`로 `provider` + `model` + 시각에 맞는 `provider_model_price` 행 조회. 테이블이 비어 있으면 기동 시 `ProviderModelPriceSeed`가 `OfficialProviderModelPriceCatalog` 기준으로 초기 행 삽입.
- **비용**: `ExpenditureCostCalculator.compute` — prompt/completion 토큰 × 입·출력 USD/1M 토큰(합산). 단가 행이 없으면 비용 `0`.
- **토큰 정규화**: prompt/completion이 비어 있고 total만 있으면 절반씩 나누어 추정(`normalizeTokens`).
- **집계**: `BillingAggregationJdbc`로 `daily_expenditure_agg`, `monthly_expenditure_agg` upsert, `billing_user_api_key_seen`에 API Key 노출 정보 반영.
- **멱등**: `billing_processed_event`로 `eventId` 단위 중복 처리 방지.

### 4.2 지출 조회 HTTP API

- **베이스 경로**: `/api/v1/expenditure`
- **엔드포인트(요약)**:
  - `GET /summary` — 기간 합계 + (선택) Identity 월 예산
  - `GET /daily` — 일별 비용 시계열
  - `GET /monthly` — 월별 비용 시계열(확정 여부 포함)
  - `GET /api-keys` — 사용자가 본 API Key 목록(공급사 필터 선택)
- **인증**: Gateway가 넘기는 `X-User-Id` 필수; `billing.gateway.shared-secret`이 설정된 경우 `X-Gateway-Auth` 일치 필요.

### 4.3 월 집계 확정

- 매월 1일 00:00 KST에 전월 `monthly_expenditure_agg` 행을 확정 처리(`is_finalized` 등).

### 4.4 웹(BFF)

- `billing-service/web`은 지출 대시보드 UI 및 BFF 패턴으로 Gateway의 billing API를 호출하는 구성을 따른다(세부는 해당 `web` 디렉터리 및 팀 문서 참고).

### 4.5 이벤트 처리 파이프라인 상세 (`BillingRecordedService`)

다음 순서로 `UsageRecordedEvent` 한 건을 처리한다.

| 단계 | 동작 |
|------|------|
| 멱등 | `billing_processed_event`에 동일 `eventId`가 이미 있으면 **즉시 반환**(집계·비용 재적용 없음). |
| 실패 요청 | `requestSuccessful != true`이면 집계 없이 처리 레코드만 남기고 종료. |
| 사용자/키 누락 | `userId` 또는 `apiKeyId`가 비어 있으면 집계 스킵, 처리 레코드만 저장. |
| Provider 누락 | `event.provider()`가 null이면 집계 스킵. |
| 모델 결정 | `event.model()`이 있으면 사용, 없으면 `tokenUsage.model()` 사용. 둘 다 없으면 집계 스킵. |
| 집계 일자 | `occurredAt`을 **Asia/Seoul**로 변환한 **로컬 날짜**를 일 집계 키로 사용. 월 집계는 그 날짜가 속한 달의 **1일**(`monthStart`). |
| 단가 조회 | `ProviderModelPriceRepository.findActivePrices(provider, model, occurredAt, PageRequest(0,1))` — `validFrom <= occurredAt`이고 `validTo`가 null이거나 `> occurredAt`인 행 중 **가장 최근 `validFrom`**. 없으면 비용 **0**. |
| 비용·토큰 | `ExpenditureCostCalculator.compute` 및 `normalizeTokens` 결과로 일·월 upsert. |
| API Key 시드 | `billing_user_api_key_seen`에 `(userId, apiKeyId, provider)`별 **최초 관측 시각** 유지(`LEAST`로 더 이른 시각 보존). |
| 완료 | `billing_processed_event`에 `eventId` 저장. |

### 4.6 비용 계산 상세 (`ExpenditureCostCalculator`)

- **입력·출력 단가**: `provider_model_price`의 `input_usd_per_million_tokens`, `output_usd_per_million_tokens`를 사용한다(통화 USD, 1M 토큰당).
- **공식**: `promptTokens/1_000_000 * inputRate + completionTokens/1_000_000 * outputRate`, 각 항은 **소수 10자리 HALF_UP**.
- **토큰 부재 시**: prompt·completion이 모두 0이고 `totalTokens`만 있으면 절반씩 배분해 추정.
- **단가 없음**: `ProviderModelPriceEntity`가 null이면 비용 **0**(집계 행에는 비용 0·토큰은 그대로 누적 가능).

### 4.7 지출 조회 API 상세 (`ExpenditureController` / `ExpenditureQueryService`)

공통: 요청자는 `BillingGatewayTrustFilter`가 `request` 속성 `billing.authenticatedUserId`에 넣은 **문자열 userId**(헤더 `X-User-Id` 기반). 조회는 **해당 userId + 쿼리의 `apiKeyId` + (일부에) provider** 스코프로만 수행된다.

| 메서드·경로 | 쿼리 파라미터 | 동작 |
|-------------|---------------|------|
| `GET /api/v1/expenditure/summary` | `apiKeyId`, `provider`, `from`, `to` (ISO 날짜) | 일별 집계 테이블에서 기간 **총 비용 USD** 합산. 선택적으로 Identity에서 **월 예산 USD**를 붙임(`ExpenditureSummaryResponse`). |
| `GET /api/v1/expenditure/daily` | 동일 | 일자별 `totalCostUsd` 시계열(`DailyExpenditurePoint`). |
| `GET /api/v1/expenditure/monthly` | `apiKeyId`, `from`, `to` (**provider 없음**) | 월 시작일(`month_start_date`)별 누적 비용 + `isFinalized` 플래그(`MonthlyExpenditurePoint`). |
| `GET /api/v1/expenditure/api-keys` | `provider` 선택 | `billing_user_api_key_seen`에서 해당 사용자의 API Key·provider·`firstSeenAt` 목록. provider 생략 시 전체. |

**기간 제한**: `from`~`to` 포함 일수가 `billing.analytics.max-range-days`(기본 400)를 넘으면 `IllegalArgumentException` → HTTP 400.

**예산 연동**: `billing.identity.enabled=true`이고 `base-url`·`budget-path-template`이 유효할 때만 `IdentityBudgetClient`가 GET으로 JSON을 읽고, 루트에 `monthlyBudgetUsd` 필드가 있으면 요약에 포함. 404·비활성·오류 시 예산 필드는 null에 가깝게 동작(클라이언트는 empty optional).

### 4.8 스케줄러 (`MonthlyExpenditureFinalizeScheduler`)

- **Cron**: `0 0 0 1 * ?`, **zone `Asia/Seoul`** — 매월 1일 00:00 KST.
- **동작**: “직전 달”의 `month_start_date`에 해당하는 `monthly_expenditure_agg` 행 중 `is_finalized = false`인 행에 대해 `is_finalized = true`, `finalized_at = now` 일괄 업데이트.
- **의미**: 확정된 달은 이후 일별 upsert로 **월 합계가 더해지지 않음**(`BillingAggregationJdbc.upsertMonthly`의 `CASE WHEN is_finalized` 분기).

### 4.9 초기 단가 시드 (`ProviderModelPriceSeed` / `OfficialProviderModelPriceCatalog`)

- 애플리케이션 기동 시 `provider_model_price` **행 수가 0**일 때만 카탈로그 행을 INSERT.
- 금액·모델 ID·공식 URL·as-of는 카탈로그 클래스 및 `private-docs` 설계 문서에 정의된 스냅샷을 따른다(런타임 크롤링 없음).

### 4.10 Next.js BFF (`services/billing-service/web`)

- **라우트**: `app/api/expenditure/[[...path]]/route.ts` — 브라우저의 `/api/expenditure/*`를 **API Gateway**의 `/api/v1/expenditure/*`로 프록시.
- **인증**: 쿠키 `access_token`을 `Authorization: Bearer`로 전달.
- **게이트웨이 개발 모드**(`GATEWAY_DEV_MODE`): Identity `GET /api/auth/session`으로 이메일 등을 받아 **`X-User-Id`**를 세팅(운영에서는 Gateway가 사용자 식별 헤더를 붙이는 패턴).
- **환경 변수**: `API_GATEWAY_URL` 필수, 개발 모드 시 `IDENTITY_SERVICE_URL` 필수.

### 4.11 기타 런타임 구성

- **Jackson**: `BillingJacksonConfiguration`에서 `ObjectMapper`에 `JavaTimeModule` 등 자동 등록(`findAndRegisterModules`).
- **Rabbit 리스너**: JSON 문자열을 `UsageRecordedEvent`로 역직렬화 실패 시 로그 후 예외 전파(재시도·DLQ 정책은 브로커·리스너 팩토리 설정에 따름 — 기본 앱 코드에는 DLQ 빈이 없음).
- **헬스/정보**: Actuator `health`, `info` 노출.

---

## 5) 데이터베이스 구조 (`billing_db`)

스키마는 **JPA 엔티티 + `ddl-auto: update`** 로 생성·변경된다. 아래는 현재 코드 기준 **논리 모델**이다.

### 5.1 `provider_model_price`

| 컬럼 | 타입(개념) | 설명 |
|------|------------|------|
| `id` | BIGSERIAL PK | 대리 키 |
| `provider` | VARCHAR | `AiProvider` enum 문자열 (`OPENAI`, `ANTHROPIC`, `GOOGLE`) |
| `model` | VARCHAR(512) | 이벤트의 모델 문자열과 **완전 일치**해야 매칭 |
| `valid_from` | TIMESTAMP | 단가 유효 시작(포함) |
| `valid_to` | TIMESTAMP, nullable | 유효 종료(미포함); null이면 현재 유효 |
| `input_usd_per_million_tokens` | DECIMAL(24,10) | 입력 토큰 1M당 USD |
| `output_usd_per_million_tokens` | DECIMAL(24,10) | 출력 토큰 1M당 USD |

**조회**: `findActivePrices`는 동일 provider·model에 대해 시각 조건을 만족하는 행을 `validFrom` 내림차순으로 가져오고, 애플리케이션에서 첫 행만 사용한다.

### 5.2 `daily_expenditure_agg`

| 컬럼 | 설명 |
|------|------|
| PK (`agg_date`, `user_id`, `api_key_id`, `provider`, `model`) | KST 기준 일자 + 사용자 + API Key + 공급사 + 모델 |
| `total_cost_usd` | 누적 USD 비용 |
| `total_prompt_tokens` | 누적 prompt 토큰 |
| `total_completion_tokens` | 누적 completion 토큰 |

**갱신**: `INSERT ... ON CONFLICT DO UPDATE`로 비용·토큰 **누적 합산**.

### 5.3 `monthly_expenditure_agg`

| 컬럼 | 설명 |
|------|------|
| PK (`month_start_date`, `user_id`, `api_key_id`) | 해당 월의 1일(로컬 날짜) + 사용자 + API Key |
| `total_cost_usd` | 월 누적 USD |
| `is_finalized` | 월 확정 여부 |
| `finalized_at` | 확정 시각 |

**갱신**: 미확정 월에만 비용 delta 합산; 확정 후에는 일별 upsert가 월 합계를 바꾸지 않음.

### 5.4 `billing_processed_event`

| 컬럼 | 설명 |
|------|------|
| `event_id` (PK, UUID) | `UsageRecordedEvent.eventId` — 멱등 키 |
| `processed_at` | 처리 완료 시각 |

### 5.5 `billing_user_api_key_seen`

| 컬럼 | 설명 |
|------|------|
| PK (`user_id`, `api_key_id`, `provider`) | 드롭다운용 “이 사용자가 본 키” |
| `first_seen_at` | 최초 관측 시각(충돌 시 더 이른 시각 유지) |

**UNIQUE 제약**: JDBC upsert의 `ON CONFLICT (user_id, api_key_id, provider)`와 대응한다.

---

## 6) 인프라

### 6.1 PostgreSQL (`billing_db`)

- **용도**: billing 전용 DB. 타 서비스 DB에 JDBC로 접근하지 않는다(`docs/msa-database-and-service-integration.md` 원칙).
- **로컬**: 루트 `docker-compose.yml`의 `postgres-billing`(기본 호스트 포트 **5435**), 초기화 스크립트 `docker/postgres/init/03-create-billing-db.sh`.
- **연결 설정**: `BILLING_POSTGRES_*` 및 `application.yml`의 `spring.datasource` (기본 DB명 `billing_db`).
- **스키마**: JPA `ddl-auto: update`로 엔티티 기준 스키마 진화(운영에서는 마이그레이션 전략을 팀 표준에 맞출 것).

### 6.2 RabbitMQ

- **소비**: Topic 교환기 `usage.events`, 라우팅 키 `usage.recorded`, 큐 `billing-service.queue`에 바인딩 (`BillingRabbitConfiguration`, `billing.rabbit.*` 설정).
- **발행**: 현재 구현에서는 **billing-service가 RabbitMQ로 메시지를 발행하지 않는다**(부록 A 참고).

### 6.3 기타

- **HTTP 포트**: `application.yml` 기본값은 **8095** (`BILLING_SERVICE_PORT`) — `api-gateway-service`의 **`GATEWAY_BILLING_URI`** 기본(`http://localhost:8095`)과 맞춤. team-service는 기본 **8093**이라 동시에 예전처럼 둘 다 8093을 쓰던 충돌은 피합니다. **실제 기동 포트**를 바꾼 경우 Gateway·Compose의 billing URI를 동일하게 맞춥니다.
- **헬스**: `management.endpoints.web.exposure.include: health,info`.

### 6.4 로컬 체크리스트: `GET /api/v1/expenditure/api-keys` 500·502·연결 문제

**정상 동작**: 해당 경로는 DB에 해당 사용자의 `billing_user_api_key_seen` 행이 없으면 **HTTP 200 + `[]`** 입니다. **HTTP 500**은 애플리케이션 예외(예: 미처리 DB 오류)로, **대부분 인프라·설정 불일치**에서 발생합니다. 아래를 같은 요청 ID(`X-Correlation-Id`)로 **billing-service 로그**와 맞춰 확인합니다.

| 점검 | 내용 |
|------|------|
| **PostgreSQL (billing)** | 루트 `docker compose`로 `postgres-billing` 기동. 호스트 포트 기본 **5435**, DB명 **`billing_db`** (`docker/postgres/init/03-create-billing-db.sh`). |
| **JDBC 환경 변수** | `.env`(또는 실행 환경)의 `BILLING_POSTGRES_HOST` / `PORT` / `DB` / `USER` / `PASSWORD`가 `application.yml`의 `spring.datasource`와 일치하는지 확인. 잘못된 포트·DB명은 연결 거부 또는 잘못된 DB 접속으로 이어짐. |
| **스키마** | 로컬은 `ddl-auto: update`로 엔티티 기준 스키마가 맞춰짐. 수동으로 깨진 스키마·권한이 있으면 기동 로그 또는 첫 쿼리에서 `DataAccessException`이 남음. |
| **billing 포트 ↔ Gateway** | `GATEWAY_BILLING_URI`가 **실제 billing `server.port`**와 같아야 함. 저장소 기본은 둘 다 **8095**(`localhost` 호스트 / Compose 게이트웨이는 `host.docker.internal:8095`). |
| **Gateway ↔ billing 신뢰 헤더** | `BillingGatewayTrustFilter`: `X-User-Id` 필수. `GATEWAY_SHARED_SECRET`이 비어 있지 않으면 **`X-Gateway-Auth`** 가 billing의 `billing.gateway.shared-secret`과 일치해야 함(`.env.example` 기본값 동일). 불일치 시 billing은 **403**, 헤더 누락 시 **401**. |
| **지출 웹 BFF** | `services/billing-service/web`은 `API_GATEWAY_URL`로 Gateway만 호출. **`API_GATEWAY_URL` 미설정** 시 BFF가 **500**을 반환할 수 있음(앱 설정 문제). `GATEWAY_DEV_MODE` 사용 시 `IDENTITY_SERVICE_URL` 등 추가 요구사항은 `§4.10` 참고. |

**런타임 개선(선택)**: `DataAccessException`은 HTTP **503**과 JSON 본문(`error: DATABASE_UNAVAILABLE`, 선택적 `hint`)으로 매핑되며, 원인은 서버 로그에 스택으로 남깁니다. 운영에서 힌트를 끄려면 `BILLING_EXPOSE_DATASOURCE_FAILURE_HINT=false` 또는 `billing.error.expose-datasource-failure-hint: false` 를 사용합니다.

**한 줄 검증**(저장소 루트, billing·(게이트웨이 ②번은) api-gateway 기동 후): `.\scripts\verify-expenditure-chain.ps1` 또는 `./scripts/verify-expenditure-chain.sh`

| 단계 | 내용 |
|------|------|
| **① 직접 billing** | 항상 실행: `X-User-Id` + `X-Gateway-Auth` → HTTP **200** (빈 목록이면 `[]`). |
| **② 게이트웨이** | **`GATEWAY_DEV_MODE=true`**(또는 미설정): `X-User-Id` 만으로 게이트웨이 경유 검사(UI dev 모드와 유사). |
| **② 게이트웨이 (JWT)** | **`GATEWAY_DEV_MODE=false`**: 브라우저/BFF와 동일하게 **`Authorization: Bearer`** 가 필요. 스크립트는 아래 중 하나면 ②를 수행한다. 없으면 ②는 **SKIPPED**(①만으로도 billing·DB·포트·`GATEWAY_BILLING_URI` 직접 정합은 확인됨). |

JWT 경로를 켜려면(`.env` 권장, **비밀번호는 커밋하지 말 것**):

- **`EXPENDITURE_VERIFY_GATEWAY_JWT`**: 이미 가진 액세스 토큰 문자열(로컬 로그인 후 복사 등).
- 또는 **`EXPENDITURE_VERIFY_LOGIN_EMAIL`** + **`EXPENDITURE_VERIFY_LOGIN_PASSWORD`**: identity `POST /api/auth/login`으로 토큰 획득(기본 베이스 URL `IDENTITY_SERVICE_URL` 또는 `EXPENDITURE_VERIFY_IDENTITY_URL`, 기본 `http://127.0.0.1:8090`).

**필수 정합**: 게이트웨이가 JWT를 검증하려면 **`GATEWAY_JWT_SECRET`**(게이트웨이 `gateway.jwt.secret`)과 identity 서명 키 **`JWT_SECRET`**(`security.jwt.secret`)이 **동일한 값**이어야 한다(`.env.example`의 `GATEWAY_JWT_SECRET` / `JWT_SECRET` 참고). 불일치 시 ②에서 **401**이 난다.

**직접 검증 예시**(로컬 billing이 8095이고 공유 시크릿이 기본인 경우):

```http
GET /api/v1/expenditure/api-keys
X-User-Id: <테스트 사용자 ID>
X-Gateway-Auth: local-dev-gateway-shared-secret-do-not-use-in-prod
```

---

## 7) 연관 서비스와의 연동 (상세)

### 7.1 `proxy-service` (업스트림 이벤트 생산자)

| 항목 | 내용 |
|------|------|
| **역할** | AI 업스트림 호출 후 `UsageRecordedEvent`를 **발행**한다(`UsageEventPublisher`). |
| **교환기·라우팅** | `usage.events` + `usage.recorded` — **billing Rabbit 설정과 동일한 문자열**이어야 소비 큐에 메시지가 도달한다. |
| **페이로드** | `libs/usage-events`의 `UsageRecordedEvent`: `eventId`, `userId`, `apiKeyId`, `provider`, `model`, `tokenUsage`, `estimatedCost`(현재 **항상 0**), 성공 여부 등. |
| **직접 연결** | billing은 proxy를 HTTP로 호출하지 않는다. **브로커를 통한 단방향 이벤트 흐름**만 존재한다. |

### 7.2 `usage-service` (동일 이벤트의 다른 소비자)

| 항목 | 내용 |
|------|------|
| **공통 이벤트** | 동일 `UsageRecordedEvent` 스트림을 usage 쪽에서도 구독해 **원천 로그** 등을 적재할 수 있다(usage 리스너·큐는 usage 모듈 설정에 따름). |
| **DB** | `usage_db`는 billing이 **접근하지 않는다**. 비용 숫자를 usage 로그와 맞추려면 부록 A의 **비용 확정 이벤트** 같은 별도 계약이 필요하다. |
| **직접 연결** | 기본 구현에서 billing → usage **동기 HTTP 호출은 없음**. |

### 7.3 `api-gateway-service` (지출 HTTP 진입점)

| 항목 | 내용 |
|------|------|
| **라우팅** | `Path=/api/v1/expenditure/**` → `GATEWAY_BILLING_URI`(기본 `http://localhost:8095`; Docker Compose 게이트웨이는 기본 `http://host.docker.internal:8095`). |
| **보안** | Spring Security에서 expenditure 경로는 인증 정책에 포함되며, **게이트웨이가 내부 헤더를 세팅**하는 패턴을 쓴다. |
| **billing 측 검증** | `BillingGatewayTrustFilter`: `/api/v1/expenditure`만 대상. `billing.gateway.shared-secret`이 비어 있지 않으면 **`X-Gateway-Auth`** 가 일치해야 하고, **`X-User-Id`** 가 필수. |

### 7.4 `identity-service` (선택 — 월 예산)

| 항목 | 내용 |
|------|------|
| **설정** | `billing.identity.enabled`, `billing.identity.base-url`, `billing.identity.budget-path-template` (`application.yml` / env). |
| **호출** | `IdentityBudgetClient`가 `RestClient`로 GET; 경로에 `{userId}` 치환을 지원. 응답 JSON에서 `monthlyBudgetUsd`만 매핑(`BudgetEnvelope`). |
| **실패 시** | 404 및 기타 오류는 **예산 없음**으로 취급(지출 합계 API는 계속 동작). |
| **용도** | `GET /expenditure/summary` 응답에 **예산 vs 지출** 표시를 풍부히 하기 위한 선택적 연동이다. |

### 7.5 `billing-service/web` (Next.js)

| 항목 | 내용 |
|------|------|
| **역할** | 브라우저 → **BFF** → API Gateway → billing Spring. 쿠키의 액세스 토큰을 Gateway로 넘긴다. |
| **개발 모드** | `GATEWAY_DEV_MODE` 시 Gateway 대신 **직접 `X-User-Id`**를 붙이기 위해 Identity 세션 API를 호출한다. |

### 7.6 공유 라이브러리 `libs/usage-events`

| 항목 | 내용 |
|------|------|
| **계약** | `UsageRecordedEvent`, `TokenUsage`, `AiProvider` 등이 **프록시·billing·usage** 간 JSON 직렬화 호환을 위해 공유된다. |
| **버전** | 필드 추가 시 모든 발행·소비자의 Jackson 설정이 호환되는지 확인해야 한다(`fail-on-unknown-properties: false`는 billing 측 `application.yml`에 있음). |

---

## 8) 통신 방식 (요약 표)

| 방향 | 방식 | 설명 |
|------|------|------|
| **인바운드 (비동기)** | **AMQP 구독** | `UsageRecordedEvent` JSON → 비용·집계 처리 |
| **인바운드 (동기)** | **HTTP** | 지출 조회 REST; 클라이언트는 보통 **API Gateway** 경유 |
| **아웃바운드 (동기, 선택)** | **HTTP** | `BILLING_IDENTITY_ENABLED=true`일 때 Identity에서 월 예산 USD 조회 |
| **아웃바운드 (비동기)** | **AMQP 발행** | `UsageCostFinalizedEvent` — `billing.events` / `usage.cost.finalized` (usage-service가 소비해 `usage_recorded_log.estimated_cost` 갱신). 상세: 부록 A, `docs/billing-pricing-catalog-ops.md`, `docs/billing-identity-budget.md`. |

### 8.1 API Gateway·헤더

- `ProxyTrustHeadersWebFilter` 등에서 expenditure 경로가 게이트웨이 신뢰 헤더 처리 대상에 포함된다.
- Billing 앱은 **Gateway 뒤에서만** 지출 API를 노출한다고 가정하고 `X-User-Id`를 신뢰한다(운영에서는 네트워크 격리·공유 시크릿으로 보완).

### 8.2 이벤트 페이로드

- 소비 메시지 타입: `libs/usage-events`의 `UsageRecordedEvent`.
- Proxy는 `estimatedCost`를 `0`으로 보내는 현행 정책이며, **실제 USD 비용은 billing 측에서 단가 적용 후 집계**된다.

---

## 9) 문서·연계 참고

- MSA DB·연동 원칙: `docs/msa-database-and-service-integration.md`
- 저장소 레이아웃: `docs/repository-structure.md`
- Gateway·프록시 계약: `docs/contracts/gateway-proxy.md` (공유 시크릿 등)

---

## 부록 A) `usage-service-cost-dashboard-billing-proxy-requirements-20260412.md`와 RabbitMQ **비용 이벤트 발행**

아래는 **`private-docs/architecture-report/usage-service-cost-dashboard-billing-proxy-requirements-20260412.md`** 의 §4(정본 아키텍처)를 기준으로, **billing-service가 앞으로 RabbitMQ에 발행해야 할 비용 이벤트**를 정리한 것이다.  
**현재 코드베이스에서는 해당 발행자가 아직 구현되어 있지 않으며**, 큐 이름·교환기·스키마는 요구사항 문서에 따라 구현 단계에서 확정한다.

### A.1 요구사항이 말하는 흐름

1. Proxy는 `UsageRecordedEvent`의 `estimatedCost`를 **0**으로 유지한다.
2. Billing은 동일 사용 이벤트(또는 동일 `eventId`를 가진 맥락)에 대해 **단가를 적용해 추정 비용**을 산출한다.
3. Billing은 그 결과를 담은 **비용 확정(또는 보정) 이벤트**를 RabbitMQ에 **발행**한다.
4. Usage-service가 이를 **소비**하여 `usage_db`의 `usage_recorded_log`(또는 동등 모델)의 **`estimated_cost`를 업데이트**한다.

이렇게 하면 Usage 대시보드·로그 조회가 **자신의 DB**만으로 빠르게 응답하고, Billing 일시 장애 시에도 이미 채워진 비용을 활용하기 쉽다(요구 문서 §4.1).

### A.2 billing-service가 발행해야 하는 이벤트(의미·필수 정보)

**목적**: “이 `eventId`(또는 동일한 멱등 키)에 대해 billing이 산출한 **최종 추정 비용(USD)** 이 무엇인지”를 usage-service에 전달한다.

권장으로 포함할 **최소·권장 필드**(이름은 구현 시 `libs/` 이벤트 모듈에 record/DTO로 고정):

| 구분 | 필드(개념) | 설명 |
|------|-------------|------|
| **필수** | **멱등 키** | 원천 `UsageRecordedEvent`와 동일한 **`eventId`** (또는 요구사항에서 합의한 단일 키). usage 쪽 로그 행과 1:1로 매칭 가능해야 한다. |
| **필수** | **추정 비용** | billing이 계산한 **`estimatedCost`** 에 해당하는 금액(USD, `BigDecimal` 등). usage DB의 `estimated_cost` 보정값. |
| **권장** | **발행 시각** | 처리 완료 시각(감사·재처리 구분). |
| **권장** | **사유/버전** | 단가 스냅샷·규칙 버전(재처리·백필 시 디버깅). |
| **선택** | **토큰·모델** | 이미 usage에 있으면 중복일 수 있으나, 재처리 검증용으로 선택적 포함 가능. |

**라우팅(개념)**:

- 별도 **교환기/라우팅 키/큐**를 둔다(예: `billing.cost.confirmed` 등).  
- `usage.events` / `usage.recorded` 와 **분리**하는 것이 일반적이다(소비자 혼동·재처리 루프 방지).

### A.3 현재 구현과의 차이

| 항목 | 현재 구현 | 요구사항 §4 |
|------|-----------|-------------|
| RabbitMQ | 소비(`UsageRecordedEvent`) + **발행**(`UsageCostFinalizedEvent` → `billing.events` / `usage.cost.finalized`) | 동일 |
| Usage DB `estimated_cost` | usage-service 리스너가 **소비 후 업데이트** | 동일 |

구현 세부: `libs/usage-events`의 `UsageCostFinalizedEvent`, billing `UsageCostFinalizedEventPublisher`, usage `UsageCostFinalizedEventListener`.

---

본 문서는 구현이 바뀔 때마다 날짜·본문을 갱신하는 것을 권장한다.
