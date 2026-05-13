# Web(Next.js) ↔ Identity 인증 BFF 계약

버전: 1.27  
관련: [docs/architecture.md](../architecture.md) §1.3, §3.3, §10.2, §13, [Identity 인증 API 계약](../identity-auth-api-contract.md), [Web·Gateway Usage BFF](./web-gateway-bff.md)(Usage BFF·`basePath` 호출 맵), [Web·Team BFF](./web-team-bff.md), [저장소 구조](../repository-structure.md) §6, [웹 경계](./web-split-boundary.md)(§2.4 로컬 `web-edge` Nginx)

**소스 트리:** BFF·화면의 **정본**은 `services/identity-service/web/` 이다. **공용 UI(Shadcn 래퍼·`cn`)** 는 루트 pnpm workspace **`@ai-usage/ui`**(`packages/ui`)를 참조한다([web-split-boundary.md §1.1](./web-split-boundary.md)). Identity vs Usage 라우트·미들웨어 매처는 [web-split-boundary.md](./web-split-boundary.md) §2·§3.

---

## 1. 목적

- **Identity Next 앱**(`services/identity-service/web/`)은 브라우저가 Identity 서비스를 직접 호출하지 않고, **Next Route Handler(BFF)** 를 통해 회원가입/로그인을 처리한다.
- 인증 토큰은 프론트 JavaScript에 노출하지 않고, **httpOnly 쿠키**로만 관리한다.

---

## 2. 엔드포인트 계약

| 구분 | Web BFF 브라우저 경로(Next 동일 오리진) | Upstream Identity |
|------|-----------------------|-------------------|
| 회원가입 | `POST /api/auth/signup` | `POST /api/auth/signup` |
| 비밀번호 찾기(재설정 메일 요청) | `POST /api/auth/forgot-password` | `POST /api/auth/forgot-password` |
| 비밀번호 재설정 | `POST /api/auth/reset-password` | `POST /api/auth/reset-password` |
| 로그인 | `POST /api/auth/login` | `POST /api/auth/login` |
| 외부 API 키 조회(개인) | `GET /api/auth/external-keys` | Gateway `GET /api/identity/auth/external-keys` |
| 외부 API 키 등록(개인) | `POST /api/auth/external-keys` | Gateway `POST /api/identity/auth/external-keys` |
| 외부 API 키 수정(개인) | `PUT /api/auth/external-keys/{id}` | Gateway `PUT /api/identity/auth/external-keys/{id}` |
| 외부 API 키 삭제 예약(개인) | `DELETE /api/auth/external-keys/{id}` (선택 쿼리 `gracePeriodDays`, `retainLogs`) | Gateway `DELETE /api/identity/auth/external-keys/{id}` (동일) |
| 외부 API 키 삭제 취소(개인) | `POST /api/auth/external-keys/{id}/deletion-cancel` | Gateway `POST /api/identity/auth/external-keys/{id}/deletion-cancel` |
| 세션(로그인 여부 단일 기준) | `GET /api/auth/session` | Gateway `GET /api/identity/auth/session` (BFF가 쿠키 JWT를 Bearer로 전달해 프록시) |
| 계정 삭제 | `POST /api/auth/delete-account` | Gateway `POST /api/identity/auth/delete-account` |
| 로그아웃 | `POST /api/auth/logout` | `POST /api/auth/logout` (선택 프록시; stateless이며 실질 로그아웃은 BFF의 쿠키 삭제) |

- Web BFF는 경로 성격에 따라 업스트림을 분리한다.
  - 인증 기본 플로우(`signup/login/forgot-password/reset-password/logout`)는 `IDENTITY_SERVICE_URL` 기반 직접 프록시
  - 세션/외부 API 키/관리 BFF는 `GATEWAY_URL` 또는 `WEB_GATEWAY_URL` 기반 Gateway 프록시
