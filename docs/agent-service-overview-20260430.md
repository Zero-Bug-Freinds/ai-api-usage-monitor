# Agent Service Overview (2026-04-30)

`agent-service`는 RabbitMQ 이벤트를 기반으로 개인/팀 API Key와 과금 신호를 스냅샷으로 유지하고, 이를 바탕으로 정책 추천/예산 소진 예측 API를 제공하는 보조 서비스다.

## 1. 책임과 경계

- 위치: `services/agent-service` (Spring Boot), `services/agent-service/web` (Next.js BFF/UI)
- 데이터 경계: 타 서비스 DB를 직접 조회하지 않는다. 기본은 RabbitMQ 이벤트 스냅샷을 사용하고, 웹 BFF에서 필요한 최소 범위의 내부 HTTP 조회(Team/Identity)를 fallback으로 사용한다.
- 주요 역할:
  - 이벤트 스냅샷 조회 API 제공
  - 예산 소진 예측 어시스턴트 API 제공
  - 웹에서 사용할 컨텍스트 합성 BFF 제공

## 2. 백엔드 API

기본 베이스 경로는 `http://<agent-service>/api/v1/agents` 이다.

- `POST /policy-recommendations`
  - 입력: `PolicyRecommendationRequest`
  - 출력: `PolicyRecommendationResponse`
- `POST /policy-recommendations/analyze`
  - 입력: `RecommendationAnalyzeRequest`
  - 출력: `OptimizationRecommendationIssuedEvent`
  - 동작: 키/스코프 기준으로 최근 패턴을 분석하고 모델 최적화 추천 결과를 생성·캐시한다.
- `GET /policy-recommendations/{keyId}?scopeType={PERSONAL|TEAM}&scopeId={id}`
  - 출력: `RecommendationQueryResponse`
  - 동작: 캐시된 추천 결과(근거 지표/후보 모델/절감률)를 조회한다.
- `POST /budget-forecast-assistant`
  - 입력: `BudgetForecastRequest`
  - 출력: `BudgetForecastResponse`
  - 동작: Gemini 예측 결과를 우선 사용하며, AI 결과 생성 실패 시 서비스 내부의 수식 기반 fallback 결과를 반환한다.
- `GET /identity-api-keys`
  - 설명: 전체 개인 API Key 스냅샷 조회
- `GET /identity-api-keys/{userId}`
  - 설명: 사용자별 개인 API Key 스냅샷 조회
- `GET /team-api-keys?teamId={id}`
  - 설명: 팀 API Key 스냅샷 조회
- `GET /billing-signals?teamId={id}`
  - 설명: 비용 최종화/예산 임계 이벤트 기반 신호 조회
- `GET /usage-prediction-signals?teamId={id}`
  - 설명: `usage.prediction.signals` 기반 일평균 지출/토큰 신호 조회
- `GET /debug/events?limit={n}`
  - 설명: 최근 수신 이벤트 디버그 조회
- `GET /model-catalog`
  - 출력: `CatalogSnapshot`
  - 동작: 추천 엔진이 사용하는 모델 단가 카탈로그(현재 스냅샷, 마지막 갱신 시각/성공 여부)를 조회한다.

## 3. RabbitMQ 소비 이벤트

현재 `agent-service`는 아래 이벤트를 소비한다.

- Identity 이벤트(`identity.events` 계열)
  - `EXTERNAL_API_KEY_STATUS_CHANGED`
  - `EXTERNAL_API_KEY_DELETED`
  - `EXTERNAL_API_KEY_BUDGET_CHANGED`
  - `USER_CONTEXT_CHANGED`
- Team 이벤트
  - `team.api.key.status.changed`
- Billing 이벤트(`billing.events`)
  - `usage.cost.finalized`
  - `billing.budget.threshold.reached`
  - `billing.cost.corrected`
- Usage 이벤트(`usage.events`)
  - `usage.prediction.signals`
  - `usage.daily.cumulative.tokens`
  - `usage.recorded`

구체 큐/라우팅키는 `services/agent-service/src/main/resources/application.properties`를 따른다.

