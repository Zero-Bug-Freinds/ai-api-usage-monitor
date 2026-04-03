# Gateway ↔ Proxy 서비스 간 계약

버전: 1.3  
관련: [docs/architecture.md](../architecture.md) §4.1, §4.2, §10.2, [`services/usage-service/web/.env.example`](../../services/usage-service/web/.env.example), [Web·Gateway Usage BFF](./web-gateway-bff.md)(브라우저 `/api/usage/**` 매핑)

**v1.1:** `application.yml` 라우트·`RemoveRequestHeader=Authorization`·Bearer 검증·Web `API_GATEWAY_URL` 합의를 §1.1·§3·§9에 명시(게이트웨이·Usage BFF 담당 정합).  
**v1.2:** §4.2 Identity JWT `sub`(이메일)·Usage 원장 `user_id`·BFF `GATEWAY_DEV_MODE` 세션 이메일 정합·게이트웨이 회귀 테스트 위치 명시.  
**v1.3:** 웹 BFF 소재를 `services/*/web` 목표 구조·풀스택 소유에 맞게 서술 보강.

---

## 1. 역할

| 구분 | API Gateway | Proxy |
|------|-------------|--------|
| 배포 위치 | 외부에 노출되는 HTTP 진입점 | Gateway 뒤(또는 신뢰 네트워크)에서만 접근 |
| 플랫폼 인증 | 클라이언트 `Authorization: Bearer <JWT>` 검증 | 클라이언트 JWT를 **검증하지 않음** |
| AI Provider 키 | 다루지 않음 | API Key Service 또는 환경 변수(mock)로 주입 |
| 사용량 이벤트 | 발행하지 않음 | RabbitMQ로 `usage-recorded` 성격 메시지 발행 |

### 1.1 Bearer 검증·개발 모드 (구현 정본)

구현: [`SecurityConfiguration.java`](../../services/api-gateway-service/src/main/java/com/eevee/apigateway/config/SecurityConfiguration.java)(`addFilterAfter`로만 등록 — 해당 클래스를 `@Component`/`WebFilter` 빈으로 두면 전역 체인과 중복되어 인증 컨텍스트가 비는 문제가 난다), [`ProxyTrustHeadersWebFilter.java`](../../services/api-gateway-service/src/main/java/com/eevee/apigateway/filter/ProxyTrustHeadersWebFilter.java)(Spring Security `WebFilter` 체인, `Authorization` 이후), 설정 [`application.yml`](../../services/api-gateway-service/src/main/resources/application.yml)의 `gateway.dev-mode`·`gateway.jwt.secret`.

| 모드 | `gateway.dev-mode` | `/api/v1/ai/**`, `/api/v1/usage/**` |
|------|----------------------|--------------------------------------|
| 개발(기본) | `true` (`GATEWAY_DEV_MODE`) | **인증 생략**(permitAll). 신뢰 헤더 필터는 **`X-User-Id` 필수**(없으면 `401`). BFF가 쿠키 세션 등으로 `X-User-Id`를 보강할 수 있다. |
| 운영 | `false` | Spring **OAuth2 Resource Server(JWT)** 로 `Authorization: Bearer <플랫폼 JWT>` 검증(authenticated). JWT `sub` 등이 §4의 `X-User-Id`로 전달된다. `gateway.jwt.secret`·`ReactiveJwtDecoder` 필수. |

- **Usage HTTP**도 동일하게 게이트웨이에서 Bearer를 검증한다(Proxy만이 아님). Usage 서비스는 클라이언트 JWT를 받지 않는다(§3.1 `RemoveRequestHeader`).

---

## 2. 공개 URL (클라이언트 → Gateway)

- **Base path:** `/api/v1/ai`
- **패턴:** `/api/v1/ai/{provider}/**`
- `{provider}`: `openai` | `anthropic` | `google` ([AiProvider](../../libs/usage-events/src/main/java/com/eevee/usage/events/AiProvider.java)와 동일)

**예:** `POST /api/v1/ai/openai/v1/chat/completions`

---

## 3. Gateway 라우트·경로 (`application.yml` 정본)

소스: [`services/api-gateway-service/src/main/resources/application.yml`](../../services/api-gateway-service/src/main/resources/application.yml) (`spring.cloud.gateway.server.webflux.routes`).

### 3.1 라우트 ID·필터 (AI·Usage)

| Route ID | Predicate | Upstream URI (환경 변수) | 필터 |
|----------|-----------|--------------------------|------|
| `proxy-ai` | `Path=/api/v1/ai/**` | `GATEWAY_PROXY_URI` (기본 `http://localhost:8081`) | `RemoveRequestHeader=Authorization`, `RewritePath=/api/v1/ai/(?<segment>.*), /proxy/${segment}` |
| `usage-http` | `Path=/api/v1/usage/**` | `GATEWAY_USAGE_URI` (기본 `http://localhost:8092`) | `RemoveRequestHeader=Authorization` |