- 요청 **본문**이 있는 경로(회원가입·로그인 등)의 입력 검증은 Zod 스키마로 수행한다. `GET /api/auth/session`·`POST /api/auth/logout` 은 본문이 없으며, 로그아웃은 쿠키 삭제가 핵심이다.
- **`GET /api/auth/session`** 은 프론트가 “로그인됨/만료”를 판단할 때 사용하는 **단일 기준 엔드포인트**로 둔다. BFF 응답에도 `Cache-Control: no-store`를 적용한다(§8).

### 2.1 `GET /api/auth/session` 동작

1. 브라우저 → BFF: `Cookie`에 `access_token`(httpOnly, 로그인 시 설정)이 포함되면 자동 전송된다.
2. BFF → Gateway: `GET {GATEWAY_URL|WEB_GATEWAY_URL}/api/identity/auth/session`, 헤더 `Authorization: Bearer {access_token}`, `Accept: application/json`.
3. **`access_token` 쿠키가 없거나 값이 비어 있으면** BFF는 Identity를 호출하지 않고 `401` + `ApiResponse<null>` (`success=false`, `data=null`)로 응답한다. 메시지는 BFF에서 고정할 수 있다(예: 로그인 필요 안내).
4. **성공 시**(`Identity`가 `200` + `success=true`) BFF가 프론트에 돌려주는 `data` 필드는 업스트림과 **동일한 형태**이어야 한다. 필드 정의의 정본은 [Identity 인증 API 계약 §5](../identity-auth-api-contract.md)를 따른다.

```json
{
  "email": "user@example.com",
  "role": "USER",
  "authenticated": true
}
```

5. **Gateway/Identity가 `401` 등으로 거절**하면 상태 코드와 JSON 본문을 **그대로** 프론트에 전달한다(§6).
6. `IDENTITY_SERVICE_URL` 미설정 등 BFF 설정 오류·업스트림 연결 실패·업스트림 성공 응답이 계약과 맞지 않는 경우 등은 BFF가 `500`/`502` 등으로 처리할 수 있다. 프론트는 `success=false`와 `message`로 사용자 메시지를 구성한다.

### 2.2 `GET /api/auth/external-keys` 동작

1. 브라우저 → BFF: `GET /api/auth/external-keys`
2. **`access_token` 쿠키가 없거나 값이 비어 있으면** BFF는 Identity를 호출하지 않고 `401` + `ApiResponse<null>` (`success=false`, `data=null`)로 응답한다.
3. BFF → Gateway: `GET {GATEWAY_URL|WEB_GATEWAY_URL}/api/identity/auth/external-keys`
   - `Authorization: Bearer {access_token}`
   - `Accept: application/json`
4. **성공 시** Gateway/Identity의 상태 코드/본문(`ApiResponse`)을 가능한 그대로 전달한다. 응답에는 `Cache-Control: no-store`를 적용한다.
5. 목록 항목은 [Identity 인증 API 계약 §6](../identity-auth-api-contract.md)과 동일하며, **삭제 예정** 행에는 `deletionRequestedAt`, `permanentDeletionAt`, `deletionGraceDays`(선택) 등이 포함될 수 있다.
6. **Gateway/Identity가 `401` 등으로 거절**하면 상태 코드와 JSON 본문을 **그대로** 프론트에 전달한다(§6).
7. `IDENTITY_SERVICE_URL` 미설정, 업스트림 연결 실패, 업스트림 응답이 계약과 맞지 않는 경우 등은 BFF가 `500`/`502` 등으로 처리할 수 있다.

### 2.3 `POST /api/auth/external-keys` 동작

1. 브라우저 → BFF: `POST /api/auth/external-keys` (JSON body: `provider`, `externalKey`, `alias`, `monthlyBudgetUsd`)
2. BFF: 입력 본문을 Zod로 검증한다(요청 본문이므로 검증 대상).
3. **`access_token` 쿠키가 없거나 값이 비어 있으면** BFF는 Identity를 호출하지 않고 `401` + `ApiResponse<null>` (`success=false`, `data=null`)로 응답한다.
4. BFF → Gateway: `POST {GATEWAY_URL|WEB_GATEWAY_URL}/api/identity/auth/external-keys`
   - `Authorization: Bearer {access_token}`
   - `Content-Type: application/json`, `Accept: application/json`
