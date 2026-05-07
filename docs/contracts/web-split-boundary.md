# Identity vs Usage vs Team 웹 경계 (라우트·BFF·소스)

버전: 1.9  
관련: [저장소 구조](../repository-structure.md) §6, [architecture.md](../architecture.md) §10.2·§13.3, [`docker/web-edge/nginx.conf.template`](../../docker/web-edge/nginx.conf.template), [web-identity-bff.md](./web-identity-bff.md), [web-gateway-bff.md](./web-gateway-bff.md), [web-team-bff.md](./web-team-bff.md), [mfe-pages-only-remote-split-guidance-20260414.md](../mfe-pages-only-remote-split-guidance-20260414.md)

단일 도메인·경로 기반 엣지 프록시 뒤에서는 브라우저 오리진이 하나이므로 아래 **브라우저 경로**가 그대로 유지된다. **소스 트리**는 서비스 소유권에 맞게 `services/<svc>/web/`에 나뉜다.

---

## 1. 소스 트리 (정본)

| 서비스 | Gradle(Spring) | Next.js 15 (`web/`) | MF remote (`web-mfe/`, 선택) |
|--------|----------------|---------------------|------------------------------|
| Identity | `services/identity-service/` | `services/identity-service/web/` | — |
| Usage | `services/usage-service/` | `services/usage-service/web/` | `services/usage-service/web-mfe/` |
| Team | `services/team-service/` | `services/team-service/web/` | `services/team-service/web-mfe/` |
| Notification | `services/notification-service/`(Nest/Prisma) | `services/notification-service/web/` | — |

**`apps/web`(web-host):** Module Federation **호스트**로 쓸 때 remote URL은 환경 변수(`NEXT_PUBLIC_MFE_*`)로 받는다. **`web-mfe`** 는 Pages Router·remote 엔트리 전용이며, BFF·App Router 운영 UI는 각 서비스 **`web/`** 에 둔다.

과도기 통합 앱 이전은 완료되었고, **`apps/web`** 은 호스트 전용 등 팀 합의 용도로 유지할 수 있다.

### 1.1 공유 UI 패키지 (`@ai-usage/ui`)

- **위치:** `packages/ui`(루트 **`pnpm` workspace**에 포함 — `pnpm-workspace.yaml`·`pnpm-lock.yaml` 정본).
- **범위:** `cn`, Button·Input·Label·Select 등 **Shadcn/Radix 래퍼**만. 화면·도메인·BFF는 각 `services/<svc>/web/`에 둔다([`repository-structure.md`](../repository-structure.md) §3·§6와 동일한 “도메인 로직 공유 금지” 취지).
- **로컬:** 저장소 루트에서 **`pnpm install`** 후 각 앱은 **`pnpm --filter identity-web dev`** / **`pnpm --filter usage-web dev`** / **`pnpm --filter team-web-mfe dev`** 등으로 기동할 수 있다(`README.md`).

---

## 2. 브라우저 라우트 소유

### 2.1 Identity `web/` (랜딩·계정·세션 BFF)

| 경로 접두 | 설명 |
|-----------|------|
| `/` | 랜딩 |
| `/login`, `/signup` | 로그인·회원가입 UI |
| `/settings`, `/organizations` | 계정·조직 UI (미들웨어 보호) |
| `/api/auth/*` | 세션·로그인·외부 키 BFF → Identity HTTP |
| `/api/identity/*` | Identity 관리 API 프록시 (`/api/v1/...` 업스트림) |

