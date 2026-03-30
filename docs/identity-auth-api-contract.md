# Identity 인증 API 계약 (백엔드)

버전: 1.0  
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
| `POST` | `/api/auth/external-keys` | 필요  | 외부 AI API 키 등록 (`provider`, `externalKey`, `alias`) |
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

## 6. 외부 API 키 등록 계약

클라이언트가 Gemini 등 **제3자에서 발급받은 API 키 평문**과 **제공자(`provider`)**, **별칭(`alias`)**을 전달하면, 서버는 키 평문을 **AES-256-GCM으로 암호화해 DB에 저장**하고, 응답 본문에는 **평문·암호문을 포함하지 않는다**. 동일 사용자가 동일 `provider`에 대해 동일 키 평문을 중복 등록하면 `409`를 반환한다.

### 6.1 요청

| 항목 | 값 |
| --- | --- |
| 메서드 | `POST` |
| 경로 | `/api/auth/external-keys` |
| 인증 | 필요 (액세스 토큰, 일반적으로 `Authorization: Bearer <jwt>`) |
| `Content-Type` | `application/json` |

요청 본문 필드:

| 필드 | 타입 | 필수 | 제약 | 예시 값 |
| --- | --- | --- | --- | --- |
| `provider` | string (enum) | 예 | `GEMINI`, `OPENAI`, `ANTHROPIC` 중 하나 | `"GEMINI"` |
| `externalKey` | string | 예 | 공백만 불가, 최대 4096자 | 제3자가 발급한 비밀 키 |
| `alias` | string | 예 | 공백만 불가, 최대 100자 | `"데모용 Gemini"` |

요청 본문 예시:

| 항목 | 예시 JSON |
| --- | --- |
| Body | `{"provider":"GEMINI","externalKey":"<비밀키>","alias":"데모용 Gemini"}` |

### 6.2 성공 응답 (`201 Created`)

공통 래퍼 `ApiResponse`와 `data` 객체 형태는 아래와 같다. **`data`에 키 평문은 포함되지 않는다.**

| 항목 | 값 |
| --- | --- |
| 상태 코드 | `201` |
| `Cache-Control` | `no-store` (본 서비스는 API 응답 전반에 적용) |

응답 필드 예시:

| 필드 | 타입 | 예시 값 | 설명 |
| --- | --- | --- | --- |
| `success` | boolean | `true` | 처리 성공 여부 |
| `message` | string | `"외부 API 키가 등록되었습니다"` | 사용자에게 보일 메시지 |
| `data.id` | number | `1` | 등록 레코드 ID |
| `data.provider` | string | `"GEMINI"` | 제공자 |
| `data.alias` | string | `"데모용 제미나이 키"` | 별칭 |
| `data.createdAt` | string | `"2026-03-29T08:05:19.296098200Z"` | 등록 시각(ISO-8601 UTC, 나노초 포함 가능) |

응답 본문 예시:

```json
{
  "success": true,
  "message": "외부 API 키가 등록되었습니다",
  "data": {
    "id": 1,
    "provider": "GEMINI",
    "alias": "데모용 제미나이 키",
    "createdAt": "2026-03-29T08:05:19.296098200Z"
  }
}
```

오류 응답 예시 (`success=false`, `data=null`):

| 상황 | 상태 코드 | 예시 JSON |
| --- | --- | --- |
| `provider` 누락 | `400` | `{"success":false,"message":"provider는 필수입니다","data":null}` |
| `externalKey` 누락 | `400` | `{"success":false,"message":"externalKey는 필수입니다","data":null}` |
| `alias` 누락 | `400` | `{"success":false,"message":"alias는 필수입니다","data":null}` |
| `provider` 값 불가 | `400` | `{"success":false,"message":"provider 값이 올바르지 않습니다. 허용: GEMINI, OPENAI, ANTHROPIC","data":null}` (또는 본문 형식 오류 메시지) |
| 사용자당 외부 키 상한 초과 | `400` | `{"success":false,"message":"외부 API 키는 사용자당 최대 5개까지 등록할 수 있습니다","data":null}` |
| 동일 provider·동일 키 재등록 | `409` | `{"success":false,"message":"이미 등록된 API 키입니다","data":null}` |

### 6.3 캐시 정책

- identity-service는 HTTP 응답에 `Cache-Control: no-store`를 적용한다.

---

## 7. 로그아웃 계약

- `POST /api/auth/logout`은 Stateless 정책에 따라 서버 토큰 무효화를 수행하지 않는다.
- 대신 BFF가 `access_token` 쿠키를 삭제할 수 있도록 성공 신호를 반환한다.
- 응답 헤더는 `Cache-Control: no-store`를 포함한다.

---

## 8. 회원가입 입력 정책

- `passwordConfirm`은 필수이며 `password`와 일치해야 한다.
- 비밀번호 정책:
  - 소문자/숫자/특수문자 각각 1개 이상
  - 대문자 불가
  - 공백 불가
  - 길이 8~100자

---

## 9. 오류 코드 기준


| 상황        | 상태 코드 | 설명                           |
| --------- | ----- | ---------------------------- |
| 입력 검증 실패  | `400` | 필드 유효성/정책 위반 (`provider`·`externalKey`·`alias` 등) |
| 외부 API 키 개수 초과 | `400` | 사용자당 최대 5개까지 등록 가능        |
| 로그인 인증 실패 | `401` | 이메일/비밀번호 불일치                 |
| 보호 API 미인증 | `401` | 액세스 토큰 없음/무효 (`POST /api/auth/external-keys` 등) |
| 외부 API 키 중복 등록 | `409` | 동일 사용자·동일 키 평문 재등록           |
| 이메일 중복    | `409` | 회원가입 중복                      |
| 인증 계약 위반  | `502` | 업스트림/내부 계약 위반(`tokenType` 등) |


---

## 10. 구현 시 주의

- 인증 관련 응답은 캐시 금지(`Cache-Control: no-store`)를 유지한다(identity-service는 필터로 API 응답 전반에 적용).
- 인증 실패 응답은 프론트/BFF에서 공통 처리할 수 있도록 코드/본문 일관성을 유지한다.
- 토큰 원문, 비밀값, 인증 헤더, **외부 API 키 평문**을 로그에 남기지 않는다.
- 외부 API 키 평문은 DB에 저장하지 않으며, AES-256-GCM 암호문만 저장한다. 암호화 재료는 `identity.api-key.encryption.secret` / `IDENTITY_API_KEY_ENCRYPTION_SECRET` 등으로 운영 환경에 안전하게 주입한다.