5. **성공 시** Gateway/Identity의 상태 코드/본문(`ApiResponse`)을 가능한 그대로 전달한다. 응답에는 `Cache-Control: no-store`를 적용한다.
6. **Identity가 `400`/`401`/`409` 등으로 거절**하면 상태 코드와 JSON 본문을 **그대로** 프론트에 전달한다(§6). 동일 provider·동일 키 값에 대해 **활성 행이 이미 있으면** `409` 메시지 예: `이미 등록된 API 키입니다`. 동일 해시 키가 **삭제 예정 행으로만 존재하면** `409`로 거절하지 않고 기존 키를 ACTIVE로 복구해 등록 성공으로 처리한다. **동일 키가 Team 키로 이미 등록돼 있으면** `409` 메시지 예: `팀에 이미 등록된 API 키입니다`([identity-auth-api-contract §7](../identity-auth-api-contract.md)).
7. `IDENTITY_SERVICE_URL` 미설정, 업스트림 연결 실패, 업스트림 응답이 계약과 맞지 않는 경우 등은 BFF가 `500`/`502` 등으로 처리할 수 있다. 단, **외부 API 키 평문(`externalKey`)은 로그/에러 메시지에 포함하지 않는다.**

### 2.4 `PUT /api/auth/external-keys/{id}` 동작

1. 브라우저 → BFF: `PUT /api/auth/external-keys/{id}` (JSON body: `alias`, `monthlyBudgetUsd` 필수, `externalKey`/`provider` 선택)
2. BFF: 입력 본문을 Zod로 검증한다.
   - `alias`는 필수
   - `monthlyBudgetUsd`는 필수(0 이상, 소수점 둘째 자리까지)
   - `externalKey`를 보내면 `provider`도 함께 필수
3. **`access_token` 쿠키가 없거나 값이 비어 있으면** BFF는 Identity를 호출하지 않고 `401` + `ApiResponse<null>` (`success=false`, `data=null`)로 응답한다.
4. BFF → Gateway: `PUT {GATEWAY_URL|WEB_GATEWAY_URL}/api/identity/auth/external-keys/{id}`
   - `Authorization: Bearer {access_token}`
   - `Content-Type: application/json`, `Accept: application/json`
5. 성공/오류 모두 Identity의 상태 코드/JSON 본문을 가능한 그대로 전달하고, 응답에 `Cache-Control: no-store`를 적용한다.
6. 키 값을 바꿀 때 다른 행과의 중복·삭제 예정 충돌, Team 키와의 중복 메시지는 [identity-auth-api-contract §9](../identity-auth-api-contract.md)와 동일하다.

### 2.5 `DELETE /api/auth/external-keys/{id}` 동작 (삭제 예약)

1. 브라우저 → BFF: `DELETE /api/auth/external-keys/{id}`  
   - 선택 **쿼리** `gracePeriodDays`(정수): 생략 시 Identity 기본 **7일**, 허용 범위는 Identity 구현(현재 **0~365일**). `0`은 즉시 삭제이며, 잘못된 값은 업스트림이 `400`으로 거절할 수 있다.
2. **`access_token` 쿠키가 없거나 값이 비어 있으면** BFF는 Identity를 호출하지 않고 `401`로 응답한다.
3. BFF → Gateway: 브라우저 요청 URL의 **쿼리 문자열을 그대로 이어 붙여** `DELETE {GATEWAY_URL|WEB_GATEWAY_URL}/api/identity/auth/external-keys/{id}{?gracePeriodDays=…&retainLogs=…}` 로 프록시한다(구현: `services/identity-service/web/src/app/api/auth/external-keys/[id]/route.ts`).
4. 성공 시 Identity 응답(`deletionRequestedAt`, `permanentDeletionAt`, `deletionGraceDays` 등)을 그대로 전달한다. 유예 종료 후 물리 삭제는 스케줄러가 담당한다([identity-auth-api-contract §10.1](../identity-auth-api-contract.md)).
5. `retainLogs=false`로 물리 삭제가 완료되면 usage/agent의 키 기준 지출·사용 프로젝션도 삭제 대상이며, `retainLogs=true`면 키 삭제 후에도 기록은 보존한다.

