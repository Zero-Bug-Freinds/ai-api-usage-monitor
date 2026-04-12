# <Team Project> AI Usage & Billing Platform (MSA)

프록시 서버 기반으로 OpenAI / Gemini / Claude 등 AI Provider의 **실시간 사용량과 비용**을 수집·분석하고, **개인/팀/조직 단위 Quota 및 정산**을 돕는 MSA 플랫폼입니다.

## 목표(Goals)
- 조직 내 AI API 사용량을 투명하게 관리
- 팀별 AI 비용 자동 정산
- 개인 사용자의 AI 비용/사용 패턴 별도 추적 및 분석
- 사용량 폭증을 사전에 감지
- 사용자/팀/조직별 비용 책임을 명확히 분리

## 핵심 기능(요약)
- **Proxy 기반 사용량 수집**: 사용자는 Provider를 직접 호출하지 않고 플랫폼 프록시로 호출
- **비용 계산 및 정산**: 사용량(토큰/estimated cost)을 기반으로 개인/팀/조직 비용 집계
- **대시보드**: 개인/조직·팀별 사용량 및 비용 확인
- **Budget 제한**: soft limit(경고) / hard block(차단) 정책
- **알림**: Slack/Email 등으로 임계치 도달 알림

## 아키텍처(High-Level)
- 외부 요청 진입: `API Gateway`
- 핵심 도메인: `Proxy Service`(AI 호출 중계 + usage 이벤트 발행)
- 비동기 처리: `RabbitMQ` 이벤트 기반 연계(usage-recorded)
- 도메인 서비스 분리(현재 `services/`에 있는 실행 단위 예시: Gateway·Proxy·Identity·Usage·Team·Billing 등):
  - `Identity Service`(인증·사용자·조직·RBAC; 공급사 외부 API 키 등은 이 경계에서 구현될 수 있음)
  - `Team Service`(팀·팀원·팀 API Key 등 팀 도메인; Identity와 HTTP API로 연동)
  - `Usage Service`(usage 이벤트 소비·저장 및 사용량 대시보드)
  - `Billing Service`(지출 집계·API Key 노출 목록·비용 확정 이벤트 발행 등; 상세는 `docs/billing-service-overview-20260412.md`)
  - `Notification Service`(알림 백엔드 스캐폴드: `services/notification-service`, NestJS·Prisma 등 — `docs/architecture.md` §12)
  - `Quota Service` 등은 목표 아키텍처·로드맵에 따라 추가 가능

## 기술 스택(결정)
- **백엔드(Proxy 등)**: **Spring Boot + Spring WebFlux** — 비동기 I/O·스트리밍·Provider 중계에 사용. **FastAPI(Python)는 사용하지 않습니다.**
- **메시지 브로커**: **RabbitMQ** — `usage-recorded` 등 이벤트 발행·구독(Spring AMQP).
- **기타 서비스**: 동일 Spring 생태계에서 Spring MVC + JPA 등으로 구현 가능(팀 합의).
- 상세: `docs/architecture.md` §2.1, §6.2, §10.2, §13 · 웹 경계: `docs/contracts/web-split-boundary.md`