랜딩 **`/`** 는 [web-identity-bff.md §10.1](./web-identity-bff.md)에 따라 클라이언트에서 **`GET /api/auth/session`** 으로 세션을 읽어 헤더·CTA를 바꾼다. 로그인 상태에서는 헤더에 **대시보드·설정·로그아웃**(`POST /api/auth/logout` → 로그인 페이지)을 둔다. Usage 대시보드로 가는 링크는 **`usageAppHref("/dashboard")`** 로 조립하며, 분리 호스트 개발 시 **`NEXT_PUBLIC_USAGE_WEB_ORIGIN`** 을 설정한다([§4](#4-로컬-개발쿠키-httponly-access_token)).

### 2.2 Usage `web/` (대시보드·Usage BFF)

Next `basePath`는 **`/dashboard`**(단일 도메인에서 `/_next` 충돌 방지). 브라우저에 보이는 접두가 아래와 같다.

| 경로 접두 | 설명 |
|-----------|------|
| `/dashboard` | 대시보드 홈(UI) |
| `/dashboard/*` | 사용량 대시보드 하위 경로(UI, 미들웨어 보호) |
| `/dashboard/api/usage/*` | Usage BFF → API Gateway `/api/v1/usage/...` |

대시보드 사이드바(`services/*/web/src/components/dashboard/dashboard-sidebar.tsx`)에는 제품 랜딩으로 가는 별도 「랜딩」 항목을 두지 않는다. Identity `web`으로 돌아갈 **`홈으로`** 링크만 유지한다.

엣지에서 **`/dashboard/`** 접두(또는 **`/dashboard`** → **`/dashboard/`** 리다이렉트 후) → Usage Next, 그 외 많은 공개·계정 경로 → Identity Next로 가면 [web-identity-bff.md §5.1](./web-identity-bff.md)·[web-gateway-bff.md](./web-gateway-bff.md) 계약과 맞는다.

### 2.3 Team `web/` (팀 API BFF)

`/teams` UI는 **Identity `web` 소유**이며(동일 DashboardShell 유지), Team `web`는 API BFF 역할을 담당한다.

| 경로 접두 | 설명 |
|-----------|------|
| `/teams` | Identity `web` 팀 관리 UI |
| `/api/team/v1/*` | Identity `web` rewrite → Team BFF → Gateway `/api/team/v1/*` → Team Service `/api/v1/*` |
| `/api/auth/session` | (필요 시) Team BFF 세션 확인 → Identity `/api/auth/session` |

보완(2026-04, host 실구현):

- `apps/web`(web-host)는 `/teams/[id]/[section]` 라우트를 사용해 Team remote를 마운트할 수 있다.
- 사이드바의 팀 서브메뉴 경로는 다음을 사용한다.
  - `/teams/[teamId]/dashboard`
  - `/teams/[teamId]/members`
  - `/teams/[teamId]/api-keys`
- Team remote(`team-management-view`)는 좌측 팀 아코디언에서 멤버/API Key CRUD를 수행하고, 우측 패널은 Usage 전용 슬롯으로 비워 둔다.

### 2.4 Notification `web/` (인앱 알림 UI·Notification BFF)

`/notifications` UI는 **Notification `web`** 이 소유한다(단일 도메인 엣지에서 `/notifications/` 접두만 notification-web으로 프록시).

| 경로 접두 | 설명 |
|-----------|------|
| `/notifications` | Notification `web` 인앱 알림 목록 UI |
| `/notifications/*` | 인앱 알림 하위 경로(UI) |
| `/notifications/api/notification/*` | Notification BFF → notification-service(Nest) 프록시 (세션 확인 후 `X-User-Id` 부여) |

### 2.5 로컬 Compose `web-edge`(Nginx 정본)

`docker compose --profile web` 의 **`web-edge`** 는 **`docker/web-edge/nginx.conf.template`** 를 기반으로 기동한다. 요약:

- **`/dashboard`**(정확히): usage `web` `/dashboard` 로 프록시.
- **`/dashboard/`** 로 시작: **usage `web`** upstream. **`/dashboard2`** 처럼 접두가 **`/dashboard/`** 가 아니면 매칭되지 않아 **`location /`** 로 **identity `web`** 이 받는다(오탐 방지).
- **`/teams`**(정확히): team `web` `/teams` 로 프록시.
- **`/teams/`** 로 시작: **team `web`** upstream.
- **`/notifications`**(정확히): notification `web` `/notifications` 로 프록시.
- **`/notifications/`** 로 시작: **notification `web`** upstream.
- **`/api/v1`**(정확히): API Gateway `/api/v1` 로 프록시.
- **`/api/v1/`** 로 시작: **api-gateway-service** upstream. AI 스트리밍 등을 위해 **`proxy_buffering off`**·긴 read/send timeout 이 설정되어 있다.
- **그 외**: **identity `web`**.

### 2.6 Identity `next.config.ts` — `rewrites`(앱 간 통합 고속도로)

브라우저가 **Identity `web` 오리진 한 곳**(예: 로컬 `:3000`)만 바라볼 때, **`services/identity-service/web/next.config.ts`** 의 **`async rewrites()`** 가 아래와 같이 **다른 서비스의 `web` 컨테이너/프로세스**로 요청을 넘긴다. 이는 **`web-edge`**(§2.5)와 함께 “서비스 통합의 핵심 라우팅 계층”이며, 경로·환경 변수 변경 시 **반드시 이 파일과 계약 문서를 함께** 갱신한다.

| `source`(브라우저 경로) | `destination`(내부 업스트림) |
|-------------------------|-----------------------------|
| `/dashboard`, `/dashboard/:path*` | Usage `web` (`USAGE_WEB_INTERNAL_ORIGIN` 등) |
| `/billing`, `/billing/:path*` | Billing `web` |
| `/notifications`, `/notifications/:path*` | Notification `web` |
| `/api/team/v1/:path*` | Team `web` BFF 경로(`…/teams/api/team/v1/…`) |

새 **최상위 접두**를 추가해 다른 도메인 `web`으로 프록시할 때도 동일하게 **`rewrites`** 에 명시한다. Module Federation **remote** 전용 트래픽은 보통 **별도 포트·오리진**(`web-mfe` dev 서버, 예: `:3011`·`:3012`)으로 호스트가 직접 붙으며, 정본은 [`mfe-pages-only-remote-split-guidance-20260414.md`](../mfe-pages-only-remote-split-guidance-20260414.md)를 본다.

### 2.7 Billing `web/` (지출 대시보드·Expenditure BFF)

Next `basePath`는 **`/billing`**(단일 도메인·Identity `rewrites`와 정합 — 위 표의 `/billing`, `/billing/:path*`).

| 경로 접두 | 설명 |
|-----------|------|
| `/billing` | 지출 대시보드 UI |
| `/billing/*` | 지출 하위 경로(UI) |
| `/billing/api/expenditure/*` | Expenditure BFF → API Gateway `/api/v1/expenditure/...` |
| `/billing/api/expenditure/team/month-rollup` | **전용** `POST` 라우트: `teamId`·`userIds` 검증 후 서버에서 **`GET …/api/team/v1/teams/{teamId}/members`**(팀 멤버)로 교차 검증하고, 허용된 `userIds`만 게이트웨이 `POST /api/v1/expenditure/team/month-rollup`으로 전달한다. 서버가 팀 API를 부를 때의 베이스 오리진은 환경 변수 **`BILLING_TEAM_BFF_BASE_URL`**(선택) 및 `docker-compose.yml`의 `billing-web` 기본값으로 맞춘다. 상세·상태 코드는 [`billing-service-overview-20260412.md`](../billing-service-overview-20260412.md) §4.10. |

---

## 3. Next.js `middleware.ts` 매처

| 앱 | `config.matcher` |
|----|------------------|
| Identity `web/` | `/settings/:path*`, `/organizations/:path*`, `/teams/:path*` |
| Usage `web/` | 정적·API 제외 전역 매처(BFF `/dashboard/api/**`는 코드에서 통과) |
| Team `web/` | Team BFF 내부 규칙(브라우저 직접 진입 경로 소유 없음) |
| Notification `web/` | (현재 없음) — BFF(`/notifications/api/notification/*`)에서 쿠키 기반으로 인증 강제 |

`/dashboard` UI는 Usage 소유이므로 Identity 미들웨어에서 제외한다.

---

## 4. 로컬 개발·쿠키 (httpOnly `access_token`)

`access_token`은 **호스트+포트(오리진)** 에 묶인다. Identity와 Usage를 **서로 다른 포트**로만 띄우면, 한쪽에서 로그인해 설정한 쿠키가 다른 포트의 Next로 전달되지 않는다.

- **권장:** 단일 호스트·포트로 보이게 **엣지 리버스 프록시**(Nginx 등)로 경로만 나누거나, Compose 스택에서 동일 패턴으로 기동한다(`docs/architecture.md` §10.2).
- **선택:** 분리 포트로만 개발할 때는 `NEXT_PUBLIC_IDENTITY_WEB_ORIGIN` / `NEXT_PUBLIC_USAGE_WEB_ORIGIN`(각 `web/.env.example` 참고)으로 링크·리다이렉트를 보강할 수 있으나, **쿠키 공유는 동일 오리진이 아니면 되지 않는다**는 점을 유의한다. Identity 랜딩·로그인 후 이동에서 Usage로 넘어갈 때는 **`NEXT_PUBLIC_USAGE_WEB_ORIGIN`** + **`usageAppHref`**([web-identity-bff.md §10.1](./web-identity-bff.md))로 대시보드 URL을 맞춘다.
- **Compose로 `web` 프로필 기동 시:** 루트 **`.env`** 가 `identity-web`·`usage-web`·`api-gateway-service` 등에 전달된다. 대시보드 BFF가 게이트웨이에 붙으려면 게이트웨이 컨테이너가 살아 있어야 하며, **`GATEWAY_SHARED_SECRET`** 빈 값 주의는 [gateway-proxy.md §5.1](./gateway-proxy.md).

---

## 5. BFF·문서 교차 참조

- Identity 세션·쿠키·`/api/auth/*`·`/api/identity/*`: [web-identity-bff.md](./web-identity-bff.md)
- Usage `/dashboard/api/usage/*`·게이트웨이 매핑: [web-gateway-bff.md](./web-gateway-bff.md)
- Billing `/billing/api/expenditure/*`·팀 롤업 하드닝·`BILLING_TEAM_BFF_BASE_URL`: [billing-service-overview-20260412.md](../billing-service-overview-20260412.md) §4.10
- Team `/api/team/v1/*`·팀 생성/초대/초대 수락·거절·팀원·팀 삭제·`GET .../owner`·팀 API Key(등록은 팀장, 조회·수정은 멤버 등): [web-team-bff.md](./web-team-bff.md)
- Notification `/notifications/api/notification/*`·notification-service 프록시: [web-notification-bff.md](./web-notification-bff.md)
