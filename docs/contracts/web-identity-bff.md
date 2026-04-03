# Web(Next.js) ↔ Identity 인증 BFF 계약

버전: 1.9  
관련: [docs/architecture.md](../architecture.md) §1.3, §3.3, §10.2, §13, [Identity 인증 API 계약](../identity-auth-api-contract.md), [Web·Gateway Usage BFF](./web-gateway-bff.md)(`/api/usage/**` 호출 맵), [저장소 구조](../repository-structure.md) §6, [웹 경계](./web-split-boundary.md)

**소스 트리:** BFF·화면의 **정본**은 `services/identity-service/web/` 이다. Identity vs Usage 라우트·미들웨어 매처는 [web-split-boundary.md](./web-split-boundary.md) §2·§3.

---

## 1. 목적

- **Identity Next 앱**(`services/identity-service/web/`)은 브라우저가 Identity 서비스를 직접 호출하지 않고, **Next Route Handler(BFF)** 를 통해 회원가입/로그인을 처리한다.
- 인증 토큰은 프론트 JavaScript에 노출하지 않고, **httpOnly 쿠키**로만 관리한다.

---

## 2. 엔드포인트 계약

| 구분 | Web BFF 브라우저 경로(Next 동일 오리진) | Upstream Identity |
|------|-----------------------|-------------------|
| 회원가입 | `POST /api/auth/signup` | `POST /api/auth/signup` |
| 로그인 | `POST /api/auth/login` | `POST /api/auth/login` |
| 외부 API 키 조회(개인) | `GET /api/auth/external-keys` | `GET /api/auth/external-keys` |
| 외부 API 키 등록(개인) | `POST /api/auth/external-keys` | `POST /api/auth/external-keys` |
| 외부 API 키 수정(개인) | 미지원 (현재 BFF 미구현) | `PUT /api/auth/external-keys/{id}` |
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

### 2.2 `GET /api/auth/external-keys` 동작

1. 브라우저 → BFF: `GET /api/auth/external-keys`
2. **`access_token` 쿠키가 없거나 값이 비어 있으면** BFF는 Identity를 호출하지 않고 `401` + `ApiResponse<null>` (`success=false`, `data=null`)로 응답한다.
3. BFF → Identity: `GET {IDENTITY_SERVICE_URL}/api/auth/external-keys`
   - `Authorization: Bearer {access_token}`
   - `Accept: application/json`
4. **성공 시** Identity의 상태 코드/본문(`ApiResponse`)을 가능한 그대로 전달한다. 응답에는 `Cache-Control: no-store`를 적용한다.
5. **Identity가 `401` 등으로 거절**하면 상태 코드와 JSON 본문을 **그대로** 프론트에 전달한다(§6).
6. `IDENTITY_SERVICE_URL` 미설정, 업스트림 연결 실패, 업스트림 응답이 계약과 맞지 않는 경우 등은 BFF가 `500`/`502` 등으로 처리할 수 있다.

### 2.3 `POST /api/auth/external-keys` 동작

1. 브라우저 → BFF: `POST /api/auth/external-keys` (JSON body: `provider`, `externalKey`, `alias`)
2. BFF: 입력 본문을 Zod로 검증한다(요청 본문이므로 검증 대상).
3. **`access_token` 쿠키가 없거나 값이 비어 있으면** BFF는 Identity를 호출하지 않고 `401` + `ApiResponse<null>` (`success=false`, `data=null`)로 응답한다.
4. BFF → Identity: `POST {IDENTITY_SERVICE_URL}/api/auth/external-keys`
   - `Authorization: Bearer {access_token}`
   - `Content-Type: application/json`, `Accept: application/json`
5. **성공 시** Identity의 상태 코드/본문(`ApiResponse`)을 가능한 그대로 전달한다. 응답에는 `Cache-Control: no-store`를 적용한다.
6. **Identity가 `400`/`401`/`409` 등으로 거절**하면 상태 코드와 JSON 본문을 **그대로** 프론트에 전달한다(§6).
7. `IDENTITY_SERVICE_URL` 미설정, 업스트림 연결 실패, 업스트림 응답이 계약과 맞지 않는 경우 등은 BFF가 `500`/`502` 등으로 처리할 수 있다. 단, **외부 API 키 평문(`externalKey`)은 로그/에러 메시지에 포함하지 않는다.**

### 2.4 `PUT /api/auth/external-keys/{id}` 지원 상태

- Identity 백엔드는 `PUT /api/auth/external-keys/{id}`를 지원한다([Identity 인증 API 계약](../identity-auth-api-contract.md) §9).
- 그러나 현재 BFF(`services/identity-service/web/src/app/api/auth/external-keys/route.ts`)는 `GET`/`POST`만 구현되어 있다.
- 따라서 브라우저에서 BFF 경유로 외부 API 키 수정이 필요하면, BFF에 `PUT` 핸들러를 추가해 업스트림 `PUT` 프록시를 구현해야 한다.
- 본 문서 버전에서는 Web BFF의 `PUT /api/auth/external-keys/{id}`를 **미지원**으로 정의한다.

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

