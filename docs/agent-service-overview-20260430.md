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
- `POST /budget-forecast-assistant`
  - 입력: `BudgetForecastRequest`
  - 출력: `BudgetForecastResponse`
- `GET /identity-api-keys`
  - 설명: 전체 개인 API Key 스냅샷 조회
- `GET /identity-api-keys/{userId}`
  - 설명: 사용자별 개인 API Key 스냅샷 조회
- `GET /team-api-keys?teamId={id}`
  - 설명: 팀 API Key 스냅샷 조회
- `GET /billing-signals?teamId={id}`
  - 설명: 비용 최종화/예산 임계 이벤트 기반 신호 조회
- `GET /user-contexts`
  - 설명: 사용자 컨텍스트(active team, role) 스냅샷 조회
- `GET /debug/events?limit={n}`
  - 설명: 최근 수신 이벤트 디버그 조회

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

구체 큐/라우팅키는 `services/agent-service/src/main/resources/application.properties`를 따른다.

## 4. 웹(BFF) 구성

웹 basePath는 `/agent`이며 Next Route Handler가 백엔드 API를 중계/합성한다.

- `GET /agent/api/v1/agents/available-context`
  - 개인 API Key(alias 중심) + 팀 API Key + Billing Signal + User Context를 합쳐 UI에 전달
  - 개인 API Key는 우선 agent 이벤트 스냅샷을 사용하고, 비어 있을 때 Identity 예산 API(`/api/identity/v1/users/{userId}/budget` 또는 이메일 기반 `/api/identity/v1/users/budget`)를 fallback으로 조회한다.
  - 로그인 사용자 식별 헤더(`x-user-id`)가 숫자가 아니거나 누락된 경우에도 컨텍스트/팀 owner 정보를 기반으로 사용자 식별을 보완한다.
  - 팀명은 괄호 메타정보를 제거한 표시명으로 정규화한다. 예: `Platform Team (T-12)` -> `Platform Team`
- `POST /agent/api/v1/agents/budget-forecast-assistant`
  - 백엔드 `budget-forecast-assistant` API 프록시

내부 백엔드 오리진은 `AI_AGENT_SERVICE_INTERNAL_ORIGIN` 환경변수로 지정한다.

## 5. 환경변수 요약

- 포트/LLM
  - `AI_AGENT_SERVICE_PORT` (default: `8096`)
  - `AI_AGENT_GEMINI_API_KEY`
  - `AI_AGENT_GEMINI_MODEL` (default: `gemini-1.5-flash`)
  - `AI_AGENT_GEMINI_BASE_URL`
- RabbitMQ
  - `SPRING_RABBITMQ_HOST`, `SPRING_RABBITMQ_PORT`
  - `SPRING_RABBITMQ_USERNAME`, `SPRING_RABBITMQ_PASSWORD`
  - `AI_AGENT_RABBIT_*` 계열(Identity/Team/Billing queue, exchange, routing key, enabled)
- Web BFF
  - `AI_AGENT_SERVICE_INTERNAL_ORIGIN`
  - `IDENTITY_SERVICE_INTERNAL_ORIGIN` (optional, 미지정 시 `localhost:8090` 등 기본 후보 사용)
  - `TEAM_SERVICE_INTERNAL_ORIGIN` (optional)

## 6. 운영상 주의점

- 스냅샷 저장소는 메모리 기반(`ConcurrentHashMap`, `ConcurrentLinkedDeque`)이라 서비스 재시작 시 상태가 초기화된다.
- `debug/events`는 운영 환경에서 접근 통제와 민감정보 마스킹 정책을 반드시 적용해야 한다.
- 컨텍스트 집계는 이벤트 도착 순서와 시점에 따라 일시적으로 비어 있을 수 있다.
- 개인 키 이벤트가 아직 유입되지 않은 초기 상태에서는 개인 키 목록이 비어 보일 수 있으며, 이때는 Identity fallback 조회 성공 여부(오리진/포트/권한 헤더)를 함께 점검해야 한다.
- `docker compose --profile web up`에는 `agent-web`만 포함되고 `agent-service`(Spring)는 포함되지 않을 수 있다. 이 경우 `AI_AGENT_SERVICE_INTERNAL_ORIGIN` 대상(기본 `host.docker.internal:8096`)에 백엔드가 실제로 떠 있어야 이벤트 수신/스냅샷 API가 동작한다.

## 7. 최근 반영 사항 (2026-04-30)

- `available-context`와 `budget-forecast-assistant` BFF의 기본 백엔드 오리진을 동일하게 정리했다.
- Identity 이벤트 리스너에서 예외를 삼키지 않고 재전파하도록 수정했다.
- UI의 임계치 퍼센트 표기 로직을 보정하고, 중복된 빈 목록 가드를 제거했다.
- 팀 보드 목록은 팀명 대신 팀 API Key 별명(alias) 중심으로 표시하도록 조정했다.
- `available-context` 응답의 팀명은 괄호 포함 부가 텍스트를 제거한 값으로 통일했다.
- 개인 API 키 목록 UI는 alias 중심으로 단순화했고, `available-context`의 불필요한 보조 필드(note/디버그 파생 값)를 제거했다.
- `available-context`는 개인 키 스냅샷이 비어 있을 때 Identity 내부 API 조회로 보완하도록 확장했다.
