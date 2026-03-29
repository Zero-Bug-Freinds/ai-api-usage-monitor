# Web(Next.js) ↔ Identity 인증 BFF 계약

버전: 1.6  
관련: [docs/architecture.md](../architecture.md) §1.3, §3.3, [Identity 인증 API 계약](../identity-auth-api-contract.md), [Web·Gateway Usage BFF](./web-gateway-bff.md)(`/api/usage/**` 호출 맵), [저장소 구조](../repository-structure.md) §6

---

## 1. 목적

- `apps/web`는 브라우저가 Identity 서비스를 직접 호출하지 않고, **Next Route Handler(BFF)** 를 통해 회원가입/로그인을 처리한다.
- 인증 토큰은 프론트 JavaScript에 노출하지 않고, **httpOnly 쿠키**로만 관리한다.

---

## 2. 엔드포인트 계약

| 구분 | Web BFF (`apps/web`) | Upstream Identity |
|------|-----------------------|-------------------|
| 회원가입 | `POST /api/auth/signup` | `POST /api/auth/signup` |
| 로그인 | `POST /api/auth/login` | `POST /api/auth/login` |
| 세션(로그인 여부 단일 기준) | `GET /api/auth/session` | `GET /api/auth/session` (BFF가 쿠키 JWT를 Bearer로 전달해 프록시) |
| 로그아웃 | `POST /api/auth/logout` | `POST /api/auth/logout` (선택 프록시; stateless이며 실질 로그아웃은 BFF의 쿠키 삭제) |

- Web BFF는 `IDENTITY_SERVICE_URL` 환경 변수를 사용해 Identity로 프록시한다.
- 요청 **본문**이 있는 경로(회원가입·로그인 등)의 입력 검증은 Zod 스키마로 수행한다. `GET /api/auth/session`·`POST /api/auth/logout` 은 본문이 없으며, 로그아웃은 쿠키 삭제가 핵심이다.
- **`GET /api/auth/session`** 은 프론트가 “로그인됨/만료”를 판단할 때 사용하는 **단일 기준 엔드포인트**로 둔다. BFF 응답에도 `Cache-Control: no-store`를 적용한다(§8).

### 2.1 `GET /api/auth/session` 동작

1. 브라우저 → BFF: `Cookie`에 `access_token`(httpOnly, 로그인 시 설정)이 포함되면 자동 전송된다.
2. BFF → Identity: `GET {IDENTITY_SERVICE_URL}/api/auth/session`, 헤더 `Authorization: Bearer {access_token}`, `Accept: application/json`.
3. **`access_token` 쿠키가 없거나 값이 비어 있으면** BFF는 Identity를 호출하지 않고 `401` + `ApiResponse<null>` (`success=false`, `data=null`)로 응답한다. 메시지는 BFF에서 고정할 수 있다(예: 로그인 필요 안내).
4. **성공 시**(`Identity`가 `200` + `success=true`) BFF가 프론트에 돌려주는 `data` 필드는 업스트림과 **동일한 형태**이어야 한다. 필드 정의의 정본은 [Identity 인증 API 계약 §5](../identity-auth-api-contract.md)를 따른다.

```json
{
  "email": "user@example.com",
  "role": "USER",
  "authenticated": true
}
```

5. **Identity가 `401` 등으로 거절**하면 상태 코드와 JSON 본문을 **그대로** 프론트에 전달한다(§6).
6. `IDENTITY_SERVICE_URL` 미설정 등 BFF 설정 오류·업스트림 연결 실패·업스트림 성공 응답이 계약과 맞지 않는 경우 등은 BFF가 `500`/`502` 등으로 처리할 수 있다. 프론트는 `success=false`와 `message`로 사용자 메시지를 구성한다.

---

## 3. 회원가입 계약 (정합성)

- `passwordConfirm`은 필수이며, `password`와 일치해야 한다.
- 비밀번호 정책은 Identity와 동일하게 유지한다.
  - 소문자/숫자/특수문자 각각 1개 이상
  - 대문자 불가
  - 공백 불가
  - 길이 8~100자

---

## 4. 로그인 응답 처리 계약

### 4.1 토큰 저장 위치

- BFF가 Identity 로그인 응답의 `accessToken`을 받아, 브라우저에 **httpOnly 쿠키**로 저장한다.
- 프론트(JavaScript)는 토큰 원문에 직접 접근하지 않는다.

### 4.2 `tokenType` 검증

- BFF는 로그인 응답의 `tokenType`이 정확히 **`Bearer`** 인지 검증한다.
- 불일치 시 토큰(쿠키)을 저장하지 않고 **`502 Bad Gateway`** 로만 응답한다.
- **에러 코드 통일:** `tokenType` 불일치는 업스트림(Identity) 계약 위반에 가깝다. **`502`로 고정**하고, **`500`과 혼용하지 않는다**(프론트 예외 처리 분기 최소화).