### 2.6 `POST /api/auth/external-keys/{id}/deletion-cancel` 동작

1. 브라우저 → BFF: `POST /api/auth/external-keys/{id}/deletion-cancel`
2. **`access_token` 쿠키가 없거나 값이 비어 있으면** BFF는 Identity를 호출하지 않고 `401`로 응답한다.
3. BFF → Gateway: `POST {GATEWAY_URL|WEB_GATEWAY_URL}/api/identity/auth/external-keys/{id}/deletion-cancel`
4. 성공 시 Identity 응답을 그대로 전달한다.

### 2.7 `POST /api/auth/forgot-password` 동작

1. 브라우저 → BFF: `POST /api/auth/forgot-password`, JSON `{ "email": "…" }`
2. BFF: Zod로 이메일 형식을 검증한다.
3. BFF → Identity: `POST {IDENTITY_SERVICE_URL}/api/auth/forgot-password` (동일 본문)
4. **성공 시**(`200`) Identity의 `message`는 **이메일 존재 여부와 무관한 동일 문구**를 사용한다(이메일 열거 방지). BFF는 업스트림 본문을 그대로 전달한다. 응답에 `Cache-Control: no-store`를 적용한다.
5. Identity·SMTP 미구성 시에는 서버 로그에 재설정 링크가 남을 수 있다(로컬 개발). 운영에서는 `IDENTITY_WEB_PUBLIC_BASE_URL`·SMTP를 설정한다([identity-auth-api-contract](../identity-auth-api-contract.md)).

### 2.8 `POST /api/auth/reset-password` 동작

1. 브라우저 → BFF: `POST /api/auth/reset-password`, JSON `{ "token": "…", "password": "…", "passwordConfirm": "…" }` (`token`은 메일 링크의 쿼리 값)
2. BFF: Zod로 비밀번호 정책·일치 여부를 검증한다(회원가입과 동일 정책).
3. BFF → Identity: `POST {IDENTITY_SERVICE_URL}/api/auth/reset-password`
4. **성공 시** 비밀번호가 갱신되며, 웹은 안내 후 **`/login`** 으로 이동할 수 있다. 토큰이 잘못되었거나 만료된 경우 Identity는 `400`과 안내 메시지를 반환할 수 있다.

---

## 3. 회원가입 계약 (정합성)

- 브라우저→BFF 본문에는 **`role`을 넣지 않는다.** BFF(`POST /api/auth/signup` Route Handler)가 Identity로 보낼 때 **`role: "USER"`를 합쳐서** 전달한다(일반 가입자 고정).
- `passwordConfirm`은 필수이며, `password`와 일치해야 한다.
- 비밀번호 정책은 Identity와 동일하게 유지한다.
  - 소문자/숫자/특수문자 각각 1개 이상
  - 대문자 불가
  - 공백 불가
  - 길이 8~100자
- **웹 UX:** `services/identity-service/web/src/components/signup/signup-form.tsx` 는 BFF가 **`success`이고 `data`가 있는** 응답을 받은 뒤 곧바로 **`/login`** 으로 `router.replace` 한다. 별도의 가입 성공 안내 화면은 두지 않는다.

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
| `name` | `access_token` (`is_logged_in` 보조 쿠키도 함께 설정) |
| `httpOnly` | `true` |
| `sameSite` | `lax` |
| `path` | `/` |
| `secure` | `IDENTITY_WEB_SECURE_COOKIE` 우선, 미설정 시 `x-forwarded-proto`/요청 URL/prod 여부로 결정 |
| `maxAge` | `expiresInSeconds`(기본 3600초) |

---

## 5. 보호 API 호출 방식

