# Web(Next.js) ↔ API Gateway 경유 BFF 호출 맵

버전: 1.0  
관련: [web-identity-bff.md](./web-identity-bff.md), [gateway-proxy.md](./gateway-proxy.md), [`services/api-gateway-service`](../../services/api-gateway-service), [`UsageAnalyticsController`](../../services/usage-service/src/main/java/com/eevee/usageservice/api/UsageAnalyticsController.java)

---

## 1. 목적

- 브라우저는 **httpOnly 쿠키**만으로 세션을 유지하고, **API Gateway를 직접 호출하지 않는 것**을 기본으로 한다(크로스 오리진 쿠키·게이트웨이 쿠키 파서·CORS credentials 없이도 성립).
- Next.js **Route Handler(BFF)** 가 `access_token` 쿠키를 읽어 upstream으로 **`Authorization: Bearer`** 를 싣는다. 정본 패턴은 [web-identity-bff.md §5](./web-identity-bff.md)와 동일하다.
- 본 문서는 **화면·기능 → BFF 경로 → Gateway 공개 URL(= usage-service에 그대로 전달되는 path)** 을 한 장으로 고정한다. OpenAPI 스펙이 없을 때는 **본 표 + `UsageAnalyticsController`** 를 계약 정본으로 삼는다.

---

## 2. 환경 변수 (`apps/web`)

| 변수 | 용도 | 로컬 기본값 (참고) |
|------|------|-------------------|
| `IDENTITY_SERVICE_URL` | 회원가입·로그인·세션 등 Identity BFF 업스트림 | `http://localhost:8080` ([web-identity-bff.md §9](./web-identity-bff.md)) |
| `API_GATEWAY_URL` | 대시보드·집계(및 향후 AI 프록시 등) BFF 업스트림 — **API Gateway 베이스 URL** | `http://localhost:8080` (`docker-compose.yml` 의 `API_GATEWAY_PORT` 매핑과 일치) |

**포트 충돌:** 호스트에서 Identity가 `8080`을 쓰면 Gateway를 `8088` 등으로 올리는 경우가 있다(루트 `.env.example` 주석 참고). 그때는 `IDENTITY_SERVICE_URL`과 `API_GATEWAY_URL`을 **서로 다른 포트**로 둔다.

---

## 3. 호출 맵

### 3.1 인증 (구현됨)

| 화면·기능 | BFF | Method | Upstream | Bearer |
|-----------|-----|--------|----------|--------|
| 회원가입 | `/api/auth/signup` | POST | `{IDENTITY_SERVICE_URL}/api/auth/signup` | 없음 |
| 로그인 | `/api/auth/login` | POST | `{IDENTITY_SERVICE_URL}/api/auth/login` | 없음 |
| 세션(로그인 여부) | `/api/auth/session` | GET | `{IDENTITY_SERVICE_URL}/api/auth/session` | 쿠키의 `access_token` |
| 로그아웃 | `/api/auth/logout` | POST | `{IDENTITY_SERVICE_URL}/api/auth/logout`(선택) | 선택(구현에 따름) |

### 3.2 사용량·대시보드 (BFF 미구현 · 제안 경로)

구현 시 BFF는 쿠키에서 토큰을 꺼내 **`{API_GATEWAY_URL}`** 로 프록시한다. Gateway는 `/api/v1/usage/**` 를 usage-service로 넘기며, 클라이언트 `Authorization`은 라우트 필터에서 제거된다([`application.yml`](../../services/api-gateway-service/src/main/resources/application.yml)). usage 측은 **`X-User-Id`** 등 게이트웨이 신뢰 헤더로 사용자를 식별한다(`UsageGatewayTrustFilter`).

| 화면·기능 (예시) | BFF (제안) | Method | Upstream (Gateway = `API_GATEWAY_URL`) | Query / Note |
|------------------|------------|--------|----------------------------------------|----------------|
| 대시보드 요약 KPI | `/api/usage/dashboard/summary` | GET | `/api/v1/usage/dashboard/summary` | `from`, `to` (ISO date) |
| 일별 시계열 차트 | `/api/usage/dashboard/series/daily` | GET | `/api/v1/usage/dashboard/series/daily` | `from`, `to` |
| 월별 시계열 | `/api/usage/dashboard/series/monthly` | GET | `/api/v1/usage/dashboard/series/monthly` | `from`, `to` |
| 모델별 집계 | `/api/usage/dashboard/by-model` | GET | `/api/v1/usage/dashboard/by-model` | `from`, `to` |
| 사용 로그 테이블 | `/api/usage/logs` | GET | `/api/v1/usage/logs` | `from`, `to`, 선택 `provider`, `model`, `page`, `size` |

**응답 형식:** 현재 usage REST는 `ApiResponse` 래핑 없이 DTO/리스트를 직접 반환한다. 프론트 타입은 컨트롤러 DTO(`UsageSummaryResponse`, `DailyUsagePoint` 등)와 맞춘다.

### 3.3 조직·팀·설정 (향후)

`/organizations`, `/teams`, `/settings` 등은 Identity·별도 관리 API 계약이 정해지면 본 절에 행을 추가한다. 현재 본 문서 범위는 **Identity BFF(§3.1)** 와 **Usage 대시보드 HTTP(§3.2)** 이다.

### 3.4 AI 프록시 (선택 · 향후)

클라이언트 → Gateway `POST /api/v1/ai/{provider}/**` 는 스트리밍·대용량 바디 때문에 BFF 경유가 무겁다. **브라우저가 Gateway를 직접 호출**할지, **서버 프록시 Route**를 둘지는 팀원 A·C 별도 합의. 계약 경로는 [gateway-proxy.md §2](./gateway-proxy.md) 참고.

---

## 4. 로컬·운영 시 게이트웨이 동작 차이 (팀원 A 연동)

| 모드 | Gateway 보안 | BFF → Gateway 호출 시 |
|------|----------------|------------------------|
| `GATEWAY_DEV_MODE=true` (기본) | `/api/v1/usage/**` 등 permitAll | `ProxyTrustHeadersGatewayFilter`가 JWT 컨텍스트가 없으면 요청의 **`X-User-Id` 헤더**를 요구한다. 로컬에서 BFF가 **Bearer만**내면 401이 날 수 있으므로, A·C 합의 하에 **`X-User-Id`를 BFF가 추가**하거나, 개발용으로 게이트웨이에 JWT 리소스 서버를 켜는 방식을 맞춘다. |
| `GATEWAY_DEV_MODE=false` | JWT 리소스 서버, `/api/v1/usage/**` authenticated | BFF는 **`Authorization: Bearer`** 만내면 되고, 게이트웨이가 JWT `sub` 등으로 `X-User-Id`를 붙인다. |

Identity 발급 JWT에서 `sub`은 현재 **사용자 이메일**이며, `userId` 클레임에 숫자 ID가 있다(`JwtTokenProvider`). usage 원장의 `userId` 문자열과 어떤 값을 맞출지는 **Proxy·Usage·Identity 간 이미 쓰는 식별자**와 통일해야 한다.

---

## 5. 변경 절차

- BFF 경로를 바꾸거나 upstream을 직접 호출(게이트웨이 우회)하려면 본 문서 버전을 올리고, 팀원 A(Gateway 라우트)·usage 담당과 정본을 맞춘다.