### 4.3 쿠키 옵션

| 항목 | 값 |
|------|----|
| `name` | `access_token` |
| `httpOnly` | `true` |
| `sameSite` | `lax` |
| `path` | `/` |
| `secure` | `production=true`, `local=false` |
| `maxAge` | `expiresInSeconds`(기본 3600초) |

---

## 5. 보호 API 호출 방식

1. 브라우저 → BFF: 쿠키 자동 전송
2. BFF → 백엔드 보호 API: `Authorization: Bearer {accessToken}` 헤더로 전달

### 5.1 Usage 경로(`GET /api/usage/**`)와 `GATEWAY_DEV_MODE`

- BFF는 `API_GATEWAY_URL`로 `/api/v1/usage/...` 를 프록시한다(`apps/web/src/app/api/usage/[[...path]]/route.ts`). **엔드포인트 표·환경 변수 정본은 [web-gateway-bff.md](./web-gateway-bff.md)**.
- 게이트웨이 **개발 모드**(`GATEWAY_DEV_MODE=true`, `gateway.dev-mode=true`)에서는 Usage 라우트에 **`X-User-Id`가 필수**이므로, BFF가 Identity **`GET /api/auth/session` 응답의 `email`** 을 읽어 `X-User-Id` 헤더로 붙인다.
- 그 **이메일**은 Identity JWT의 **`sub`** 와 동일하다([identity-auth-api-contract §4.3](../identity-auth-api-contract.md)). 운영에서 게이트웨이가 JWT만으로 `X-User-Id`를 세팅할 때와 **같은 문자열**이 Usage 원장·집계 키가 된다([gateway-proxy.md §4.2](./gateway-proxy.md)).

### 5.2 Identity 관리 API BFF (`/api/identity/v1/**`)

- 조직·팀 등 **Identity 보호 REST**(게이트웨이가 아닌 Identity 직접)는 브라우저가 **`/api/identity/v1/...`** 로만 호출하고, BFF가 **`{IDENTITY_SERVICE_URL}/api/v1/...`** 로 동일 메서드·쿼리·본문을 프록시한다. 구현: `apps/web/src/app/api/identity/[[...path]]/route.ts`.
- `access_token` **httpOnly 쿠키**가 없으면 BFF는 Identity를 호출하지 않고 `401`을 반환한다.
- 경로는 **`v1`으로 시작하는 세그먼트만** 허용한다(예: 브라우저 `GET /api/identity/v1/me/organizations` → 업스트림 `GET /api/v1/me/organizations`). **`/api/auth/*`** 는 §2의 전용 BFF 라우트를 쓴다.
- 응답 본문·상태 코드는 업스트림을 그대로 전달한다(캐시는 `Cache-Control: no-store`).
- 웹 **설정** 화면의 계정 요약은 `GET /api/auth/session`(§2)을 사용한다. **조직·팀 목록**은 팀원 B가 구현하는 예시 경로로 `GET /api/v1/me/organizations`, `GET /api/v1/me/teams` 를 두고, 응답 `data`는 공통 `ApiResponse`([identity-auth-api-contract §2](../identity-auth-api-contract.md))로 감싼 배열·객체와 맞춘다.

---

## 6. 401 / 만료 처리 정책

- 백엔드 401 응답은 공통 JSON(`success=false`, `message`, `data=null`)을 유지한다.
- BFF는 Identity에서 받은 **401을 프론트에 그대로 전달**한다(상태 코드·본문 유지).

### 6.1 프론트에서 `/login` 리다이렉트 범위 (합의)

- **“모든 401을 받으면 즉시 `/login`으로 보낸다”는 방식은 지양**한다.
- **`auth-required`** 인 요청·페이지(로그인이 필요한 화면/흐름)에서만, 공통 처리로 **`/login` 리다이렉트**를 적용한다.
- **일반 API** 호출에서 401이 나오면 즉시 리다이렉트하지 않고, **화면에서 에러 상태(토스트/메시지 등)로 처리**한 뒤 필요 시 로그인을 유도한다.

위 범위는 초기 논의에서 “401 시 항상 로그인 페이지”로 읽힐 수 있는 문구와 구분되므로, **정본은 본 절(6.1)을 따른다.**

### 6.2 보호 페이지(App Router) 및 미들웨어 (`apps/web`)

브라우저가 직접 보는 **페이지 라우트** 중 로그인이 필요한 영역은 Next.js **`middleware.ts`** 로 1차 게이트한다. 구현 위치: `apps/web/middleware.ts`.

