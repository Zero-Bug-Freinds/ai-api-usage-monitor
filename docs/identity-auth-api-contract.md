# Identity 인증 API 계약 (백엔드)

버전: 1.1  
관련: [docs/architecture.md](../docs/architecture.md) §1.3, [docs/contracts/web-identity-bff.md](./docs/contracts/web-identity-bff.md)

---

## 1. 목적

- `services/identity-service`의 인증 API 계약을 명확히 정의한다.
- Web BFF(`apps/web`)와의 인증 연동 시 필요한 응답 형식, 보안 헤더, 오류 코드를 고정한다.

---

## 2. 공통 응답 형식

모든 인증 API는 아래 구조를 따른다.

```json
{
  "success": true,
  "message": "설명 메시지",
  "data": {}
}
```

- 실패 응답은 `success=false`, `data=null`을 사용한다.
- 예외 응답도 공통 `ApiResponse` 포맷을 유지한다.

---

## 3. 엔드포인트 계약


| 메서드    | 경로                  | 인증  | 설명                       |
| ------ | ------------------- | --- | ------------------------ |
| `POST` | `/api/auth/signup`  | 불필요 | 회원가입                     |
| `POST` | `/api/auth/login`   | 불필요 | 로그인 및 액세스 토큰 발급          |
| `GET`  | `/api/auth/session` | 필요  | 세션(인증 상태) 확인             |
| `POST` | `/api/auth/logout`  | 불필요 | 로그아웃 신호 응답(BFF 쿠키 삭제 유도) |


---

## 4. 로그인 계약

### 4.1 성공 응답 `data`

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresInSeconds": 3600
}
```

- `tokenType`은 반드시 `Bearer`를 반환한다.
- 계약 위반(`tokenType != Bearer`)은 서버에서 `502 Bad Gateway`로 처리한다.

### 4.2 캐시 정책

- `POST /api/auth/login` 응답에는 `Cache-Control: no-store`를 설정한다.

### 4.3 액세스 JWT 클레임 (API Gateway·Usage 정합)

- 구현 정본: `services/identity-service/.../JwtTokenProvider.generateAccessToken`.
- **`sub`:** 로그인 사용자 **이메일**(문자열). API Gateway(운영 JWT 모드)가 이 값을 **`X-User-Id`** 로 downstream(Proxy·Usage)에 전달한다. Usage 원장·대시보드 집계의 `user_id`는 이 문자열과 일치해야 한다([gateway-proxy.md §4.2](./contracts/gateway-proxy.md)).
- **`userId`:** 내부 DB 사용자 PK 등(선택 클레임). **게이트웨이는 `X-User-Id`에 `userId` 클레임을 쓰지 않는다**(`sub`만 사용).
- **`role`:** 역할 문자열.

---

## 5. 세션 체크 계약

### 5.1 성공 응답 `data`

```json
{
  "email": "user@example.com",
  "role": "USER",
  "authenticated": true
}
```

- 인증된 요청만 성공한다.
- 미인증 요청은 `401` + 공통 실패 응답을 반환한다.

### 5.2 캐시 정책

- `GET /api/auth/session` 응답에는 `Cache-Control: no-store`를 설정한다.

---

## 6. 로그아웃 계약

- `POST /api/auth/logout`은 Stateless 정책에 따라 서버 토큰 무효화를 수행하지 않는다.
- 대신 BFF가 `access_token` 쿠키를 삭제할 수 있도록 성공 신호를 반환한다.
- 응답 헤더는 `Cache-Control: no-store`를 포함한다.

---

## 7. 회원가입 입력 정책

- `passwordConfirm`은 필수이며 `password`와 일치해야 한다.
- 비밀번호 정책:
  - 소문자/숫자/특수문자 각각 1개 이상
  - 대문자 불가
  - 공백 불가
  - 길이 8~100자

---

## 8. 오류 코드 기준


| 상황        | 상태 코드 | 설명                           |
| --------- | ----- | ---------------------------- |
| 입력 검증 실패  | `400` | 필드 유효성/정책 위반                 |
| 로그인 인증 실패 | `401` | 이메일/비밀번호 불일치                 |
| 이메일 중복    | `409` | 회원가입 중복                      |
| 인증 계약 위반  | `502` | 업스트림/내부 계약 위반(`tokenType` 등) |


---

## 9. 구현 시 주의

- 인증 관련 응답은 캐시 금지(`Cache-Control: no-store`)를 유지한다.
- 인증 실패 응답은 프론트/BFF에서 공통 처리할 수 있도록 코드/본문 일관성을 유지한다.
- 토큰 원문, 비밀값, 인증 헤더를 로그에 남기지 않는다.