## 4. 웹(BFF) 구성

웹 basePath는 `/agent`이며 Next Route Handler가 백엔드 API를 중계/합성한다.

- `GET /agent/api/v1/agents/available-context`
  - 개인 API Key(alias 중심) + 팀 API Key + Billing Signal + User Context를 합쳐 UI에 전달
  - 개인 API Key는 우선 agent 이벤트 스냅샷을 사용하고, 비어 있을 때 Identity 예산 API(`/api/identity/v1/users/{userId}/budget` 또는 이메일 기반 `/api/identity/v1/users/budget`)를 fallback으로 조회한다.
  - **Billing 웹(지출) 키별 요약과 동일한 기준으로 예산 사용률을 맞춘다.** 키마다 `billing-service`의 `GET /api/v1/expenditure/summary`를 호출해, 응답의 **`totalCostUsd`와 `monthlyBudgetUsd`**를 사용한다(기간: **Asia/Seoul** 기준 이번 달 1일 ~ 오늘, `services/billing-service/web/src/lib/expenditure/dates.ts`의 `currentMonthRangeKst`와 동일한 의도). `billing-service` 수정 없이 공개 API만 사용한다.
  - 지출은 이벤트 스냅샷(`billing-signals`의 `latestEstimatedCostUsd`)과 위 summary의 `totalCostUsd` 중 **큰 값**을 취해, 실시간 이벤트와 집계 DB가 어긋날 때도 보수적으로 표시한다.
  - 내부 HTTP 호출 시 원 요청의 `Authorization`·`Cookie`·`x-user-id`·`x-user-email`을 전달하고, billing direct 호출에는 **`X-Gateway-Auth`**(`GATEWAY_SHARED_SECRET`, BFF 기본값은 로컬 개발용 시크릿)를 붙인다.
  - 로그인 세션(`/api/auth/session`)에서 이메일을 파싱해 내부 호출 시 `x-user-email` 헤더로 전달하고, 식별 헤더(`x-user-id`)가 없으면 `AI_AGENT_FALLBACK_USER_ID`를 사용한다.
  - 개인 키 스냅샷 조회는 숫자 `userId` 헤더가 있으면 `GET /api/v1/agents/identity-api-keys/{userId}`로 한정해, 타 사용자 키와 섞이지 않도록 한다.
  - Team 서비스 조회 결과가 비어 있으면 다음 사용자 식별자 후보(헤더/세션/환경값)를 순차 시도한다.
  - 팀명은 괄호 메타정보를 제거한 표시명으로 정규화한다. 예: `Platform Team (T-12)` -> `Platform Team`
- `POST /agent/api/v1/agents/budget-forecast-assistant`
  - 백엔드 `budget-forecast-assistant` API 프록시
  - 세션 이메일을 파싱해 내부 호출 시 `x-user-email` 헤더로 전달한다.
  - 내부 호출 타임아웃은 20초로 설정되어 Gemini 응답 지연 시 조기 실패를 줄인다.
- `POST /agent/api/v1/agents/policy-recommendations/analyze`
  - 백엔드 `policy-recommendations/analyze` API 프록시
  - 개인/팀 키 분석 실행 시 추천 캐시를 생성한다.
- `GET /agent/api/v1/agents/policy-recommendations/{keyId}?scopeType=...&scopeId=...`
  - 백엔드 추천 조회 API 프록시
  - UI 카드에 추천 신뢰도/절감률/근거 지표를 렌더링할 때 사용한다.
- `GET /agent/api/v1/agents/model-catalog`
  - 백엔드 `model-catalog` API 프록시
  - UI에서 현재 추천 단가 기준(모델별 input/output 단가, 소스, 마지막 갱신 상태) 표시 시 사용한다.

웹 UI에서 API Key별 "다음 결제일"은 브라우저 `localStorage`에 저장한다.

- 저장 키(prefix): `agent.manualBillingCycleEnd.`
- 개인 키: `agent.manualBillingCycleEnd.personal.{keyId}`
- 팀 키: `agent.manualBillingCycleEnd.team.{teamId}.{teamApiKeyId}`
- DB 저장/동기화 없이 현재 브라우저에서만 유지된다.

