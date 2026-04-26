# Identity 인증 API 계약 (백엔드)

버전: 1.7  
관련: [architecture.md](./architecture.md) §1.3, [contracts/web-identity-bff.md](./contracts/web-identity-bff.md)

---

## 1. 목적

- `services/identity-service`의 인증 API 계약을 명확히 정의한다.
- Web BFF(`services/identity-service/web`)와의 인증 연동 시 필요한 응답 형식, 보안 헤더, 오류 코드를 고정한다.

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
| `POST` | `/api/auth/forgot-password` | 불필요 | 비밀번호 재설정 메일 요청(이메일 열거 방지용 동일 응답) |
| `POST` | `/api/auth/reset-password` | 불필요 | 메일 링크 토큰으로 비밀번호 재설정 |
| `POST` | `/api/auth/login`   | 불필요 | 로그인 및 액세스 토큰 발급          |
| `GET`  | `/api/auth/session` | 필요  | 세션(인증 상태) 확인             |
| `GET`  | `/api/auth/external-keys` | 필요  | 내 외부 AI API 키 목록 조회 (`id`, `provider`, `alias`, `monthlyBudgetUsd`, `createdAt`, 삭제 예정 시 `deletionRequestedAt`·`permanentDeletionAt`·`deletionGraceDays` 등) |
| `POST` | `/api/auth/external-keys` | 필요  | 외부 AI API 키 등록 (`provider`, `externalKey`, `alias`, `monthlyBudgetUsd`) |
| `PUT`  | `/api/auth/external-keys/{id}` | 필요  | 외부 AI API 키 수정 (`alias`, `monthlyBudgetUsd` 필수, `externalKey`는 선택) |
| `DELETE` | `/api/auth/external-keys/{id}` | 필요  | 외부 AI API 키 삭제 예약(선택 쿼리 `gracePeriodDays`, `retainLogs`; 기본 7일·범위 0~365일) |
| `POST` | `/api/auth/external-keys/{id}/deletion-cancel` | 필요  | 외부 AI API 키 삭제 예약 취소 |
| `POST` | `/api/auth/logout`  | 불필요 | 로그아웃 신호 응답(BFF 쿠키 삭제 유도) |


---

## 3.1 비밀번호 찾기·재설정

### 요청 본문

- **`POST /api/auth/forgot-password`:** `{ "email": "user@example.com" }`
- **`POST /api/auth/reset-password`:** `{ "token": "<메일 링크의 토큰>", "password": "…", "passwordConfirm": "…" }` — 비밀번호 규칙은 회원가입(`SignupRequest`)과 동일하다.

### 동작

- **forgot-password:** 등록된 이메일인 경우에만 재설정 토큰을 발급하고 메일을 보낸다. 응답 **메시지는 항상 동일**하여 이메일 존재 여부를 드러내지 않는다. SMTP가 설정되지 않은 환경에서는 재설정 링크가 서버 로그(INFO)로만 출력될 수 있다.
- **reset-password:** 토큰은 DB에 SHA-256 해시로만 저장되며 일회용이다. 만료·재사용·불일치 시 `400`과 안내 메시지를 반환한다.
- 설정: `identity.passwordReset.webBaseUrl`(공개 웹 베이스 URL, 메일 링크에 사용), `identity.passwordReset.tokenValidityHours`, `identity.passwordReset.mailFrom`, 선택적 `spring.mail.*`(SMTP).

### 캐시 정책

- 두 엔드포인트 응답에 `Cache-Control: no-store`를 적용한다.

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

## 6. 외부 API 키 조회 계약

로그인 사용자가 등록한 외부 API 키 메타데이터 목록을 조회한다. 응답에는 **키 평문/암호문이 포함되지 않는다**.

### 6.1 요청

| 항목 | 값 |
| --- | --- |
| 메서드 | `GET` |
| 경로 | `/api/auth/external-keys` |
| 인증 | 필요 (액세스 토큰, 일반적으로 `Authorization: Bearer <jwt>`) |

### 6.2 성공 응답 (`200 OK`)

| 항목 | 값 |
| --- | --- |
| 상태 코드 | `200` |
| `Cache-Control` | `no-store` (본 서비스는 API 응답 전반에 적용) |

응답 본문 예시:

```json
{
  "success": true,
  "message": "외부 API 키 목록 조회에 성공했습니다",
  "data": [
    {
      "id": 1,
      "provider": "GEMINI",
      "alias": "데모용 제미나이 키",
      "monthlyBudgetUsd": 20.5,
      "createdAt": "2026-03-29T08:05:19.296098200Z"
    },
    {
      "id": 2,
      "provider": "OPENAI",
      "alias": "예시 삭제 예정 키",
      "monthlyBudgetUsd": 10,
      "createdAt": "2026-03-30T08:00:00Z",
      "deletionRequestedAt": "2026-04-01T08:00:00Z",
      "permanentDeletionAt": "2026-04-08T08:00:00Z",
      "deletionGraceDays": 7
    }
  ]
}
```

삭제 예정 행이 아니면 `deletionRequestedAt`·`permanentDeletionAt`·`deletionGraceDays`는 응답에서 생략되거나 `null`일 수 있다(JSON 직렬화 정책에 따름).

오류 응답 예시 (`success=false`, `data=null`):

| 상황 | 상태 코드 | 예시 JSON |
| --- | --- | --- |
| 보호 API 미인증 | `401` | `{"success":false,"message":"인증이 필요합니다","data":null}` |

### 6.3 캐시 정책

- identity-service는 HTTP 응답에 `Cache-Control: no-store`를 적용한다.

---

## 7. 외부 API 키 등록 계약

클라이언트가 Gemini 등 **제3자에서 발급받은 API 키 평문**과 **제공자(`provider`)**, **별칭(`alias`)**, **월 예산(`monthlyBudgetUsd`)**을 전달하면, 서버는 키 평문을 **AES-256-GCM으로 암호화해 DB에 저장**하고, 응답 본문에는 **평문·암호문을 포함하지 않는다**. 동일 사용자·동일 `provider`·동일 키 값(해시)에 대해 **이미 활성 행이 있으면** `409`, **삭제 예정 행만 존재하면** `409`(메시지: `삭제예정키와 중복된 키`)를 반환한다.

### 7.1 요청

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
| `monthlyBudgetUsd` | number | 예 | 0 이상, 소수점 둘째 자리까지 | `20.5` |

요청 본문 예시:

| 항목 | 예시 JSON |
| --- | --- |
| Body | `{"provider":"GEMINI","externalKey":"<비밀키>","alias":"데모용 Gemini","monthlyBudgetUsd":20.5}` |

### 7.2 성공 응답 (`201 Created`)

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
| `data.monthlyBudgetUsd` | number | `20.5` | 월 예산(USD) |
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
    "monthlyBudgetUsd": 20.5,
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
| `monthlyBudgetUsd` 누락 | `400` | `{"success":false,"message":"monthlyBudgetUsd는 필수입니다","data":null}` |
| `provider` 값 불가 | `400` | `{"success":false,"message":"provider 값이 올바르지 않습니다. 허용: GEMINI, OPENAI, ANTHROPIC","data":null}` (또는 본문 형식 오류 메시지) |
| 동일 provider·동일 키 재등록(활성 행 존재) | `409` | `{"success":false,"message":"이미 등록된 API 키입니다","data":null}` |
| 동일 provider·동일 키, 삭제 예정 행과만 충돌 | `409` | `{"success":false,"message":"삭제예정키와 중복된 키","data":null}` |

### 7.3 캐시 정책

- identity-service는 HTTP 응답에 `Cache-Control: no-store`를 적용한다.

---

## 8. 로그아웃 계약

- `POST /api/auth/logout`은 Stateless 정책에 따라 서버 토큰 무효화를 수행하지 않는다.
- 대신 BFF가 `access_token` 쿠키를 삭제할 수 있도록 성공 신호를 반환한다.
- 응답 헤더는 `Cache-Control: no-store`를 포함한다.

---

## 9. 외부 API 키 수정 계약

외부 API 키 ID를 기준으로 별칭(`alias`)과 월 예산(`monthlyBudgetUsd`)을 수정한다. 필요 시 `externalKey`와 `provider`를 함께 보내 키 자체도 교체할 수 있다. 응답 본문에는 **키 평문·암호문을 포함하지 않는다**.

### 9.1 요청

| 항목 | 값 |
| --- | --- |
| 메서드 | `PUT` |
| 경로 | `/api/auth/external-keys/{id}` |
| 인증 | 필요 (액세스 토큰, 일반적으로 `Authorization: Bearer <jwt>`) |
| `Content-Type` | `application/json` |

경로 파라미터:

| 파라미터 | 타입 | 설명 |
| --- | --- | --- |
| `id` | number | 수정 대상 외부 API 키 ID |

요청 본문 필드:

| 필드 | 타입 | 필수 | 제약 | 예시 값 |
| --- | --- | --- | --- | --- |
| `provider` | string (enum) | 조건부 | `GEMINI`, `OPENAI`, `ANTHROPIC` 중 하나 (`externalKey`를 함께 보낼 때 필수) | `"GEMINI"` |
| `externalKey` | string | 아니오 | 공백만 불가, 최대 4096자 (`provider`와 함께 보낼 때 키 교체) | 제3자가 발급한 비밀 키 |
| `alias` | string | 예 | 공백만 불가, 최대 100자 | `"데모용 Gemini (수정)"` |
| `monthlyBudgetUsd` | number | 예 | 0 이상, 소수점 둘째 자리까지 | `35.0` |

### 9.2 성공 응답 (`200 OK`)

| 항목 | 값 |
| --- | --- |
| 상태 코드 | `200` |
| `Cache-Control` | `no-store` (본 서비스는 API 응답 전반에 적용) |

응답 본문 예시:

```json
{
  "success": true,
  "message": "외부 API 키가 수정되었습니다",
  "data": {
    "id": 1,
    "provider": "GEMINI",
    "alias": "데모용 Gemini (수정)",
    "monthlyBudgetUsd": 35.0,
    "createdAt": "2026-03-29T08:05:19.296098200Z"
  }
}
```

오류 응답 예시 (`success=false`, `data=null`):

| 상황 | 상태 코드 | 예시 JSON |
| --- | --- | --- |
| 수정 대상 키 없음 | `404` | `{"success":false,"message":"등록된 API 키를 찾을 수 없습니다","data":null}` |
| `externalKey`는 있는데 `provider` 누락 | `400` | `{"success":false,"message":"externalKey를 수정할 때 provider는 필수입니다","data":null}` |
| `monthlyBudgetUsd` 누락 | `400` | `{"success":false,"message":"monthlyBudgetUsd는 필수입니다","data":null}` |
| 삭제 예정 키 수정 시도 | `409` | `{"success":false,"message":"삭제 예정인 키는 수정할 수 없습니다. 취소 후 다시 시도하세요.","data":null}` |
| 별칭 중복 | `409` | `{"success":false,"message":"이미 사용 중인 별칭입니다","data":null}` |
| 동일 provider·동일 키 중복(활성 다른 행) | `409` | `{"success":false,"message":"이미 등록된 API 키입니다","data":null}` |
| 동일 provider·동일 키, 삭제 예정 행과만 충돌 | `409` | `{"success":false,"message":"삭제예정키와 중복된 키","data":null}` |

### 9.3 캐시 정책

- identity-service는 HTTP 응답에 `Cache-Control: no-store`를 적용한다.

---

## 10. 회원가입 입력 정책

- `passwordConfirm`은 필수이며 `password`와 일치해야 한다.
- 비밀번호 정책:
  - 소문자/숫자/특수문자 각각 1개 이상
  - 대문자 불가
  - 공백 불가
  - 길이 8~100자

---

## 10.1 외부 API 키 삭제 예약/취소 계약

삭제는 즉시 물리 삭제가 아니라 **유예 기간(soft delete)** 후 물리 삭제로 처리한다. **기본 유예는 7일**이며, 요청 시 **쿼리 `gracePeriodDays`** 로 **0~365일** 범위에서 바꿀 수 있다(생략 시 7일). 유예 중에는 삭제 취소가 가능하며, 유예 종료 후 스케줄러가 물리 삭제한다.
`retainLogs`(기본 `true`)를 함께 전달할 수 있으며, `false`면 키가 최종 삭제되는 시점(즉시 삭제 또는 유예 만료 purge)에 usage 로그·통계 삭제를 요청한다.

### 삭제 예약: `DELETE /api/auth/external-keys/{id}`

| 항목 | 설명 |
| --- | --- |
| 쿼리 | 선택 `gracePeriodDays`(정수), 선택 `retainLogs`(boolean, 기본 `true`). |
| 동작 | `deletionRequestedAt`·`permanentDeletionAt`·`deletionGraceDays` 설정 |

| 상황 | 상태 코드 | 예시 JSON |
| --- | --- | --- |
| 삭제 예약 성공 | `200` | `{"success":true,"message":"삭제가 예약되었습니다. 일주일 이내에 취소할 수 있으며, 이후에는 키가 영구 삭제됩니다.","data":{"id":1,"provider":"GEMINI","alias":"데모 키","createdAt":"...","monthlyBudgetUsd":10,"deletionRequestedAt":"...","permanentDeletionAt":"...","deletionGraceDays":7}}` |
| `gracePeriodDays` 범위 밖 | `400` | `{"success":false,"message":"유예 기간은 0일 이상 365일 이하로 설정할 수 있습니다","data":null}` |
| 대상 키 없음 | `404` | `{"success":false,"message":"등록된 API 키를 찾을 수 없습니다","data":null}` |
| 이미 삭제 예정 | `409` | `{"success":false,"message":"이미 삭제 예정인 키입니다","data":null}` |

