# Ext 팀·개인 usage — identity-service / team-service 구현 계획

본 문서는 **proxy-service ext 팀 fingerprint hydration**([`TeamKeyCredentialClient`](../services/proxy-service/src/main/java/com/eevee/proxyservice/key/TeamKeyCredentialClient.java), [`team-ext-credential-api-contract.md`](../services/proxy-service/docs/team-ext-credential-api-contract.md))이 반영된 **현재 코드 기준**으로, **identity-service**·**team-service**에서 추가로 구현해야 할 작업을 정리한다.

- **api-gateway / usage-service / billing / usage-web** — 이번 범위 아님(이미 ext ingress·이벤트 소비 지원).
- **proxy-service** — 팀 credential **소비** 및 팀 `UsageSubjectResolver` 우선순위는 구현 완료. 본 문서는 **제공 API(identity/team)** 구현 담당용이다.

---

## 완료 조건 (ext 팀 AI 호출 E2E)

| 단계 | 담당 | 상태 |
|------|------|------|
| Gateway ext HMAC + fingerprint | api-gateway | 완료 |
| Proxy fingerprint merge + 팀 credential **호출** | proxy-service | 완료(소비 측) |
| Team **GET credential** API | team-service | **미구현 → 팀 ext relay 불가** |
| Team fingerprint lookup `userId`(이메일) | team-service | **미구현 → 헤더 없을 때 `user_id` null 가능** |
| Identity fingerprint lookup 이메일 | identity-service | **미구현 → proxy가 추가 Identity HTTP로 보완 중** |

배포 순서 권장: **team-service(P0 credential) → team-service(P1 lookup userId) → identity-service(P1 lookup 이메일)**. proxy 추가 변경은 team/identity 응답만 맞으면 **불필요**.

---

## 우선순위 요약

| 우선순위 | 서비스 | 작업 | 없을 때 영향 |
|----------|--------|------|----------------|
| **P0 필수** | team-service | Trusted credential API | ext 팀 키 AI 호출 **502/연결 실패**, usage 이벤트 **미발행** |
| **P1 필수** | team-service | Fingerprint lookup `userId` = 등록자 이메일(JWT `sub` 형식) | `X-Ext-User-Id` 없을 때 팀 멤버 usagelog·`user_id` 귀속 불완전 |
| **P1 필수** | identity-service | Fingerprint lookup `userId` = 소유자 이메일 | ext 개인 usage가 `u_*`로 남거나 proxy Identity 왕복·실패(502) 부담 |

---

## team-service

### P0. Trusted credential API (ext 팀 relay 차단 해소)

Proxy가 기대하는 계약(정본: [`services/proxy-service/docs/team-ext-credential-api-contract.md`](../services/proxy-service/docs/team-ext-credential-api-contract.md)):

| 항목 | 값 |
|------|-----|
| Method / Path | `GET /internal/v1/team-api-keys/{keyId}/credential` |
| Query | `teamId`(Long, 필수), `provider`(`openai` / `anthropic` / `google` — [`TeamInternalApiKeyResolveService`](services/team-service/src/main/java/com/zerobugfreinds/team_service/service/TeamInternalApiKeyResolveService.java)와 동일 규칙) |
| Auth | `Authorization: Bearer` = `team.internal.api-token` / `PROXY_TEAM_KEY_SERVICE_INTERNAL_TOKEN` |
| 200 | [`InternalTeamApiKeyResponse`](services/team-service/src/main/java/com/zerobugfreinds/team_service/dto/InternalTeamApiKeyResponse.java): `{ "plainKey": "...", "keyId": "..." }` |
| 403 | 토큰 누락·불일치 |
| 404 | 키 없음, `teamId`·`keyId`·`provider` 불일치, 삭제 요청(`deletion_requested_at` 등) |

#### 구현 작업 (필수)

1. **신규 서비스** (예: `TeamTrustedApiKeyCredentialService`)
   - Bearer 검증: 기존 [`TeamInternalApiKeyResolveService.validateInternalToken`](services/team-service/src/main/java/com/zerobugfreinds/team_service/service/TeamInternalApiKeyResolveService.java) 재사용 또는 공통 추출.
   - **팀 멤버십 검사 없음** — 호출자는 proxy(내부망)만 가정.
   - `TeamApiKeyRepository.findByIdAndTeamIdAndProviderAndDeletionRequestedAtIsNull`(또는 fingerprint lookup과 동일 활성 조건)으로 행 조회.
   - 복호화: [`EncryptionUtil.decryptAes256Gcm`](services/team-service/src/main/java/com/zerobugfreinds/team_service/util/EncryptionUtil.java) — membership용 internal GET과 동일.
   - **평문 키·전체 fingerprint 로그 금지** (keyId·teamId mask만).