내부 백엔드 오리진은 `AI_AGENT_SERVICE_INTERNAL_ORIGIN` 환경변수로 지정한다.

## 5. 환경변수 요약

- 포트/LLM
  - `AI_AGENT_SERVICE_PORT` (default: `8096`)
  - `AI_AGENT_GEMINI_API_KEY`
  - `AI_AGENT_GEMINI_MODEL` (default: `gemini-2.5-flash`)
  - `AI_AGENT_GEMINI_BASE_URL`
- RabbitMQ
  - `SPRING_RABBITMQ_HOST`, `SPRING_RABBITMQ_PORT`
  - `SPRING_RABBITMQ_USERNAME`, `SPRING_RABBITMQ_PASSWORD`
  - `AI_AGENT_RABBIT_*` 계열(Identity/Team/Billing queue, exchange, routing key, enabled)
- Redis (billing-signals 영속화)
  - `SPRING_REDIS_HOST` / `REDIS_HOST` (default: 컨테이너 네트워크에서는 `redis`)
  - `SPRING_REDIS_PORT` / `REDIS_PORT` (default: `6379`)
- Billing 스냅샷 보정 (HTTP, billing-service 수정 없음)
  - `ai-agent.billing-reconcile.*` (`application.properties` 참고)
  - `AI_AGENT_BILLING_RECONCILE_ENABLED` (default: `true`)
  - `AI_AGENT_BILLING_BASE_URL` (default: `http://billing-service:8095`)
  - `GATEWAY_SHARED_SECRET` (billing expenditure API 신뢰 헤더, BFF·reconcile 공통)
  - `AI_AGENT_BILLING_RECONCILE_INITIAL_DELAY_MS`, `AI_AGENT_BILLING_RECONCILE_FIXED_DELAY_MS` (주기 보정 간격)
- Recommendation 모델 단가 카탈로그
  - `AI_AGENT_RECOMMENDATION_CATALOG_URL` (optional, 외부 모델 단가 JSON URL)
  - `AI_AGENT_RECOMMENDATION_CATALOG_REFRESH_MS` (default: `300000`)
  - `AI_AGENT_RECOMMENDATION_CATALOG_REQUEST_TIMEOUT_MS` (default: `5000`)
  - `AI_AGENT_RECOMMENDATION_CATALOG_API_KEY` (optional, 외부 카탈로그 Authorization Bearer)
  - `AI_AGENT_RECOMMENDATION_CATALOG_OPENROUTER_REFERER` (optional, OpenRouter `HTTP-Referer`)
  - `AI_AGENT_RECOMMENDATION_CATALOG_OPENROUTER_TITLE` (optional, OpenRouter `X-Title`)
- Web BFF
  - `AI_AGENT_SERVICE_INTERNAL_ORIGIN`
  - `BILLING_SERVICE_INTERNAL_ORIGIN` (optional, `available-context`에서 summary 호출 시)
  - `IDENTITY_SERVICE_INTERNAL_ORIGIN` (optional, 미지정 시 `localhost:8090` 등 기본 후보 사용)
  - `TEAM_SERVICE_INTERNAL_ORIGIN` (optional)
  - `AI_AGENT_FALLBACK_USER_ID` (optional, 미설정 시 `"1"`)
  - `AI_AGENT_TEAM_CATALOG_USER_ID` (optional override, 기본 미사용)
  - `GATEWAY_SHARED_SECRET` (billing 내부 호출 시 `X-Gateway-Auth`)

## 6. 운영상 주의점

