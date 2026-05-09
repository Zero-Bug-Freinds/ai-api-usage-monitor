# Proxy API Key 역조회 내부 API 가이드

이 문서는 `proxy-service` 또는 다른 내부 호출자가 **외부 AI API Key의 해시값으로 등록 키를 역조회**할 때 사용하는 내부 API 계약을 정의한다.

주요 사용 사례는 다음과 같다. 3rd-party 도구가 `teamId`, `keyId` 같은 우리 플랫폼 전용 헤더 없이 provider API Key를 `Authorization` 헤더로 직접 보낸다. 이때 proxy는 들어온 키를 해시한 뒤 각 소유 서비스에 조회하여 어떤 사용자/팀의 어떤 키에 매핑되는지 확인하고, 사용량 귀속 및 상태 게이트를 적용한다.

응답에는 평문 키를 **절대 포함하지 않는다**. 응답에는 식별 메타데이터(`keyId`, owner, `status`, `alias`, `scope`)만 포함한다.

## 범위

- 호출자: `services/proxy-service` 및 동일한 해시 → 사용자/팀 매핑이 필요한 내부 서비스
- 피호출자:
  - `services/identity-service`: 개인 API Key(`USER` scope)
  - `services/team-service`: 팀 API Key(`TEAM` scope)
- 목적: 클라이언트 헤더를 신뢰하지 않고 해시값 기준으로 등록 키를 식별하여 사용량 귀속과 라이프사이클 상태 판단을 수행한다.
- 정책: 호출자는 키 등록 시 소유 서비스가 사용한 것과 **완전히 동일한 알고리즘**으로 해시를 계산해야 한다. 자세한 내용은 [해시 형식](#해시-형식)을 따른다.

## 해시 형식

두 서비스 모두 아래 방식으로 계산된 `key_hash`를 저장하고 조회한다. 이 형식이 일치하지 않으면 조회는 항상 `404`가 된다.

```text
sha256( providerName + '\0' + plainKey )  ->  64자 lowercase hex
```

- `providerName`: 대문자 enum 이름 (`OPENAI`, `ANTHROPIC`, `GOOGLE`, ...)
- `'\0'`: 구분자로 사용하는 ASCII NUL byte (`0x00`)
- `plainKey`: 사용자가 입력한 provider 키 평문을 trim 한 값
- 출력: 공백이나 `0x` prefix 없는 lowercase hex 문자열

참조 구현:

- `services/identity-service/.../util/EncryptionUtil#sha256HexForUniqueness`
- `services/team-service/.../util/EncryptionUtil#sha256HexForUniqueness`

엔드포인트는 `hashedKey` 입력값의 앞뒤 공백을 제거하고 서버에서 소문자로 정규화한다. 따라서 해시 문자열 대소문자 차이는 허용한다.

## Provider 값

두 엔드포인트는 아래 provider 값을 허용한다. 입력은 대문자 enum 이름만 허용한다.

- `OPENAI`
- `ANTHROPIC`
- `GOOGLE`
- `META`
- `MISTRAL`
- `COHERE`
- `GROK`

`team-service`는 추가로 아래 alias를 허용한다.

- `GEMINI` -> `GOOGLE`로 정규화
- `CLAUDE` -> `CLAUDE` enum (legacy 호환)

소문자 또는 지원하지 않는 provider는 `400`을 반환한다.

---

## 1. Identity-service: 개인 API Key 역조회

### 요청

- Method: `GET`
- Path: `/internal/v1/api-keys/lookup`
- Query parameters:
  - `hashedKey` (required): SHA-256 hex 해시. [해시 형식](#해시-형식)을 따른다.
  - `provider` (required): AI provider 이름. [Provider 값](#provider-값)을 따른다.
- Header: 없음
  - 현재 identity-service의 기존 내부 키 조회 API(`/internal/api-keys/{provider}`)와 동일하게 네트워크 격리를 전제로 한다.

### 응답 (200)

```json
{
  "keyId": "12345",
  "ownerId": 7,
  "status": "ACTIVE",
  "alias": "openai-default",
  "scope": "USER"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `keyId` | string | 외부 API Key 행의 PK. 문자열로 반환한다. |
| `ownerId` | number (`Long`) | 키 소유 사용자 ID |
| `status` | string (enum) | `ACTIVE` / `DELETION_REQUESTED` / `DELETED` |
| `alias` | string | 사용자가 설정한 별칭 |
| `scope` | string (literal) | 항상 `"USER"` |

### 에러 의미

- `400`: `hashedKey` 또는 `provider` 누락/blank, 소문자 provider, 지원하지 않는 provider
- `404`: `(provider, hashedKey)`에 매칭되는 행 없음. 삭제 유예 기간이 끝나 물리 삭제된 행도 조회 관점에서는 `404`다.
- `409`: `(provider, hashedKey)`에 2건 이상 매칭됨. 전역적으로 모호하므로 호출자가 단일 키를 선택할 수 없다. 자세한 내용은 [모호성 처리](#모호성-처리)를 따른다.
- `500`: 내부 서버 오류

에러 응답은 서비스 공통 `ApiResponse<Void>` 형식을 따른다.

```json
{ "success": false, "message": "동일 해시값에 매칭되는 외부 API 키가 2건 이상입니다", "data": null }
```

---

## 2. Team-service: 팀 API Key 역조회

### 요청

- Method: `GET`
- Path: `/internal/v1/team-api-keys/lookup`
- Query parameters:
  - `hashedKey` (required): SHA-256 hex 해시. [해시 형식](#해시-형식)을 따른다.
  - `provider` (required): AI provider 이름. [Provider 값](#provider-값)을 따른다.
- Header:
  - `Authorization: Bearer <internal-token>` (required)
  - 기존 team-service 내부 팀 키 조회 API(`/internal/api-keys/{provider}`)와 동일한 내부 토큰 정책을 사용한다.

### 응답 (200)

```json
{
  "keyId": "777",
  "teamId": 42,
  "ownerUserId": "user-1",
  "status": "ACTIVE",
  "alias": "openai-team",
  "scope": "TEAM"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `keyId` | string | 팀 API Key 행의 PK. 문자열로 반환한다. |
| `teamId` | number (`Long`) | 키가 속한 팀 ID |
| `ownerUserId` | string \| null | 키를 등록한 팀 멤버의 사용자 ID. legacy 행은 `null`일 수 있다. |
| `status` | string (enum) | `ACTIVE` / `DELETION_REQUESTED` / `DELETED` |
| `alias` | string | 팀 키 별칭 |
| `scope` | string (literal) | 항상 `"TEAM"` |

`ownerUserId`는 `team_api_keys.created_by_user_id` 컬럼에서 가져온다. 이 컬럼은 nullable이다. 컬럼 도입 이전에 생성된 기존 행은 다음 재등록 전까지 `null`로 반환될 수 있다.

### 에러 의미

- `400`: `hashedKey` 또는 `provider` 누락/blank, 소문자 provider, 지원하지 않는 provider
- `403`: `Authorization: Bearer <internal-token>` 누락/오류, 또는 서버에 내부 토큰 미설정
- `404`: `(provider, hashedKey)`에 매칭되는 행 없음
- `409`: `(provider, hashedKey)`에 2건 이상 매칭됨. 자세한 내용은 [모호성 처리](#모호성-처리)를 따른다.
- `500`: 내부 서버 오류

에러 응답은 서비스 공통 `ApiResponse<Void>` 형식을 따른다.

---

## 상태 의미

`status` 필드는 DB 행의 라이프사이클 상태를 그대로 반영한다.

| DB 상태 | 반환 `status` |
| --- | --- |
| 행 존재, `deletion_requested_at IS NULL` | `ACTIVE` |
| 행 존재, `deletion_requested_at IS NOT NULL` | `DELETION_REQUESTED` |
| purge scheduler에 의해 행이 물리 삭제됨 | `404` (매핑할 행 없음) |

`DELETED` enum 값은 이벤트(`identity.events` / `team.api-key.exchange`)로 수신할 수 있는 상태값을 위해 예약되어 있다. 역조회 엔드포인트는 행이 삭제되면 매핑할 데이터가 없으므로 직접 `DELETED`를 반환하지 않고 `404`를 반환한다.

호출자는 `DELETION_REQUESTED`를 **요청 차단 대상**으로 처리해야 한다. 사용자가 또는 팀 소유자가 키 폐기를 요청한 상태이며, 유예 기간 중이라 행이 남아 있어도 신규 요청에 사용할 키로 보면 안 된다.

## 모호성 처리

기저 테이블의 유일 제약은 다음과 같다.

- `external_api_keys`: `UNIQUE (user_id, provider, key_hash)`
- `team_api_keys`: `UNIQUE (team_id, provider, key_hash)`

두 제약 모두 owner 단위로 유일성을 보장한다. 따라서 동일한 provider 키가 여러 owner 행에 존재할 수 있다. 예를 들어 두 사용자가 같은 OpenAI 키를 등록하거나, 두 팀이 같은 키를 등록할 수 있다.

이 경우 `(provider, hashedKey)`만으로 전역 조회를 수행하면 어떤 owner를 선택해야 하는지 결정할 수 없다. 그래서 엔드포인트는 `409 Conflict`를 반환한다. 호출자는 명시적인 `userId` / `teamId` 헤더가 있으면 이를 사용해 다른 조회 경로로 재시도하거나, 모호한 요청으로 거절해야 한다.

서버 로그에는 감사 목적으로 매칭 건수와 해시의 앞 8자만 남긴다. 전체 해시와 평문 키는 절대 로그에 남기지 않는다.

## 성능 고려사항

두 테이블 모두 `key_hash`가 유일 제약의 일부이므로 PostgreSQL 인덱스가 이미 존재한다. 다만 현재 유일 제약의 컬럼 순서는 아래와 같다.

- `(user_id, provider, key_hash)`
- `(team_id, provider, key_hash)`

역조회 쿼리는 `(provider, key_hash)` 조건을 사용한다. 운영 프로파일링에서 많은 행을 스캔하는 핫패스가 확인되면 각 서비스에 `(provider, key_hash)` 전용 인덱스를 추가한다.

proxy는 같은 해시에 대한 성공 조회 결과를 캐시하는 것이 좋다. 동일 클라이언트의 반복 요청마다 DB 조회가 발생하지 않도록 기존 `proxy-service`의 `ApiKeyClient`가 사용하는 `Caffeine` `LoadingCache` 패턴을 재사용한다.

## 보안

- 두 엔드포인트는 내부 전용(`/internal/v1/...`)이며 public API gateway로 노출하지 않는다.
- `team-service`는 `Authorization: Bearer <internal-token>`을 `team.internal.api-token`과 비교한다. 환경 변수는 `PROXY_TEAM_KEY_SERVICE_INTERNAL_TOKEN`을 사용한다.
- `identity-service`는 현재 내부 토큰을 검증하지 않는다. 기존 `/internal/api-keys/{provider}`와 동일한 전제로 네트워크 격리에 의존한다.
- 평문 provider 키는 입력으로 받지 않으며 응답에도 포함하지 않는다. 네트워크를 오가는 값은 SHA-256 해시뿐이다.
- 모호성 경고 로그에는 해시 앞 8자만 남기며 alias, owner id, 평문 키는 포함하지 않는다.

## 런타임 설정

- `identity-service`
  - `IDENTITY_API_KEY_ENCRYPTION_SECRET`
  - 이미 사용 중인 값이다. 저장 암호문용 AES 키 유도에 사용되며, 현재 `EncryptionUtil#sha256HexForUniqueness`는 별도 salt 없이 위 [해시 형식](#해시-형식)만 사용한다.
- `team-service`
  - `TEAM_API_KEY_ENCRYPTION_SECRET`
  - `team.internal.api-token`
    - `PROXY_TEAM_KEY_SERVICE_INTERNAL_TOKEN`에서 해석된다.
    - team-service 역조회 호출 시 필수다.
- `proxy-service`
  - 팀 역조회에는 기존 `PROXY_TEAM_KEY_SERVICE_INTERNAL_TOKEN`을 재사용한다.
  - identity-service 역조회에는 현재 신규 환경 변수가 필요 없다.