2. **신규 컨트롤러** (예: `InternalTeamApiKeyCredentialController`)
   - `@RequestMapping("/internal/v1/team-api-keys")`
   - `@GetMapping("/{keyId}/credential")`
   - [`SecurityConfig`](services/team-service/src/main/java/com/zerobugfreinds/team_service/config/SecurityConfig.java)에 `/internal/v1/team-api-keys/**` permit 이미 있음 — **애플리케이션 Bearer 검증 필수**.

3. **하지 말 것**
   - `POST /internal/v1/api-keys/lookup` 응답에 `plainKey` 포함.
   - 기존 `GET /internal/api-keys/{provider}?userId=&teamId=` 에 “trusted 모드” 플래그 추가(멤버십 우회 혼선).

4. **테스트 (필수)**
   - 403 / 404 / 200 복호화 성공.
   - `teamId`·`keyId` 불일치 404.
   - 삭제 요청 키 404.

5. **문서·운영**
   - [`docs/contracts/proxy-api-key-reverse-lookup-internal-api.md`](contracts/proxy-api-key-reverse-lookup-internal-api.md)에 credential 절 추가(또는 team-ext-credential-api-contract 링크).
   - docker-compose / `.env`: `PROXY_TEAM_KEY_SERVICE_INTERNAL_TOKEN` = `team.internal.api-token` 정렬 확인.

---

### P1. Fingerprint lookup — `userId` 이메일 정합 (팀 등록자)

**현재:** [`TeamApiKeyFingerprintLookupService`](services/team-service/src/main/java/com/zerobugfreinds/team_service/service/TeamApiKeyFingerprintLookupService.java) → `InternalFingerprintLookupResponse.team(...)` 시 **`userId=null`** ([`dto` factory](services/team-service/src/main/java/com/zerobugfreinds/team_service/dto/InternalFingerprintLookupResponse.java)).

**목표:** `POST /internal/v1/api-keys/lookup` TEAM 응답의 `userId`에 **키 등록자**의 이메일(lowerercase, Identity JWT `sub`와 동일 규칙)을 넣는다. Proxy [`UsageSubjectResolver`](services/proxy-service/src/main/java/com/eevee/proxyservice/identity/UsageSubjectResolver.java)는 `X-Ext-User-Id` **우선**, 없으면 lookup `userId`를 usage `user_id`로 쓴다.

#### 구현 작업 (필수)

1. **`TeamApiKeyFingerprintLookupService.lookup`**
   - `TeamApiKeyEntity.getCreatedByUserId()` 읽기(컬럼 [`created_by_user_id`](services/team-service/src/main/java/com/zerobugfreinds/team_service/entity/TeamApiKeyEntity.java), nullable·legacy 행 가능).
   - 이메일 정규화:
     - 값이 이미 `@` 포함 → `trim` + `toLowerCase(Locale.ROOT)`.
     - numeric / opaque id → [`IdentityUserSyncService`](services/team-service/src/main/java/com/zerobugfreinds/team_service/service/IdentityUserSyncService.java) / [`IdentityUserSyncRepository`](services/team-service/src/main/java/com/zerobugfreinds/team_service/repository/IdentityUserSyncRepository.java) 또는 Identity internal API로 이메일 해석(팀 멤버십 lookup과 동일 후보 집합 패턴).
   - 해석 불가 시: `userId=null` 유지(기존과 동일) — proxy는 팀 합계(`team_id`)만 가능.

2. **`InternalFingerprintLookupResponse.team` factory**
   - 시그니처에 `String registrantUserId`(또는 `userId`) 추가 후 JSON `userId` 필드에 설정.

3. **테스트 (필수)**
   - `createdByUserId`가 이메일인 키 → lookup `userId` 소문자 이메일.
   - sync 테이블로 opaque → 이메일 변환 케이스(있으면).
   - `createdByUserId` null → `userId` null.

4. **한계 (문서화)**
   - 등록자 이메일 ≠ ext 호출자(Colab 실행 멤버). **실제 호출 멤버**는 클라이언트 **`X-Ext-User-Id`** 필요(변경 없음).

---

## identity-service

### P1. Fingerprint lookup — `userId` 이메일 정합 (개인 소유)

**현재:** [`ExternalApiKeyService.lookupByApiKeyFingerprint`](services/identity-service/src/main/java/com/zerobugfreinds/identity_service/service/ExternalApiKeyService.java) 가 `userId`에 **`"u_" + entity.getUserId()`** 반환(377–378행).