- **`RemoveRequestHeader=Authorization`:** 게이트웨이가(또는 개발 모드에서 생략 시에도) 클라이언트 `Authorization`을 **Proxy·Usage로 넘기기 전에 제거**한다. 플랫폼 JWT 유출 방지·§4「Proxy로 전달하지 않음」과 일치한다. **AI·Usage 라우트 모두** 동일하게 적용한다.
- **AI만 `RewritePath`:** Usage는 `/api/v1/usage/...`가 다운스트림에서도 **동일 path**로 전달된다.

### 3.2 AI: 클라이언트 경로 → Proxy path

- **Proxy Base path:** `/proxy`
- **패턴:** `/proxy/{provider}/**`

| 클라이언트 요청 (Gateway 수신) | Proxy로 전달되는 path |
|---------------------------|-------------------------|
| `/api/v1/ai/{provider}/...` | `/proxy/{provider}/...` |

**AI 라우트 필터:** Gateway는 `/api/v1/ai/**` 에 대해 **`RemoveRequestHeader=Authorization`** 을 적용한다. 클라이언트가 보낸 플랫폼 JWT(또는 기타 `Authorization`)는 **Proxy로 전달되지 않는다**(게이트웨이 보안 체인에서 소비·검증 후 라우팅). 구현: [`api-gateway-service` `application.yml`](../../services/api-gateway-service/src/main/resources/application.yml).

**로컬 개발(`gateway.dev-mode=true`):** JWT 없이 호출할 수 있으며, 이 경우 **`X-User-Id`** 는 클라이언트가 보낸 값을 게이트웨이 필터가 그대로 신뢰 경로에 실어 Proxy로 넘긴다(`ProxyTrustHeadersWebFilter`). Mock 키(`PROXY_GOOGLE_TEST_API_KEY` 등)를 쓰면 Key Service의 `userId`는 사실상 mock 분기에서 무시되기 쉽다.

---

## 4. Gateway → Proxy 신뢰 헤더

Gateway는 JWT 검증에 성공한 뒤(또는 개발 모드 규칙에 따라) 아래 헤더를 **Proxy로 전달**한다.

| 헤더 | 필수 | 설명 |
|------|------|------|
| `X-User-Id` | 예 | 플랫폼 사용자 식별자. **JWT 모드:** Gateway가 JWT **`sub` 클레임만** 넣는다(현재 Identity 구현에서 `sub`은 이메일). **개발 모드:** 클라이언트가 보낸 `X-User-Id`를 전달한다. |
| `X-Org-Id` | 아니오 | 조직 ID (JWT `org_id` 등) |
| `X-Team-Id` | 아니오 | 팀 ID (JWT `team_id` 등) |
| `X-Correlation-Id` | 아니오 | 분산 추적 ID; 없으면 Proxy에서 생성 가능 |
| `X-Gateway-Auth` | 예¹ | Gateway가 발급한 **공유 비밀** (스푸핑 방지) |

¹ **로컬 개발**에서 Proxy만 단독 실행할 때는 계약 §7에 따라 생략 가능.

클라이언트가 보낸 `Authorization`(플랫폼 JWT)은 **Gateway에서 소비**한다. Proxy·Usage로는 **§3.1 `RemoveRequestHeader=Authorization`** 으로 전달하지 않는다(이중 검증이 필요하면 별도 내부 토큰 정책으로 확장).

### 4.1 Usage HTTP·API Key 조회

- Usage(및 게이트웨이 뒤 동일 패턴의 REST)는 **`X-User-Id` 기반**으로 호출자를 식별한다. 별도 `X-Platform-User-Id` 헤더 계약은 두지 않는다.
- **개발 모드**에서도 신뢰 헤더 필터가 붙는 경로(`/api/v1/usage/**`)는 **`X-User-Id`가 있어야** 통과한다(§1.1).

### 4.2 Usage 원장 `user_id`와 `X-User-Id`·JWT `sub` 정합 (Identity·Usage·게이트웨이)

- **Usage DB·집계**의 `user_id` 문자열은 Proxy가 발행하는 `usage-recorded` 이벤트의 사용자 식별자와 같다. Proxy는 Gateway가 넘긴 **`X-User-Id`** 를 그대로 사용한다(구현: `ProxyRelayService`, `UserContext`).
- **운영(`gateway.dev-mode=false`)** 게이트웨이는 플랫폼 JWT 검증 후 **`X-User-Id` = JWT `sub`** 를 설정한다(구현: `ProxyTrustHeadersWebFilter` JWT 경로).
- **Identity 서비스** 액세스 토큰은 **`sub`에 로그인 이메일**을 넣는다(구현: `JwtTokenProvider.generateAccessToken` → `subject(user.getEmail())`). JWT에 `userId`(DB PK) 등 다른 클레임이 있어도, **게이트웨이·Usage 경로의 호출자 식별자 정본은 `sub`(이메일 문자열)** 이다.
- **로컬 `GATEWAY_DEV_MODE=true` + Usage BFF** 는 Identity `GET /api/auth/session`의 **`email`** 로 `X-User-Id`를 보강한다(`services/usage-service/web`). 따라서 **개발 모드에서 붙는 `X-User-Id`와 운영 JWT `sub`는 동일 규칙(이메일)** 으로 맞춰져, Proxy→Usage 원장과 대시보드 조회 키가 어긋나지 않는다.
- **회귀 테스트:** `ProxyTrustHeadersWebFilterTest`(api-gateway-service) — JWT `sub` → `X-User-Id`, 개발 모드·익명 컨텍스트 시 인바운드 `X-User-Id` 유지.

