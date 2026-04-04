# Web(Next.js) ↔ API Gateway — Usage BFF 계약

버전: 1.7  
관련: [docs/architecture.md](../architecture.md) §1.3, §10.1, §10.2, §13, [게이트웨이·Proxy 계약](./gateway-proxy.md)(AI 공개 경로·Bearer·`X-User-Id`·라우트·§5.1 Compose·`GATEWAY_SHARED_SECRET`), [Web·Identity BFF 계약](./web-identity-bff.md) §5.1·§6·§9, [저장소 구조](../repository-structure.md) §6, [웹 경계](./web-split-boundary.md)

**소스 트리:** Usage BFF·대시보드 UI의 **정본**은 `services/usage-service/web/` 이다. **공용 UI(Shadcn 래퍼·`cn`)** 는 루트 pnpm workspace 패키지 **`@ai-usage/ui`**(`packages/ui`)를 참조한다([`web-split-boundary.md` §1.1](./web-split-boundary.md), [`repository-structure.md`](../repository-structure.md) §6).

---

## 1. 목적

- 브라우저는 **동일 오리진** Next.js Route Handler만 호출하고, **Usage HTTP**는 BFF가 **API Gateway** 뒤의 Usage 서비스로 넘긴다.
- 플랫폼 JWT(`access_token`)는 **httpOnly 쿠키**로만 전달하고, BFF가 `Authorization: Bearer`로 게이트웨이에 붙인다(자바스크립트에 토큰 평문 노출 없음).

### 1.1 AI 경로 (`/api/v1/ai/**`)

- **본 문서의 범위가 아니다.** 클라이언트→게이트웨이→Proxy 경로·`RewritePath`·`RemoveRequestHeader=Authorization`·신뢰 헤더는 **[gateway-proxy.md](./gateway-proxy.md) §2–§5** 가 정본이다.
- 웹에서 AI를 별도 BFF로 감쌀지 여부는 팀 합의(현 저장소 기본은 Usage BFF만 구현).

---

## 2. 환경 변수 (Usage `web/`)

| 변수 | 용도 |
|------|------|
| `API_GATEWAY_URL` | 게이트웨이 **공개** 베이스 URL(트레일링 슬래시 없음). BFF가 `{base}/api/v1/usage/...` 로 프록시한다. |
| `GATEWAY_DEV_MODE` | `true`/`1`이면 게이트웨이 `gateway.dev-mode=true`와 맞춘 **개발 모드**로 간주하고, Usage 요청에 **`X-User-Id` 보강**을 위해 Identity 세션을 조회한다. |
| `IDENTITY_SERVICE_URL` | `GATEWAY_DEV_MODE` 사용 시 필수. BFF가 `GET /api/auth/session`으로 **이메일**을 읽어 `X-User-Id`에 넣는다. |
| `NEXT_PUBLIC_BASE_PATH` | (선택) Usage Next `basePath`와 동기화. 단일 도메인 엣지에서 정적 자산 경로 충돌을 피하기 위해 기본 **`/dashboard`**. 브라우저 BFF 호출은 `{basePath}/api/usage/...` 형태가 된다(`fetch-usage.ts`). |

상세·내부 아웃바운드 URI(`GATEWAY_USAGE_URI` 등)와의 구분은 [gateway-proxy.md §9](./gateway-proxy.md).

**Docker Compose(`usage-web` 컨테이너):** 루트 `.env`가 로드되며 `API_GATEWAY_URL`·`GATEWAY_DEV_MODE`·`IDENTITY_SERVICE_URL` 등이 compose `environment`로 주입된다. **`profile: web`** 에 **`web-edge`** 가 있으면 브라우저는 동일 오리진에서 **`/api/v1/*`** 로 API Gateway에 직접 붙을 수 있다(Usage BFF 경유가 아닌 공개 게이트웨이 경로 — [gateway-proxy.md §2](./gateway-proxy.md); 엣지는 **`/api/v1`** → **`/api/v1/`** 리다이렉트 등 — [web-split-boundary.md §2.3](./web-split-boundary.md)). **게이트웨이 컨테이너가 기동하려면** 루트 `.env`의 **`GATEWAY_SHARED_SECRET`** 이 비어 있지 않아야 한다(빈 값 주의: [gateway-proxy.md §5.1](./gateway-proxy.md)). 호스트에서 Next만 띄울 때는 저장소 루트 **`pnpm install`** 후 **`pnpm --filter usage-web dev`**(또는 해당 `web/`에서 `pnpm dev`)를 쓰고, 환경은 **`services/usage-service/web/.env`** 를 본다.

---

## 3. BFF 엔드포인트·경로 매핑

**구현:** [`services/usage-service/web/src/app/api/usage/[[...path]]/route.ts`](../../services/usage-service/web/src/app/api/usage/[[...path]]/route.ts)

- 브라우저: **`{NEXT_PUBLIC_BASE_PATH 또는 /dashboard}/api/usage/{세그먼트…}{?쿼리}`** — `path`가 비어 있으면 BFF는 `404`. (`basePath` 비활성화 시에만 동일 오리진에서 `/api/usage/...`가 될 수 있으나, 저장소 기본은 `/dashboard` 접두.)
- 업스트림(게이트웨이): **`{API_GATEWAY_URL}/api/v1/usage/{동일 세그먼트}{동일 쿼리}`**
- 지원 메서드: `GET`, `HEAD`, `POST`, `PUT`, `PATCH`, `DELETE`(본문 있을 때 스트리밍 전달).