1. 브라우저 → BFF: 쿠키 자동 전송
2. BFF → 백엔드 보호 API: `Authorization: Bearer {accessToken}` 헤더로 전달

### 5.1 Usage BFF 경로와 `GATEWAY_DEV_MODE`

- Usage BFF는 **`services/usage-service/web/`** 트리에 둔다(`src/app/api/usage/[[...path]]/route.ts`). 브라우저에서 보이는 경로는 **`NEXT_PUBLIC_BASE_PATH`(기본 `/dashboard`)** 아래 **`.../api/usage/...`** 형태가 된다([web-split-boundary.md §2.2](./web-split-boundary.md)). BFF는 **`API_GATEWAY_URL`로 `/api/v1/usage/...`** 를 프록시한다. **엔드포인트 표·환경 변수 정본은 [web-gateway-bff.md](./web-gateway-bff.md)**.
- 게이트웨이 **개발 모드**(`GATEWAY_DEV_MODE=true`, `gateway.dev-mode=true`)에서는 Usage 라우트에 **`X-User-Id`가 필수**이므로, BFF가 Identity **`GET /api/auth/session` 응답의 `email`** 을 읽어 `X-User-Id` 헤더로 붙인다.
- 그 **이메일**은 Identity JWT의 **`sub`** 와 동일하다([identity-auth-api-contract §4.3](../identity-auth-api-contract.md)). 운영에서 게이트웨이가 JWT만으로 `X-User-Id`를 세팅할 때와 **같은 문자열**이 Usage 원장·집계 키가 된다([gateway-proxy.md §4.2](./gateway-proxy.md)).

### 5.2 Identity 관리 API BFF (`/api/identity/v1/**`)

- **Identity 관리 REST**는 브라우저가 **`/api/identity/v1/...`** 로만 호출하고, BFF가 **`{GATEWAY_URL|WEB_GATEWAY_URL}/api/identity/v1/...`** 로 동일 메서드·쿼리·본문을 프록시한다. 구현: `services/identity-service/web/src/app/api/identity/[[...path]]/route.ts`.
- `access_token` **httpOnly 쿠키**가 없으면 BFF는 Identity를 호출하지 않고 `401`을 반환한다.
- 경로는 **`v1`으로 시작하는 세그먼트만** 허용한다(예: 브라우저 `GET /api/identity/v1/me/profile` → 업스트림 `GET /api/v1/me/profile`). **`/api/auth/*`** 는 §2의 전용 BFF 라우트를 쓴다.
- 응답 본문·상태 코드는 업스트림을 그대로 전달한다(캐시는 `Cache-Control: no-store`).
- 웹 **설정** 화면의 계정 요약은 `GET /api/auth/session`(§2)을 사용한다. 팀 도메인은 `team-service`로 분리되어 [web-team-bff.md](./web-team-bff.md)를 따른다. **`/teams`** 에서 팀 API를 호출할 때는 `web-edge`가 `/api/team/v1/*`를 Team BFF로 라우팅한다.

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

브라우저가 직접 보는 **페이지 라우트** 중 로그인이 필요한 영역은 Next.js **`middleware.ts`** 로 1차 게이트한다. 구현: `services/identity-service/web/middleware.ts`. **대시보드(`/dashboard` 및 `/dashboard/*`)** 는 Usage `web/` 소유이므로 Identity matcher에 포함하지 않는다([web-split-boundary.md](./web-split-boundary.md) §3). **단일 도메인 엣지**에서는 Nginx가 **`/dashboard`** 및 **`/dashboard/`** 요청을 Usage `web`으로 프록시하고, **`/dashboard2`** 등 미정의 경로는 404로 닫는다(§2.3, [`docker/web-edge/nginx.conf.template`](../../docker/web-edge/nginx.conf.template)).