- **Billing 비용 신호(`BillingSignalSnapshotService`)**: 메모리 맵에 더해 **Redis Hash**(`ai-agent:billing-signals`)에 직렬화 저장한다. 재시작 후에도 Redis가 살아 있으면 스냅샷이 복구된다. Redis가 없으면 기존처럼 메모리만 사용한다.
- **보정 잡(`BillingSignalReconciliationService`)**: 애플리케이션 기동 후 및 고정 지연마다, Identity 키 스냅샷에 있는 키에 대해 `billing-service`의 `GET /api/v1/expenditure/summary`를 호출해 비용을 보강한다(기간: **Asia/Seoul** 이번 달 1일 ~ 오늘). 이벤트 유입이 없거나 재시작 직후 `billing-signals`가 비는 구간을 줄인다.
- Identity/팀/기타 스냅샷은 여전히 메모리 기반이라, 해당 컴포넌트는 재시작 시 초기화될 수 있다(이벤트 재유입 또는 BFF fallback에 의존).
- **호스트 포트**: 루트 `docker-compose.yml`에서 알림 서비스가 호스트 `8096`을 쓰는 경우가 있어, `agent-service`는 호스트에 **`8097:8096`**으로 노출되는 구성이 일반적이다. 로컬 점검 시 `http://localhost:8097/api/v1/agents/...`를 사용한다.
- `debug/events`는 운영 환경에서 접근 통제와 민감정보 마스킹 정책을 반드시 적용해야 한다.
- 컨텍스트 집계는 이벤트 도착 순서와 시점에 따라 일시적으로 비어 있을 수 있다.
- 개인 키 이벤트가 아직 유입되지 않은 초기 상태에서는 개인 키 목록이 비어 보일 수 있으며, 이때는 Identity fallback 조회 성공 여부(오리진/포트/권한 헤더)를 함께 점검해야 한다.
- `docker compose --profile web up` 실행 시 `agent-web`은 `agent-service`, `team-service` 의존으로 함께 올라오도록 구성되어야 한다. 내부 오리진 기본값은 컨테이너 DNS(`http://agent-service:8096`)를 사용한다.
- `web-edge`에서 `/agent`, `/agent/`, `/agent/*`는 `agent-web`으로 프록시되어야 한다. 템플릿 변경 후에는 `web-edge` 재기동/재생성이 필요하다.
- 모델 추천 단가 카탈로그 URL을 설정하지 않으면 추천 엔진은 내장 기본 카탈로그(`default`)를 사용한다.
- `GET /model-catalog` 응답의 `source`가 `default`이면 외부 카탈로그 fetch가 실패했거나 URL/인증 헤더가 비어 fallback이 동작한 상태다. 이 경우 추천 후보 provider도 내장 기본 모델 집합 기준으로만 보인다.
- `GET /model-catalog` 응답의 `source`가 `openrouter`(또는 외부 소스명)로 바뀌어야 외부 provider 목록이 반영된 상태다.
- OpenRouter 사용 시 최소 환경변수는 `AI_AGENT_RECOMMENDATION_CATALOG_URL=https://openrouter.ai/api/v1/models`이며, 필요 시 `AI_AGENT_RECOMMENDATION_CATALOG_API_KEY`, `AI_AGENT_RECOMMENDATION_CATALOG_OPENROUTER_REFERER`, `AI_AGENT_RECOMMENDATION_CATALOG_OPENROUTER_TITLE`를 함께 설정한다.
- `agent-web` UI는 현재 모델 카탈로그 섹션에서 `Provider별` 집계 텍스트를 노출하지 않는다(요청에 따라 제거). provider 값은 추천 후보 카드의 모델 라인과 백엔드 `model-catalog` 응답에서 확인한다.
- 현재 추천의 `input/output` 비율은 `usage.recorded` 이벤트의 호출 단위 토큰(`prompt_tokens`/`completion_tokens`)을 7일 롤링으로 집계한 실측치를 우선 사용하고, 데이터가 부족한 경우에만 기존 스냅샷 기반 fallback을 사용한다.

## 7. Billing UI vs Agent UI 예산 사용률

