# Identity vs Usage 웹 경계 (라우트·BFF·소스)

버전: 1.1  
관련: [저장소 구조](../repository-structure.md) §6, [architecture.md](../architecture.md) §10.2, [`docker/web-edge/nginx.conf`](../../docker/web-edge/nginx.conf), [web-identity-bff.md](./web-identity-bff.md), [web-gateway-bff.md](./web-gateway-bff.md)

단일 도메인·경로 기반 엣지 프록시 뒤에서는 브라우저 오리진이 하나이므로 아래 **브라우저 경로**가 그대로 유지된다. **소스 트리**는 서비스 소유권에 맞게 `services/<svc>/web/`에 나뉜다.

---

## 1. 소스 트리 (정본)

| 서비스 | Gradle(Spring) | Next.js (`web/`) |
|--------|----------------|------------------|
| Identity | `services/identity-service/` | `services/identity-service/web/` |
| Usage | `services/usage-service/` | `services/usage-service/web/` |

과도기 통합 앱 `apps/web/`은 **이행 완료 후 제거**되며, 남아 있으면 안내용 `README`만 둔다.

---

## 2. 브라우저 라우트 소유

### 2.1 Identity `web/` (랜딩·계정·세션 BFF)

| 경로 접두 | 설명 |
|-----------|------|
| `/` | 랜딩 |
| `/login`, `/signup` | 로그인·회원가입 UI |
| `/settings`, `/organizations`, `/teams` | 계정·조직·팀 UI (미들웨어 보호) |
| `/api/auth/*` | 세션·로그인·외부 키 BFF → Identity HTTP |
| `/api/identity/*` | Identity 관리 API 프록시 (`/api/v1/...` 업스트림) |

### 2.2 Usage `web/` (대시보드·Usage BFF)

Next `basePath`는 **`/dashboard`**(단일 도메인에서 `/_next` 충돌 방지). 브라우저에 보이는 접두가 아래와 같다.

| 경로 접두 | 설명 |
|-----------|------|
| `/dashboard` | 대시보드 홈(UI) |
| `/dashboard/*` | 사용량 대시보드 하위 경로(UI, 미들웨어 보호) |
| `/dashboard/api/usage/*` | Usage BFF → API Gateway `/api/v1/usage/...` |

엣지에서 **`/dashboard/`** 접두(또는 **`/dashboard`** → **`/dashboard/`** 리다이렉트 후) → Usage Next, 그 외 많은 공개·계정 경로 → Identity Next로내면 [web-identity-bff.md §5.1](./web-identity-bff.md)·[web-gateway-bff.md](./web-gateway-bff.md) 계약과 맞는다.

### 2.3 로컬 Compose `web-edge`(Nginx 정본)

`docker compose --profile web` 의 **`web-edge`** 는 **`docker/web-edge/nginx.conf`** 를 쓴다. 요약:

- **`/dashboard`**(정확히): **`308`** → **`/dashboard/`** (슬래시 고정).
- **`/dashboard/`** 로 시작: **usage `web`** upstream. **`/dashboard2`** 처럼 접두가 **`/dashboard/`** 가 아니면 매칭되지 않아 **`location /`** 로 **identity `web`** 이 받는다(오탐 방지).
- **`/api/v1`**(정확히): **`308`** → **`/api/v1/`**.
- **`/api/v1/`** 로 시작: **api-gateway-service** upstream. AI 스트리밍 등을 위해 **`proxy_buffering off`**·긴 read/send timeout 이 설정되어 있다.
- **그 외**: **identity `web`**.

---

## 3. Next.js `middleware.ts` 매처

| 앱 | `config.matcher` |
|----|------------------|
| Identity `web/` | `/settings/:path*`, `/organizations/:path*`, `/teams/:path*` |
| Usage `web/` | 정적·API 제외 전역 매처(BFF `/dashboard/api/**`는 코드에서 통과) |

`/dashboard` UI는 Usage 소유이므로 Identity 미들웨어에서 제외한다.

---

## 4. 로컬 개발·쿠키 (httpOnly `access_token`)

`access_token`은 **호스트+포트(오리진)** 에 묶인다. Identity와 Usage를 **서로 다른 포트**로만 띄우면, 한쪽에서 로그인해 설정한 쿠키가 다른 포트의 Next로 전달되지 않는다.

- **권장:** 단일 호스트·포트로 보이게 **엣지 리버스 프록시**(Nginx 등)로 경로만 나누거나, Compose 스택에서 동일 패턴으로 기동한다(`docs/architecture.md` §10.2).
- **선택:** 분리 포트로만 개발할 때는 `NEXT_PUBLIC_IDENTITY_WEB_ORIGIN` / `NEXT_PUBLIC_USAGE_WEB_ORIGIN`(각 `web/.env.example` 참고)으로 링크·리다이렉트를 보강할 수 있으나, **쿠키 공유는 동일 오리진이 아니면 되지 않는다**는 점을 유의한다.
- **Compose로 `web` 프로필 기동 시:** 루트 **`.env`** 가 `identity-web`·`usage-web`·`api-gateway-service` 등에 전달된다. 대시보드 BFF가 게이트웨이에 붙으려면 게이트웨이 컨테이너가 살아 있어야 하며, **`GATEWAY_SHARED_SECRET`** 빈 값 주의는 [gateway-proxy.md §5.1](./gateway-proxy.md).

---

## 5. BFF·문서 교차 참조

- Identity 세션·쿠키·`/api/auth/*`·`/api/identity/*`: [web-identity-bff.md](./web-identity-bff.md)
- Usage `/dashboard/api/usage/*`·게이트웨이 매핑: [web-gateway-bff.md](./web-gateway-bff.md)