## 로컬 개발 관련(중요)
- **Kubernetes는 사용하지 않음**: 배포하지 않는 캡스톤 환경을 전제로 합니다.
- **실행 순서·(Google / OpenAI) 키·경로·로그/Rabbit/DB로 사용량 파이프라인 검증**은 **`docs/architecture.md`** §3.3·§10, **`docs/contracts/gateway-proxy.md`**, 루트 **`docker-compose.yml`** · **`.env.example`** 을 본다.
- **의존성(DB·큐·캐시)**: **Docker Compose**로 실행합니다. 예: `PostgreSQL`, `RabbitMQ`, `Redis`.
- **API Gateway + Proxy**: `docker-compose.yml`에서 **컨테이너로 함께 기동**할 수 있습니다(호스트 포트 기본 `8080` / `8081`). 계약·경로는 `docs/contracts/gateway-proxy.md`를 참고합니다.
- **루트 `.env` + Compose:** `docker compose`는 프로젝트 루트의 **`.env`**만 자동 로드합니다. **`GATEWAY_SHARED_SECRET`** 은 Compose가 `${GATEWAY_SHARED_SECRET:-}` 로 넘길 때 **빈 값만 두면** 컨테이너 안 Spring이 yml 기본값을 쓰지 못해 게이트웨이 기동이 실패할 수 있으므로, **`.env.example`과 같이 비어 있지 않은 값**으로 맞추거나 해당 줄을 제거하세요(상세: `docs/contracts/gateway-proxy.md` §5, `docs/architecture.md` §10.1). **호스트에서 `bootRun`만** 할 때는 Gradle/IDE가 루트 `.env`를 읽지 않으므로, 필요하면 동일 변수를 실행 구성에 넣습니다.
- **identity-service** 등 그 외 앱도 **로컬 JVM** 실행을 기본으로 하며, 필요 시 Compose에 추가할 수 있습니다.
- **team-service / billing-service**: team은 `TEAM_SERVICE_PORT` 미설정 시 기본 **8093**, billing은 **`BILLING_SERVICE_PORT` 기본 8095**(`application.yml`)라 기본만으로는 포트가 겹치지 않습니다. `scripts/bootrun.ps1`(·`bootrun.sh`)은 team **8094**·billing **8095**를 넣어 이전(둘 다 8093) 충돌 환경과도 호환됩니다. API Gateway **`GATEWAY_BILLING_URI`** 기본은 **billing과 동일(8095)** 입니다. 지출 체인 스모크: **`scripts/verify-expenditure-chain.ps1`** 또는 **`scripts/verify-expenditure-chain.sh`** (`GATEWAY_DEV_MODE=false`이면 JWT 또는 identity 로그인 env로 ②단계 포함 가능, §6.4·`.env.example` 참고). 상세 점검표는 **`docs/billing-service-overview-20260412.md` §6.4** 를 본다.
- **Compose `profile: web`의 team-service**는 루트 **`docker-compose.yml`**에서 호스트에 노출하는 포트만 **`TEAM_SERVICE_HOST_PORT`**(기본 **8093**)로 매핑합니다. 호스트에서 JVM으로 team을 띄울 때 쓰는 **`TEAM_SERVICE_PORT`**(예: **8094**)와 이름을 분리해, 예전처럼 같은 변수가 Compose 호스트 바인딩과 겹치면서 Docker Desktop이 호스트 **8094**를 잡고 `bootRun`이 실패하는 경우를 피합니다. `TEAM_SERVICE_PORT`는 Compose `team-service` 컨테이너 안에서는 여전히 **8093**으로 고정이며, **team-web**이 같은 스택에서 부를 때는 기본으로 **`http://team-service:8093`** 을 씁니다(호스트 전용 team이면 `.env`의 **`WEB_TEAM_SERVICE_URL`**을 `host.docker.internal` + 실제 포트로 맞춤).
- **컨테이너 배포 모델**: 백엔드·프론트 **이미지 분리 + Docker Compose 스택**(패턴 B, `docs/architecture.md` §10.1). Next는 루트 **`pnpm` workspace**(`packages/ui` + 각 `web`)를 포함해 **저장소 루트를 build context**로 `docker build -f services/identity-service/web/Dockerfile …`, `docker build -f services/usage-service/web/Dockerfile …`, `docker build -f services/team-service/web/Dockerfile …` 하거나, **`profile: web`** 으로 Compose에 **`identity-web`**, **`usage-web`**, **`team-web`**, 필요 시 **`team-service`**, **`web-edge`**(Nginx, `docker/web-edge/nginx.conf`)를 함께 올립니다.
- **단일 도메인**: **`web-edge`** 기본 호스트 포트 **`8888`**(`WEB_EDGE_PORT`)에서 진입 — `/dashboard`는 `/dashboard/`로 리다이렉트(308) 후 **`/dashboard/`** 접두만 Usage `web`; `/teams`는 `/teams/`로 리다이렉트(308) 후 **`/teams/`** 접두만 Team `web`; `/api/v1/` 접두는 API Gateway; 그 외(예: `/dashboard2`)는 Identity `web`(`docker/web-edge/nginx.conf`, `docs/architecture.md` §10.2, `docs/contracts/web-split-boundary.md`).

## 개발 방식(풀스택·서비스 소유)

별도 “프론트 전담” 역할을 두지 않고, **도메인 서비스를 담당하는 사람이 Spring 백엔드와(필요 시) 같은 폴더의 `web/`(Next)를 함께** 유지합니다. 상세·디렉터리 표는 `docs/repository-structure.md` §6, `docs/architecture.md` §13을 참고합니다.