- **Billing 지출 화면 상단(개인)**: `monthly-budget-status` 기준으로 **사용자 전체 지출 합**과 **사용자 단위 월 예산**으로 %를 낸다. 여러 API 키가 있으면 지출은 합산된다.
- **Billing 지출 화면에서 특정 키·프로바이더 선택 후 요약**: `expenditure/summary` 기준으로 **그 키의 지출**과 **그 키의 월 예산**으로 %를 낸다.
- **Agent UI 키 카드**: 위와 동일하게 **키별** `expenditure/summary`의 `totalCostUsd`·`monthlyBudgetUsd`를 우선 사용하므로, Billing의 **키 선택 요약 %**와 맞추는 것이 목표다. 상단 배너 %와 숫자가 다르면 집계 단위가 다른 것이므로, 비교 시 Billing에서도 **같은 키**를 선택해 확인한다.

## 8. 최근 반영 사항 (2026-04-30)

- `available-context`와 `budget-forecast-assistant` BFF의 기본 백엔드 오리진을 동일하게 정리했다.
- Identity 이벤트 리스너에서 예외를 삼키지 않고 재전파하도록 수정했다.
- UI의 임계치 퍼센트 표기 로직을 보정하고, 중복된 빈 목록 가드를 제거했다.
- 팀 보드 목록은 팀명 대신 팀 API Key 별명(alias) 중심으로 표시하도록 조정했다.
- `available-context` 응답의 팀명은 괄호 포함 부가 텍스트를 제거한 값으로 통일했다.
- 개인 API 키 목록 UI는 alias 중심으로 단순화했고, `available-context`의 불필요한 보조 필드(note/디버그 파생 값)를 제거했다.
- `available-context`는 개인 키 스냅샷이 비어 있을 때 Identity 내부 API 조회로 보완하도록 확장했다.
- Gemini 기본 모델/환경 기본값을 `gemini-2.5-flash`로 상향했다.
- `budget-forecast-assistant` BFF에 내부 호출 타임아웃 분리(오리진 probe 3초, 예측 호출 20초)를 반영했다.
- BFF(`available-context`, `budget-forecast-assistant`)에서 로그인 세션 이메일을 파싱해 `x-user-email` 헤더를 내부 백엔드 호출에 전달하도록 변경했다.
- Agent UI 분석 액션을 개인 키/팀 키 버튼으로 분리했다.
- API Key별 "다음 결제일" 입력값을 브라우저 `localStorage`에 저장/복원하도록 정리했다.

## 9. 최근 반영 사항 (2026-05-06)

- `BillingSignalSnapshotService`: Redis 영속화, `usage.cost.finalized` / `billing.cost.corrected` 수신 시 저장, 기동 시 Redis에서 복원.
- `BillingSignalReconciliationService`: 기동 시 및 스케줄로 billing `expenditure/summary` 호출해 스냅샷 보정(billing-service 코드 변경 없음).
- `available-context` BFF: 키별 billing summary(`totalCostUsd`, `monthlyBudgetUsd`)·KST 월초~오늘·`X-Gateway-Auth`·세션/Authorization 전달, 개인 키 조회 경로 정리.
- `scripts/verify-agent-event-pipeline.ps1`: `GATEWAY_DEV_MODE=false`일 때 Gateway JWT 경로 지원.
- 문서: Billing 상단 % vs 키별 % vs Agent 키 카드 구분, 로컬 agent 호스트 포트 `8097` 안내.
- Recommendation 엔진: `policy-recommendations/analyze|{keyId}` API 추가, UI 추천 카드(절감률/절감액/근거 지표) 노출.
- 외부 모델 단가 카탈로그 동기화(`ExternalModelCatalogService`) 추가: URL 설정 시 주기 갱신, 실패 시 이전 스냅샷 유지.
- 업그레이드 추천 규칙 추가: 고지연(`HIGH_LATENCY`) 패턴에서는 비용 절감 우선이 아니라 고성능 후보를 우선 추천.
- `usage.recorded` 소비/7일 롤링 토큰 집계(`UsageRecordedTokenRollupService`)를 추천/예산 예측에 연결해 키별 input-output 비율 및 예측 정확도를 개선.
- UI 카탈로그 섹션의 `Provider별` 표시를 제거하고, `source`/활성 모델 수/갱신 실패 상태 중심으로 운영 가시성을 정리.

롤백위한수정 또 수정