### 삭제 취소: `POST /api/auth/external-keys/{id}/deletion-cancel`

| 상황 | 상태 코드 | 예시 JSON |
| --- | --- | --- |
| 삭제 취소 성공 | `200` | `{"success":true,"message":"삭제 예약이 취소되었습니다","data":{"id":1,"provider":"GEMINI","alias":"데모 키","createdAt":"...","monthlyBudgetUsd":10,"deletionRequestedAt":null,"permanentDeletionAt":null,"deletionGraceDays":null}}` |
| 대상 키 없음 | `404` | `{"success":false,"message":"등록된 API 키를 찾을 수 없습니다","data":null}` |
| 삭제 예정 상태 아님 | `409` | `{"success":false,"message":"삭제 예정 상태가 아닙니다","data":null}` |

---

## 11. 오류 코드 기준


| 상황        | 상태 코드 | 설명                           |
| --------- | ----- | ---------------------------- |
| 입력 검증 실패  | `400` | 필드 유효성/정책 위반 (`provider`·`externalKey`·`alias` 등), 삭제 예약 시 `gracePeriodDays` 범위(0~365) 위반 등 |
| 로그인 인증 실패 | `401` | 이메일/비밀번호 불일치                 |
| 보호 API 미인증 | `401` | 액세스 토큰 없음/무효 (`GET/POST/PUT/DELETE /api/auth/external-keys` 등) |
| 외부 API 키 별칭 중복 | `409` | 동일 사용자 기준 별칭 재사용              |
| 외부 API 키 중복 등록 | `409` | 동일 사용자·동일 provider·동일 키 값 — 활성 행 존재 시 `이미 등록된 API 키입니다`, 삭제 예정 행과만 충돌 시 `삭제예정키와 중복된 키` |
| 외부 API 키 삭제 예정 충돌 | `409` | 이미 삭제 예정이거나, 삭제 예정 상태가 아닌 키 취소 등 상태 충돌 |
| 외부 API 키 미존재 | `404` | 수정/조회 대상 외부 API 키를 찾을 수 없음    |
| 이메일 중복    | `409` | 회원가입 중복                      |
| 인증 계약 위반  | `502` | 업스트림/내부 계약 위반(`tokenType` 등) |


---

## 12. 구현 시 주의

- 인증 관련 응답은 캐시 금지(`Cache-Control: no-store`)를 유지한다(identity-service는 필터로 API 응답 전반에 적용).
- 인증 실패 응답은 프론트/BFF에서 공통 처리할 수 있도록 코드/본문 일관성을 유지한다.
- 토큰 원문, 비밀값, 인증 헤더, **외부 API 키 평문**을 로그에 남기지 않는다.
- 외부 API 키 평문은 DB에 저장하지 않으며, AES-256-GCM 암호문만 저장한다. 암호화 재료는 `identity.api-key.encryption.secret` / `IDENTITY_API_KEY_ENCRYPTION_SECRET` 등으로 운영 환경에 안전하게 주입한다.

## 13. 비동기 이벤트 발행 (Message Queue)

외부 API 키의 상태가 변경(생성, 수정, 삭제 예약, 취소 등)될 때마다 타 서비스(Usage 등)와의 상태 동기화를 위해 RabbitMQ로 이벤트를 발행한다.
(주의: 과금/결제 분리를 위해 `monthlyBudgetUsd`는 이벤트 페이로드에서 제외된다.)

- **Exchange:** `identity.events` (기본값)
- **Routing Key:** `identity.external-api-key.status-changed`
- **발행 시점 (트랜잭션 커밋 후 AFTER_COMMIT 보장):**
  - 신규 등록 성공 → `ACTIVE`
  - 수정 성공(별칭 변경 포함) → `ACTIVE`
  - 삭제 요청 성공(유예 시작) → `DELETION_REQUESTED`
  - 삭제 예약 취소 성공 → `ACTIVE`
  - 유예 만료로 인한 물리 삭제 시 → `DELETED`

**Event Payload (JSON):**
```json
{
  "keyId": 123,
  "alias": "플랫폼팀 제미나이",
  "userId": 456,
  "provider": "GEMINI",
  "status": "ACTIVE"
}