### 빌드 순서(요약)

1. **Java:** `proxy-service`·`api-gateway-service` 는 이미지 빌드 전 해당 디렉터리에서 `./gradlew bootJar` 로 `app.jar` 를 둔다. **identity-service**·**usage-service** 백엔드 Dockerfile 은 이미지 안에서 Gradle 을 돌려 JAR 을 만든다(usage 는 저장소 루트에서 `docker build -f services/usage-service/Dockerfile …`).
2. **Next.js:** 저장소 루트에서 **`pnpm install`**(전역 pnpm 없으면 `npx pnpm@9 install`) 후 **`pnpm build:web`**(또는 각 `web`에서 `pnpm build`). `output: 'standalone'` 산출물을 Docker가 복사(`docs/architecture.md` §10.1).
3. **공유 UI:** **`packages/ui`**(`@ai-usage/ui`) — Shadcn 래퍼·`cn`; 세 Next 앱(identity-web·usage-web·team-web)이 workspace 로 참조한다.

로컬 포트·`.env` 힌트는 루트 **`.env.example`**, 각 **`services/*/web/.env.example`**, **`docs/contracts/web-identity-bff.md` §9**, **`docs/contracts/web-split-boundary.md`** 를 본다.

## 모노레포 레이아웃(요약)
실행 가능한 앱은 `services/` 하위에 둡니다. (`docs/repository-structure.md` 참고)
- `services/api-gateway-service` — API Gateway(Spring Cloud Gateway), JWT·라우팅·Proxy로 신뢰 헤더 전달
- `services/proxy-service` — AI Provider 프록시(WebFlux), usage 이벤트 발행
- `services/usage-service` — `UsageRecordedEvent` 소비·PostgreSQL 원장 저장(Spring AMQP + JPA)
- `services/identity-service` — 계정·조직·API Key 등(Identity; 목표 Next는 `services/identity-service/web/`)
- `services/team-service` — 팀·팀원·팀 API Key 등(Team; Next는 `services/team-service/web/`)
- `services/billing-service` — 지출·집계·비용 확정 AMQP 발행 등(Spring; Next·BFF는 `services/billing-service/web/`)
- `services/notification-service` — 알림 백엔드(팀 스택에 맞는 Node/Nest 등; `docs/repository-structure.md` §2)
- `libs/usage-events` — 공유 이벤트(`UsageRecordedEvent`, `UsageCostFinalizedEvent` 등)
- `apps/web` — **이행 완료** 안내(`README.md`만). UI+BFF 소스는 `services/identity-service/web`, `services/usage-service/web`, `services/team-service/web`.

## 문서
- **로컬 실행·테스트(Gateway / Proxy / usage·이벤트·DB)**: `docs/architecture.md` §3.3·§6·§10, `docs/contracts/gateway-proxy.md`
- Gateway ↔ Proxy 계약: `docs/contracts/gateway-proxy.md`
- **이벤트·usage·Billing·대시보드 책임 구분**: `docs/architecture.md` §2·§6·§11·§12 · Billing 구현 개요: `docs/billing-service-overview-20260412.md`
- C4 다이어그램(코드 기준): `docs/c4-architecture-diagrams.md`
  - Mermaid Live Editor에서 렌더링/검증 가능: https://mermaid.live/
- 아키텍처 문서: `docs/architecture.md`
- 시퀀스·호출 흐름(다이어그램): `docs/c4-architecture-diagrams.md`, `docs/architecture.md`
- 저장소·디렉터리 구조(모노레포): `docs/repository-structure.md`
- Identity vs Usage 웹 라우트·BFF 경계: `docs/contracts/web-split-boundary.md`
- MSA 이론 배경: `docs/msa-architecture-theory.md`
- 코드 컨벤션(네이밍·스타일): `docs/code-conventions.md`
- 브랜치·Git Flow·보호 규칙: `docs/branch-conventions.md`
- 커밋 메시지 규칙: `docs/commit-conventions.md`
- GitHub Actions CI: `docs/CI.md`

## 팀 정보
- 팀원: 김민진, 박예나, 조민선

해당 프로젝트는 2026년 1학기 팀프로젝트1 캡스톤 디자인에서 개발하는 MSA 기반 서비스입니다.