- Usage BFF는 **`services/usage-service/web/`** 트리에 둔다(`src/app/api/usage/[[...path]]/route.ts`). **`API_GATEWAY_URL`로 `/api/v1/usage/...`** 프록시. **엔드포인트 표·환경 변수 정본은 [web-gateway-bff.md](./web-gateway-bff.md)**.
- 게이트웨이 **개발 모드**(`GATEWAY_DEV_MODE=true`, `gateway.dev-mode=true`)에서는 Usage 라우트에 **`X-User-Id`가 필수**이므로, BFF가 Identity **`GET /api/auth/session` 응답의 `email`** 을 읽어 `X-User-Id` 헤더로 붙인다.
- 그 **이메일**은 Identity JWT의 **`sub`** 와 동일하다([identity-auth-api-contract §4.3](../identity-auth-api-contract.md)). 운영에서 게이트웨이가 JWT만으로 `X-User-Id`를 세팅할 때와 **같은 문자열**이 Usage 원장·집계 키가 된다([gateway-proxy.md §4.2](./gateway-proxy.md)).

### 5.2 Identity 관리 API BFF (`/api/identity/v1/**`)

- 조직·팀 등 **Identity 보호 REST**(게이트웨이가 아닌 Identity 직접)는 브라우저가 **`/api/identity/v1/...`** 로만 호출하고, BFF가 **`{IDENTITY_SERVICE_URL}/api/v1/...`** 로 동일 메서드·쿼리·본문을 프록시한다. 구현: `services/identity-service/web/src/app/api/identity/[[...path]]/route.ts`.
- `access_token` **httpOnly 쿠키**가 없으면 BFF는 Identity를 호출하지 않고 `401`을 반환한다.
- 경로는 **`v1`으로 시작하는 세그먼트만** 허용한다(예: 브라우저 `GET /api/identity/v1/me/organizations` → 업스트림 `GET /api/v1/me/organizations`). **`/api/auth/*`** 는 §2의 전용 BFF 라우트를 쓴다.
- 응답 본문·상태 코드는 업스트림을 그대로 전달한다(캐시는 `Cache-Control: no-store`).
- 웹 **설정** 화면의 계정 요약은 `GET /api/auth/session`(§2)을 사용한다. **조직·팀 목록**은 Identity 서비스가 노출하는 예시 경로로 `GET /api/v1/me/organizations`, `GET /api/v1/me/teams` 를 두고, 응답 `data`는 공통 `ApiResponse`([identity-auth-api-contract §2](../identity-auth-api-contract.md))로 감싼 배열·객체와 맞춘다.

---

## 6. 401 / 만료 처리 정책

- 백엔드 401 응답은 공통 JSON(`success=false`, `message`, `data=null`)을 유지한다.
- BFF는 Identity에서 받은 **401을 프론트에 그대로 전달**한다(상태 코드·본문 유지).

### 6.1 프론트에서 `/login` 리다이렉트 범위 (합의)

- **“모든 401을 받으면 즉시 `/login`으로 보낸다”는 방식은 지양**한다.
- **`auth-required`** 인 요청·페이지(로그인이 필요한 화면/흐름)에서만, 공통 처리로 **`/login` 리다이렉트**를 적용한다.
- **일반 API** 호출에서 401이 나오면 즉시 리다이렉트하지 않고, **화면에서 에러 상태(토스트/메시지 등)로 처리**한 뒤 필요 시 로그인을 유도한다.

위 범위는 초기 논의에서 “401 시 항상 로그인 페이지”로 읽힐 수 있는 문구와 구분되므로, **정본은 본 절(6.1)을 따른다.**

### 6.2 보호 페이지(App Router) 및 미들웨어 (Identity `web/`)

브라우저가 직접 보는 **페이지 라우트** 중 로그인이 필요한 영역은 Next.js **`middleware.ts`** 로 1차 게이트한다. 구현: `services/identity-service/web/middleware.ts`. **대시보드(`/dashboard`)** 는 Usage `web/` 소유이므로 Identity matcher에 포함하지 않는다([web-split-boundary.md](./web-split-boundary.md) §3).

| 항목 | 내용 |
|------|------|
| **matcher (정본)** | `/settings/:path*`, `/organizations/:path*`, `/teams/:path*` |
| **판단 기준** | 요청에 **`access_token` httpOnly 쿠키**가 있고 값이 비어 있지 않으면 통과. **JWT 서명·만료 검증은 여기서 하지 않는다** (쿠키만 없으면 로그인 유도). 토큰 유효성은 `GET /api/auth/session`(§2.1) 등 BFF·업스트림에서 판단한다. |
| **미통과 시** | `307` 리다이렉트 → `/login?next=<원래 pathname>` (`next`는 로그인 후 되돌아갈 경로; 소비 시 오픈 리다이렉트 방지를 위해 `services/identity-service/web/src/lib/auth/safe-next-path.ts` 등으로 검증한다). |
| **대응 라우트** | 위 접두사마다 App Router **`[[...path]]` optional catch-all** 페이지를 둔다. **사용량 대시보드**는 `services/usage-service/web/src/app/dashboard/[[...path]]/page.tsx` 및 Usage `middleware.ts`를 본다. |
| **현재 UI 성격** | **설정**은 세션(§2)·**조직·팀**은 §5.2 BFF·Identity 관리 API와 연동한다(업스트림 미구현 시 빈 목록·안내). 서버 측 권한·테넌트 검증은 Identity·게이트웨이에 유지한다(프론트 규칙: `.cursor/rules/project-common-nextjs.mdc`). |