| 항목 | 내용 |
|------|------|
| **matcher (정본)** | `/dashboard/:path*`, `/settings/:path*`, `/organizations/:path*`, `/teams/:path*` |
| **판단 기준** | 요청에 **`access_token` httpOnly 쿠키**가 있고 값이 비어 있지 않으면 통과. **JWT 서명·만료 검증은 여기서 하지 않는다** (쿠키만 없으면 로그인 유도). 토큰 유효성은 `GET /api/auth/session`(§2.1) 등 BFF·업스트림에서 판단한다. |
| **미통과 시** | `307` 리다이렉트 → `/login?next=<원래 pathname>` (`next`는 로그인 후 되돌아갈 경로; 소비 시 오픈 리다이렉트 방지를 위해 `apps/web/src/lib/auth/safe-next-path.ts`의 `getSafeNextPath` 등으로 검증한다). |
| **대응 라우트** | 위 접두사마다 App Router **`[[...path]]` optional catch-all** 페이지를 둔다. 예: `apps/web/src/app/dashboard/[[...path]]/page.tsx`. 하위 경로(예: `/dashboard/reports`)도 동일 세그먼트에서 처리해 matcher와 **404 불일치**를 피한다. |
| **현재 UI 성격** | 대시보드는 사용량 UI를 둔다. **설정**은 세션(§2)·**조직·팀**은 §5.2 BFF·Identity 관리 API와 연동한다(업스트림 미구현 시 빈 목록·안내). 서버 측 권한·테넌트 검증은 Identity·게이트웨이에 유지한다(프론트 규칙: `.cursor/rules/project-common-nextjs.mdc`). |

**유지보수:** matcher에 경로를 추가·변경하면 (1) 동일 접두사의 `app/<segment>/[[...path]]/page.tsx`(또는 합의된 라우트)를 추가하거나, (2) 의도적으로 페이지가 없다면 matcher에서 해당 패턴을 제거한다. 회귀 방지용 테스트: `apps/web/middleware.test.ts`, `apps/web/src/app/protected-routes.test.ts`.

---

## 7. 로그아웃 쿠키 삭제 규칙

- BFF `POST /api/auth/logout`에서 `access_token` 쿠키를 만료 처리한다.
- 저장 시와 동일한 옵션(`path`, `sameSite`, `secure`, `httpOnly`)을 사용한다.
- `maxAge: 0`과 함께 **`Expires`를 과거 시각**(예: `Thu, 01 Jan 1970 00:00:00 GMT`, 구현에서는 `expires: new Date(0)`)으로 명시해 브라우저 간 삭제를 보장한다.

---

## 8. 캐시/보안 정책

- 로그인/로그아웃/**`GET /api/auth/session`(세션 체크)** 응답에는 `Cache-Control: no-store`를 적용한다.
- MVP는 **Access Token only**(Refresh Token 미포함)로 운영하고, 만료 시 재로그인한다.
- CSRF는 MVP에서 `SameSite=Lax + BFF 경유`를 기본으로 하고, **차기 스프린트에서 상태 변경 API는 BFF 경유에 더해 `Origin/Referer` 검증(또는 CSRF 토큰) 적용을 표준으로 한다.**

---

## 9. 로컬 실행 참고

- `apps/web/.env`에 `IDENTITY_SERVICE_URL`을 설정한다.  
  예: `IDENTITY_SERVICE_URL=http://localhost:8080`
- 로컬 기본 실행 순서: Postgres → Identity → Web
- 포트 충돌 주의: Gateway와 Identity가 동시에 `8080`을 사용하지 않도록 환경별 포트를 조정한다.

---

## 10. 랜딩(공개 홈) · BFF 회귀 테스트

### 10.1 랜딩

- 앱 루트 **`/`** (`apps/web/src/app/page.tsx`)는 제품 소개용 **최소 내비게이션**을 둔다. **로그인**·**회원가입**은 각각 **`/login`**, **`/signup`** 으로 연결한다(계약 변경 없음, 진입점 안내용).

### 10.2 Vitest(라우트 핸들러·미들웨어)

- BFF 인증 라우트는 구현 파일 옆에 **Vitest** 스펙을 둔다. 예:
  - `apps/web/src/app/api/auth/session/route.test.ts` — 쿠키 없음·`IDENTITY_SERVICE_URL` 미설정·업스트림 프록시·401·형식 오류·연결 실패 등
  - `apps/web/src/app/api/auth/logout/route.test.ts` — 쿠키 삭제·선택적 업스트림 `POST /api/auth/logout`·업스트림 실패 시에도 쿠키 삭제
  - `login`·`signup` Route Handler도 동일 패턴의 `route.test.ts`로 회귀 검증
  - `apps/web/src/app/api/identity/[[...path]]/route.test.ts` — §5.2 관리 API BFF(쿠키·`v1` 접두·업스트림 프록시)
- 미들웨어·보호 경로 정합성은 **`apps/web/middleware.test.ts`**, **`apps/web/src/app/protected-routes.test.ts`** (§6.2 유지보수 문구와 동일).
- 실행: 저장소 루트에서 `cd apps/web` 후 **`npx vitest run`** (또는 CI에서 동일). **E2E(실제 Identity 기동)** 는 §9 환경으로 별도 확인한다.