| 항목 | 내용 |
|------|------|
| **matcher (정본)** | `/settings/:path*` |
| **판단 기준** | 요청에 **`access_token` httpOnly 쿠키**가 있거나, 보조 쿠키 **`is_logged_in=true`** 이면 통과. **JWT 서명·만료 검증은 여기서 하지 않는다** (쿠키만 없으면 로그인 유도). 토큰 유효성은 `GET /api/auth/session`(§2.1) 등 BFF·업스트림에서 판단한다. |
| **미통과 시** | `307` 리다이렉트 → `/login?next=<원래 pathname>` (`next`는 로그인 후 되돌아갈 경로; 소비 시 오픈 리다이렉트 방지를 위해 `services/identity-service/web/src/lib/auth/safe-next-path.ts` 등으로 검증한다). |
| **대응 라우트** | 위 접두사마다 App Router **`[[...path]]` optional catch-all** 페이지를 둔다. **사용량 대시보드**는 `services/usage-service/web/src/app/dashboard/[[...path]]/page.tsx` 및 Usage `middleware.ts`를 본다. |
| **현재 UI 성격** | **설정**은 세션(§2)을 사용한다. 팀 UI는 web-host/Team BFF 경계를 따른다. 서버 측 권한·테넌트 검증은 Identity·게이트웨이에 유지한다(프론트 규칙: `.cursor/rules/project-common-nextjs.mdc`). |

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

- BFF `POST /api/auth/logout`에서 `access_token` 및 `is_logged_in` 쿠키를 만료 처리한다.
- 저장 시와 동일한 옵션(`path`, `sameSite`, `secure`, `httpOnly`)을 사용한다.
- `maxAge: 0`과 함께 **`Expires`를 과거 시각**(예: `Thu, 01 Jan 1970 00:00:00 GMT`, 구현에서는 `expires: new Date(0)`)으로 명시해 브라우저 간 삭제를 보장한다.

---

## 8. 캐시/보안 정책

