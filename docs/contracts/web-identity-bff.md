# Web(Next.js) ↔ Identity 인증 BFF 계약

버전: 1.0  
관련: [docs/architecture.md](../architecture.md) §1.3, §3.3

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

- Web BFF는 `IDENTITY_SERVICE_URL` 환경 변수를 사용해 Identity로 프록시한다.
- BFF 입력 검증은 Zod 스키마로 수행한다.

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

- BFF는 `tokenType === "Bearer"` 인지 검증한다.
- 불일치 시 쿠키를 저장하지 않고 `502 Bad Gateway`를 반환한다.

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

- 백엔드 401 응답 형식은 공통 JSON(`success`, `message`, `data`)을 유지한다.
- BFF는 401을 그대로 전달한다.
- 프론트는 **auth-required** 요청/페이지에 대해서만 `/login` 리다이렉트 공통 처리를 적용한다.
  - 일반 API의 401은 화면 에러(토스트/메시지)로 처리 후 필요 시 로그인 유도한다.

---

## 7. 로그아웃 쿠키 삭제 규칙

- BFF 로그아웃 API에서 `access_token` 쿠키를 만료 처리한다.
- 저장 시와 동일한 옵션(`path`, `sameSite`, `secure`, `httpOnly`)을 사용한다.
- `maxAge: 0` + 과거 `expires`를 함께 명시해 삭제를 보장한다.

---

## 8. 캐시/보안 정책

- 로그인/로그아웃/세션 체크 응답에는 `Cache-Control: no-store`를 적용한다.
- MVP는 **Access Token only**(Refresh Token 미포함)로 운영하고, 만료 시 재로그인한다.
- CSRF는 MVP에서 `SameSite=Lax + BFF 경유`를 기본으로 하고, 차기 스프린트에서 상태 변경 API에 대해 `Origin/Referer` 검증(또는 CSRF 토큰)을 표준 적용한다.

---

## 9. 로컬 실행 참고

- `apps/web/.env`에 `IDENTITY_SERVICE_URL`을 설정한다.  
  예: `IDENTITY_SERVICE_URL=http://localhost:8080`
- 로컬 기본 실행 순서: Postgres → Identity → Web
- 포트 충돌 주의: Gateway와 Identity가 동시에 `8080`을 사용하지 않도록 환경별 포트를 조정한다.
