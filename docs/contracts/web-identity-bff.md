# Web(Next.js) ↔ Identity 인증 BFF 계약

버전: 1.2  
관련: [docs/architecture.md](../architecture.md) §1.3, §3.3, [Identity 인증 API 계약](../identity-auth-api-contract.md)

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

---

## 6. 401 / 만료 처리 정책

- 백엔드 401 응답은 공통 JSON(`success=false`, `message`, `data=null`)을 유지한다.
- BFF는 Identity에서 받은 **401을 프론트에 그대로 전달**한다(상태 코드·본문 유지).

### 6.1 프론트에서 `/login` 리다이렉트 범위 (합의)

- **“모든 401을 받으면 즉시 `/login`으로 보낸다”는 방식은 지양**한다.
- **`auth-required`** 인 요청·페이지(로그인이 필요한 화면/흐름)에서만, 공통 처리로 **`/login` 리다이렉트**를 적용한다.
- **일반 API** 호출에서 401이 나오면 즉시 리다이렉트하지 않고, **화면에서 에러 상태(토스트/메시지 등)로 처리**한 뒤 필요 시 로그인을 유도한다.

위 범위는 초기 논의에서 “401 시 항상 로그인 페이지”로 읽힐 수 있는 문구와 구분되므로, **정본은 본 절(6.1)을 따른다.**

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