- 로그인/로그아웃/**`GET /api/auth/session`(세션 체크)** 응답에는 `Cache-Control: no-store`를 적용한다.
- **`GET`/`POST`/`PUT`/`DELETE /api/auth/external-keys`** 및 **`POST /api/auth/external-keys/{id}/deletion-cancel`** 응답에도 `Cache-Control: no-store`를 적용한다.
- MVP는 **Access Token only**(Refresh Token 미포함)로 운영하고, 만료 시 재로그인한다.
- CSRF는 MVP에서 `SameSite=Lax + BFF 경유`를 기본으로 하고, **차기 스프린트에서 상태 변경 API는 BFF 경유에 더해 `Origin/Referer` 검증(또는 CSRF 토큰) 적용을 표준으로 한다.**

---

## 9. 로컬 실행 참고

- **환경 파일:** `services/identity-service/web/.env`(샘플: `.env.example`)  
  `IDENTITY_SERVICE_URL`(로그인/회원가입/비밀번호 재설정/로그아웃 직접 프록시용)과 `GATEWAY_URL` 또는 `WEB_GATEWAY_URL`(세션/외부키/관리 API 프록시용)을 함께 맞춘다. 로컬 `web-edge` 사용 시 기본 게이트웨이 진입은 `http://localhost:8888` 기준이다.
- **루트 `.env` vs 호스트 `bootRun`:** `docker compose`는 저장소 루트의 **`.env`**만 자동 로드한다. **Identity Spring**을 IDE/`./gradlew bootRun`으로만 띄울 때는 루트 `.env`가 **자동으로 읽히지 않는다** — `POSTGRES_*` 등은 실행 구성·쉘 환경변수로 맞춘다. 반면 **Compose로 뜨는** `identity-web`·`usage-web`·`api-gateway-service` 등은 루트 `.env`의 변수를 받는다.
- **게이트웨이 스택:** Proxy·게이트웨이를 Compose로 올릴 때 **`GATEWAY_SHARED_SECRET`** 은 비어 있지 않게 유지한다([gateway-proxy.md §5.1](./gateway-proxy.md)).
- **단일 도메인(`profile: web` + `web-edge`):** 브라우저는 기본 **`http://localhost:8888`**(`WEB_EDGE_PORT`) 한 오리진으로 붙고, 경로 분기는 [`docker/web-edge/nginx.conf.template`](../../docker/web-edge/nginx.conf.template)·[web-split-boundary.md §2.3](./web-split-boundary.md) 정본을 따른다.
- **pnpm workspace:** 호스트에서 Next를 띄우기 전에 저장소 루트에서 **`pnpm install`**(전역 pnpm 없으면 `npx pnpm@9 install`)로 `packages/ui`·각 `web` 의존성을 맞춘다.
- 로컬 기본 실행 순서: Postgres(RabbitMQ 등) → **identity-service(Spring)** → **Identity `web`**(루트에서 **`pnpm --filter identity-web dev`** 또는 해당 `web/`에서 **`pnpm dev`**, 기본 포트 3000)
- 포트 충돌: Gateway·Identity·여러 `web` 인스턴스가 같은 포트를 쓰지 않도록 `README.md`·루트 `.env.example`를 본다.

---

## 10. 랜딩(공개 홈) · BFF 회귀 테스트

### 10.1 랜딩

- 앱 루트 **`/`** (`services/identity-service/web/src/app/page.tsx`)는 서버 컴포넌트로 메타데이터만 두고, 헤더·히어로 CTA는 클라이언트 컴포넌트 **`LandingHomeWithSession`** (`services/identity-service/web/src/components/landing/landing-header.tsx`)가 담당한다.
- 마운트 시 브라우저가 동일 오리진으로 **`GET /api/auth/session`** 을 호출할 때 **`fetch(..., { credentials: "include", cache: "no-store" })`** 를 쓴다. 응답은 공통 **`ApiResponse<SessionResponse>`** 로 파싱하고, **`data.authenticated === true`** 이면 로그인된 것으로 본다.
- **비로그인:** 내비·CTA는 **`/login`**, **`/signup`** 과 동일한 안내다.
- **로그인됨:** **대시보드** 링크는 Usage 공개 경로 **`/dashboard`** 로 이동한다. 구현은 `@ai-usage/shell`의 `usageEntryPublicPath()`를 사용한다. **설정** 등 Identity 앱 내부 링크는 **`/settings`** 등 상대 경로를 사용한다. 헤더에는 **`로그아웃`** 버튼(`LogoutButton`)이 있으며, 브라우저가 **`POST /api/auth/logout`**(§2 표·§7)을 호출한 뒤 **`/`** 으로 이동한다.
- 로그인 후 리다이렉트(`navigateAfterLogin`)도 동일한 오리진 규칙을 쓴다.
- **단일 오리진 기본:** 로컬/통합 환경에서는 `web-edge` 단일 오리진 경로 분기를 기본으로 한다. Usage 쪽 인증·401 처리는 [web-gateway-bff.md](./web-gateway-bff.md)·[web-split-boundary.md §4](./web-split-boundary.md)를 따른다.

### 10.2 Vitest(라우트 핸들러·미들웨어)

- BFF 인증 라우트는 구현 파일 옆에 **Vitest** 스펙을 둔다(`services/identity-service/web/`). 예:
  - `.../src/app/api/auth/session/route.test.ts`
  - `.../src/app/api/auth/logout/route.test.ts`
  - `.../src/app/api/auth/external-keys/route.test.ts`
  - `.../src/app/api/auth/external-keys/[id]/route.test.ts` — `PUT` 및 **`DELETE` 쿼리 전달(`gracePeriodDays`)** 회귀
  - `login`·`signup` Route Handler도 동일 패턴의 `route.test.ts`로 회귀 검증
  - `.../src/app/api/identity/[[...path]]/route.test.ts` — §5.2 관리 API BFF
- 미들웨어·보호 경로: `middleware.test.ts`, `protected-routes.test.ts` (§6.2).
- 실행: 저장소 루트에서 **`pnpm --filter identity-web test`**, 또는 (루트에서 `pnpm install` 후) 해당 `web/` 디렉터리에서 **`pnpm exec vitest run`** / **`npx vitest run`**. **E2E(실제 Identity 기동)** 는 §9 환경으로 별도 확인한다.