**유지보수:** matcher에 경로를 추가·변경하면 (1) 동일 접두사의 `app/<segment>/[[...path]]/page.tsx`(또는 합의된 라우트)를 추가하거나, (2) 의도적으로 페이지가 없다면 matcher에서 해당 패턴을 제거한다. 회귀 방지용 테스트: `services/identity-service/web/middleware.test.ts`, `services/identity-service/web/src/app/protected-routes.test.ts`.

#### 6.2.1 샘플 curl (로컬, Identity BFF 호스트만)

아래는 **Next 개발 서버가 `http://localhost:3000`** 이고 BFF가 Identity로 프록시한다고 할 때의 예이다(`PORT`·`basePath`가 다르면 호스트를 바꾼다).

```bash
# 세션(쿠키 없음 → 401 예상)
curl -sS -i "http://localhost:3000/api/auth/session"

# 회원가입(BFF → Identity)
curl -sS -i -X POST "http://localhost:3000/api/auth/signup" \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"abc123!@","passwordConfirm":"abc123!@","name":"U"}'
```

---

## 7. 로그아웃 쿠키 삭제 규칙

- BFF `POST /api/auth/logout`에서 `access_token` 쿠키를 만료 처리한다.
- 저장 시와 동일한 옵션(`path`, `sameSite`, `secure`, `httpOnly`)을 사용한다.
- `maxAge: 0`과 함께 **`Expires`를 과거 시각**(예: `Thu, 01 Jan 1970 00:00:00 GMT`, 구현에서는 `expires: new Date(0)`)으로 명시해 브라우저 간 삭제를 보장한다.

---

## 8. 캐시/보안 정책

- 로그인/로그아웃/**`GET /api/auth/session`(세션 체크)** 응답에는 `Cache-Control: no-store`를 적용한다.
- **`GET /api/auth/external-keys` / `POST /api/auth/external-keys`** 응답에도 `Cache-Control: no-store`를 적용한다.
- 추후 BFF가 `PUT /api/auth/external-keys/{id}`를 지원하면 해당 응답에도 동일하게 `Cache-Control: no-store`를 적용한다.
- MVP는 **Access Token only**(Refresh Token 미포함)로 운영하고, 만료 시 재로그인한다.
- CSRF는 MVP에서 `SameSite=Lax + BFF 경유`를 기본으로 하고, **차기 스프린트에서 상태 변경 API는 BFF 경유에 더해 `Origin/Referer` 검증(또는 CSRF 토큰) 적용을 표준으로 한다.**

---

## 9. 로컬 실행 참고

- **환경 파일:** `services/identity-service/web/.env`(샘플: `.env.example`)  
  `IDENTITY_SERVICE_URL` 예: `http://localhost:8090`(Identity 기본 포트에 맞출 것; 게이트웨이 `8080`과 혼동 방지)
- 로컬 기본 실행 순서: Postgres(RabbitMQ 등) → **identity-service(Spring)** → **Identity `web`(`pnpm dev`, 기본 3000)**
- 포트 충돌: Gateway·Identity·여러 `web` 인스턴스가 같은 포트를 쓰지 않도록 `README.md`·루트 `.env.example`를 본다.

---

## 10. 랜딩(공개 홈) · BFF 회귀 테스트

### 10.1 랜딩

- 앱 루트 **`/`** (`services/identity-service/web/src/app/page.tsx`)는 제품 소개용 **최소 내비게이션**을 둔다. **로그인**·**회원가입**은 각각 **`/login`**, **`/signup`** 으로 연결한다(계약 변경 없음, 진입점 안내용).

### 10.2 Vitest(라우트 핸들러·미들웨어)

- BFF 인증 라우트는 구현 파일 옆에 **Vitest** 스펙을 둔다(`services/identity-service/web/`). 예:
  - `.../src/app/api/auth/session/route.test.ts`
  - `.../src/app/api/auth/logout/route.test.ts`
  - `.../src/app/api/auth/external-keys/route.test.ts`
  - `login`·`signup` Route Handler도 동일 패턴의 `route.test.ts`로 회귀 검증
  - `.../src/app/api/identity/[[...path]]/route.test.ts` — §5.2 관리 API BFF
- 미들웨어·보호 경로: `middleware.test.ts`, `protected-routes.test.ts` (§6.2).
- 실행: 해당 `web/` 디렉터리에서 **`pnpm exec vitest run`** 또는 **`npx vitest run`**. **E2E(실제 Identity 기동)** 는 §9 환경으로 별도 확인한다.