**Proxy 동작:** ext 개인은 [`UsageSubjectResolver`](services/proxy-service/src/main/java/com/eevee/proxyservice/identity/UsageSubjectResolver.java) + [`IdentityUsageSubjectClient`](services/proxy-service/src/main/java/com/eevee/proxyservice/identity/IdentityUsageSubjectClient.java)로 `u_*` → 이메일 변환(**추가 HTTP**). Identity 장애 시 personal ext **502** 가능.

**목표:** lookup 응답만으로 usage·usage-web 조회 키(이메일)와 일치. Proxy 변경 **불필요**(이미 이메일이면 normalize만 수행).

#### 구현 작업 (필수)

1. **`lookupByApiKeyFingerprint` 응답 `userId`**
   - [`principalSubForUser(Long userId)`](services/identity-service/src/main/java/com/zerobugfreinds/identity_service/service/ExternalApiKeyService.java) 재사용(MQ·이벤트와 동일: `user.email` lowercase).
   - `InternalFingerprintLookupResponse.personal(principalSub, keyId, ...)` 로 변경 — **`u_<pk>` 제거**.

2. **(선택·권장) 계약 필드 `principalSub`**
   - 하위 호환: `userId`에 이메일을 넣고 `principalSub`는 동일 값 중복 또는 생략.
   - 신규 소비자는 `userId` = email 전제로 통일해도 됨.

3. **테스트 (필수)**
   - fingerprint lookup 200 → `userId`가 `user@domain.com` 형태, `u_` prefix 없음.
   - 사용자 없음/삭제 등 `principalSubForUser` 실패 시 기대 예외·HTTP 매핑 정합.

4. **문서**
   - [`docs/contracts/proxy-api-key-reverse-lookup-internal-api.md`](contracts/proxy-api-key-reverse-lookup-internal-api.md) § identity POST lookup: `userId` = owner email 명시.

5. **Proxy 영향**
   - 배포 후 proxy의 Identity `principal`/`email` 왕복은 **cache miss 시에만** 발생하며, lookup이 이미 이메일이면 **사실상 no-op**. proxy 코드 수정 **필수 아님**.

---

## 배포·검증 체크리스트

### team-service PR

- [ ] `GET /internal/v1/team-api-keys/{keyId}/credential` 구현·테스트
- [ ] fingerprint lookup `userId` 이메일(등록자) 구현·테스트
- [ ] 로컬: proxy + team 동시 기동 후 ext 팀 키 호출 → upstream 2xx, RabbitMQ `usage.recorded`에 `team_id`, `api_key_source=team`
- [ ] 토큰: `PROXY_TEAM_KEY_SERVICE_INTERNAL_TOKEN` 일치

### identity-service PR

- [ ] fingerprint lookup `userId` = email
- [ ] regression: 기존 internal `GET /internal/api-keys/{provider}` 동작 유지
- [ ] ext 개인 호출 usage `user_id`가 이메일인지 확인(대시보드·usagelog)

### 통합 (수동)

1. 우리 플랫폼에 **팀 API Key** 등록(fingerprint 저장됨).
2. 외부 클라이언트가 `/api/v1/ai/ext/{provider}/...` + provider 키 + ext HMAC.
3. (선택) `X-Ext-User-Id`, `X-Team-Id`.
4. usage-web **팀 대시보드**·**팀 usagelog**에 반영 확인.

---

## 데이터·운영 참고

- **과거 데이터:** `usage_recorded_log.user_id = u_*` 또는 null인 ext 팀 행은 **백필 없음**. 신규 이벤트만 이메일·`team_id` 정합.
- **동일 키 개인+팀 중복 등록:** proxy merge **409** — 등록 정책 문제, 본 문서 범위 밖.
- **내부망:** `/internal/**` 는 public ingress 차단 전제(compose·LB).

---

## 관련 코드·문서 링크

| 구분 | 경로 |
|------|------|
| Proxy credential 소비 | [`TeamKeyCredentialClient`](../services/proxy-service/src/main/java/com/eevee/proxyservice/key/TeamKeyCredentialClient.java) |
| Proxy 팀 subject | [`UsageSubjectResolver`](../services/proxy-service/src/main/java/com/eevee/proxyservice/identity/UsageSubjectResolver.java) |
| Team credential 계약 | [`team-ext-credential-api-contract.md`](../services/proxy-service/docs/team-ext-credential-api-contract.md) |
| Fingerprint lookup (양쪽) | `POST /internal/v1/api-keys/lookup` — [`InternalFingerprintLookupController`](../services/team-service/src/main/java/com/zerobugfreinds/team_service/controller/InternalFingerprintLookupController.java) (team), identity 동일 경로 |
| Gateway ext | [`docs/contracts/gateway-proxy.md`](contracts/gateway-proxy.md) §3.3 |