### 4.1 Proxy의 API Key 조회와 `userId`

- Proxy는 `UserContext.userId()` **한 값만** API Key Service(또는 mock) 조회에 사용한다. 이 값은 **`X-User-Id` 헤더**(또는 게이트웨이가 JWT로부터 설정한 동일 헤더)에서 온다. 구현: [`UserContextResolver`](../../services/proxy-service/src/main/java/com/eevee/proxyservice/security/UserContextResolver.java), [`ApiKeyClient`](../../services/proxy-service/src/main/java/com/eevee/proxyservice/key/ApiKeyClient.java)(`GET /internal/api-keys/{provider}?userId=...`).
- **`X-Platform-User-Id` 헤더를 읽거나**, JWT의 **`userId` 클레임을 별도 헤더로 Proxy에 넘겨 키 조회에 쓰는 코드는 없다.** Key Service가 숫자 PK를 요구하면 `X-User-Id`(현재는 JWT `sub` = 이메일)와의 매핑을 **Key Service·Identity·팀 정책**으로 맞추거나, 향후 게이트웨이/계약 확장 시 본 절을 개정한다.

---

## 5. 스푸핑 방지 (`X-Gateway-Auth`)

- Gateway와 Proxy는 동일한 **`GATEWAY_SHARED_SECRET`** (또는 설정으로 주입되는 동일 값)을 공유한다.
- Gateway는 모든 Proxy로의 요청에 `X-Gateway-Auth: <shared-secret>` 를 붙인다.
- Proxy는 해당 값이 일치할 때만 `/proxy/**` 요청을 처리한다(문자열 비교는 타이밍 공격에 주의).

---

## 6. 비기능

- **타임아웃:** Gateway의 upstream(Proxy) 타임아웃은 Proxy→Provider 타임아웃보다 **짧거나 같게** 두는 것을 권장한다.
- **바디 크기:** Proxy의 업스트림 `WebClient` 제한과 동일한 정책을 유지한다.
- **스트리밍:** `text/event-stream` 등은 hop-by-hop 헤더를 그대로 전달하지 않을 수 있으므로, Gateway 필터는 필요한 `Accept`/`Content-Type`을 보존한다.

---

## 7. 로컬 개발 (예외)

- **Proxy 단독:** `proxy.gateway.require-auth: false`(또는 동등 프로필)일 때, `X-Gateway-Auth` 없이 `X-User-Id`만으로 개발용 인증을 허용할 수 있다.
- **Gateway 단독 JWT 없음:** 동일하게 개발용 헤더만 허용하는 정책을 Gateway에 둘 수 있다(운영에서는 비활성).

---

## 8. 보안·로깅

- API Key·플랫폼 JWT 원문은 로그에 남기지 않는다 ([architecture.md](../architecture.md) §8.5).
- 운영 환경에서 Proxy는 **인터넷에 직접 노출하지 않는다**(로컬·스테이징 예외는 팀 정책).

---

## 9. 운영·Web BFF 베이스 URL (게이트웨이·Usage BFF 합의)

- **`API_GATEWAY_URL`** (Usage `web/` 환경 변수 — `services/usage-service/web`): 브라우저·Next.js BFF가 **호출하는 API Gateway의 공개 베이스 URL**이다(스킴+호스트+포트, trailing path 없음). 예: 로컬 `http://localhost:8080`, 운영 `https://<배포된 게이트웨이 호스트>`. Usage·AI 요청은 이 베이스에 `/api/v1/usage/...`, `/api/v1/ai/...` 를 붙인다.
- **`GATEWAY_PROXY_URI` / `GATEWAY_USAGE_URI`:** API Gateway **서버 프로세스**가 내부망에서 Proxy·Usage로 HTTP를 넘길 때만 쓴다. 웹 앱의 `API_GATEWAY_URL`과 **역할이 다르다**(웹은 게이트웨이 “입구”만 본다).
- 로컬에서 Identity(Spring)와 게이트웨이 **포트가 겹치면** `IDENTITY_SERVICE_URL`과 `API_GATEWAY_URL`을 팀이 분리해 맞춘다([`services/identity-service/web/.env.example`](../../services/identity-service/web/.env.example), [`services/usage-service/web/.env.example`](../../services/usage-service/web/.env.example), 루트 `.env.example` 주석).

운영 배포 시 **공개 DNS·TLS 종단점**이 가리키는 URL을 **API Gateway 운영 담당**과 **Usage `web`/BFF 담당**이 동일 값으로 두는 것을 합의 기준으로 한다.

---

## 10. 포트(로컬 기본값)

| 서비스 | 기본 포트 |
|--------|-----------|
| API Gateway | 8080 |
| Proxy | 8081 |

팀이 변경 시 본 문서와 `application.yml`을 함께 갱신한다.