### 3.1 Usage 대시보드·로그 (현행 백엔드)

정본 컨트롤러: [`UsageAnalyticsController`](../../services/usage-service/src/main/java/com/eevee/usageservice/api/UsageAnalyticsController.java) (`@RequestMapping("/api/v1/usage")`).

| 브라우저(BFF) 예시 (`basePath=/dashboard`) | 게이트웨이로 전달되는 path |
|--------------------|----------------------------|
| `GET /dashboard/api/usage/dashboard/summary?…` | `/api/v1/usage/dashboard/summary?…` |
| `GET /dashboard/api/usage/dashboard/series/daily?…` | `/api/v1/usage/dashboard/series/daily?…` |
| `GET /dashboard/api/usage/dashboard/series/monthly?…` | `/api/v1/usage/dashboard/series/monthly?…` |
| `GET /dashboard/api/usage/dashboard/by-model?…` | `/api/v1/usage/dashboard/by-model?…` |
| `GET /dashboard/api/usage/logs?…` | `/api/v1/usage/logs?…` |

쿼리 파라미터(기간·페이지 등)는 Usage 서비스 API와 동일하게 전달한다. 응답은 **Usage DTO JSON**(공통 `ApiResponse` 래핑 없음)이 기본이다.

#### 3.1.1 대시보드 UI 표시(시각·차트)

- **사용 로그 테이블:** API가 주는 `occurredAt`(ISO-8601, 보통 UTC 기준 오프셋/Z)은 브라우저에서 **`Asia/Seoul`(KST)** 로 포맷해 표시한다(`services/usage-service/web/src/lib/usage/format-occurred-at-kst.ts` 등). 헤더 문구는 이에 맞춘다.
- **집계·요약 카드:** 일자·“오늘” 등 백엔드가 **UTC 일자**로 집계하는 값은 그 의미를 유지하고, 로그 행 시각만 KST로 바꾼다(카드 문구를 KST로 바꾸면 의미가 어긋날 수 있음).
- **차트 색상:** 대시보드 차트 팔레트는 **무채색 계열**로 통일해 UI 톤과 맞춘다(`usage-dashboard.tsx`의 `CHART_COLORS`, 그리드 스트로크 등).

### 3.2 프론트 호출·401

- 보호 대시보드 등에서는 [`services/usage-service/web/src/lib/usage/fetch-usage.ts`](../../services/usage-service/web/src/lib/usage/fetch-usage.ts) 패턴으로 `credentials: "include"`·`401` 시 Identity 로그인으로 리다이렉트를 적용할 수 있다([web-identity-bff.md §6.1](./web-identity-bff.md), [web-split-boundary.md §4](./web-split-boundary.md)).

### 3.3 샘플 curl (로컬)

Compose·루트 `.env.example` 기본값과 같이 **Identity `web`은 호스트 `3000`**, **Usage `web`은 `3001`**(`USAGE_WEB_PORT`)을 쓴다고 가정한다. 게이트웨이는 `http://localhost:8080`, 개발 모드에서 `access_token` 쿠키·`X-User-Id` 보강이 되는 전제를 둔다(브라우저 쿠키가 필요하면 BFF curl은 아래와 같이 쿠키를 넣는다).

```bash
# 게이트웨이 → Usage (개발 모드: X-User-Id 필수)
curl -sS -i "http://localhost:8080/api/v1/usage/dashboard/summary" \
  -H "X-User-Id: user@example.com"

# Usage BFF 경유(세션 쿠키 필요, Usage web basePath=/dashboard)
curl -sS -i "http://localhost:3001/dashboard/api/usage/dashboard/summary" \
  --cookie "access_token=<JWT>"
```

---

## 4. 인증·헤더 (게이트웨이와의 정합)

1. **`access_token` 쿠키**가 없으면 BFF는 `401`(게이트웨이 호출 없음).
2. 있으면 BFF는 **`Authorization: Bearer {access_token}`** 를 설정해 게이트웨이로 보낸다.
3. **`GATEWAY_DEV_MODE`가 켜진 경우:** BFF는 Identity `GET {IDENTITY_SERVICE_URL}/api/auth/session`으로 **유효 세션·`email`** 을 확인한 뒤 **`X-User-Id: {email}`** 을 붙인다. 실패 시 `401`.
4. **운영(`gateway.dev-mode=false`):** 게이트웨이가 JWT를 검증하고 **`X-User-Id` = JWT `sub`** 등으로 설정한다. Usage로는 **`Authorization`이 제거**된 채 전달된다([gateway-proxy.md §3.1](./gateway-proxy.md)).

**Usage 원장 `user_id`와 `X-User-Id`·JWT `sub` 정합**은 [gateway-proxy.md §4.2](./gateway-proxy.md)를 따른다.

---

## 5. 조직·팀·설정(Identity 직결)

- 게이트웨이 경유 Usage가 아니라 **Identity REST**를 쓰는 경로는 **[web-identity-bff.md §5.2](./web-identity-bff.md)** (`/api/identity/v1/**` BFF)가 정본이다.

---

## 6. 회귀 테스트

- BFF 동작: `services/usage-service/web/src/app/api/usage/[[...path]]/route.test.ts` (Vitest).
- 게이트웨이 신뢰 헤더·JWT: `ProxyTrustHeadersWebFilterTest`(api-gateway-service) — [gateway-proxy.md §4.2](./gateway-proxy.md).
