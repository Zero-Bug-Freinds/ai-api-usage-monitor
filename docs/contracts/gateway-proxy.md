# Gateway ↔ Proxy 서비스 간 계약

버전: 1.1  
관련: [docs/architecture.md](../architecture.md) §4.1, §4.2, [web-gateway-bff.md](./web-gateway-bff.md)(웹 BFF·`API_GATEWAY_URL`은 usage 표만 정의; **AI 경로 정본은 본 문서**)

---

## 1. 역할

| 구분 | API Gateway | Proxy |
|------|-------------|--------|
| 배포 위치 | 외부에 노출되는 HTTP 진입점 | Gateway 뒤(또는 신뢰 네트워크)에서만 접근 |
| 플랫폼 인증 | 클라이언트 `Authorization: Bearer <JWT>` 검증 | 클라이언트 JWT를 **검증하지 않음** |
| AI Provider 키 | 다루지 않음 | API Key Service 또는 환경 변수(mock)로 주입 |
| 사용량 이벤트 | 발행하지 않음 | RabbitMQ로 `usage-recorded` 성격 메시지 발행 |

---

## 2. 공개 URL (클라이언트 → Gateway)

- **Base path:** `/api/v1/ai`
- **패턴:** `/api/v1/ai/{provider}/**`
- `{provider}`: `openai` | `anthropic` | `google` ([AiProvider](../../libs/usage-events/src/main/java/com/eevee/usage/events/AiProvider.java)와 동일)

**예:** `POST /api/v1/ai/openai/v1/chat/completions`

---

## 3. Proxy 내부 URL (Gateway → Proxy)

- **Base path:** `/proxy`
- **패턴:** `/proxy/{provider}/**`

Gateway는 아래 **경로 rewrite**를 적용한다.

| 클라이언트 요청 (Gateway) | Proxy로 전달되는 path |
|---------------------------|-------------------------|
| `/api/v1/ai/{provider}/...` | `/proxy/{provider}/...` |

**AI 라우트 필터:** Gateway는 `/api/v1/ai/**` 에 대해 **`RemoveRequestHeader=Authorization`** 을 적용한다. 클라이언트가 보낸 플랫폼 JWT(또는 기타 `Authorization`)는 **Proxy로 전달되지 않는다**(게이트웨이 보안 체인에서 소비·검증 후 라우팅). 구현: [`api-gateway-service` `application.yml`](../../services/api-gateway-service/src/main/resources/application.yml).

**로컬 개발(`gateway.dev-mode=true`):** JWT 없이 호출할 수 있으며, 이 경우 **`X-User-Id`** 는 클라이언트가 보낸 값을 게이트웨이 필터가 그대로 신뢰 경로에 실어 Proxy로 넘긴다(`ProxyTrustHeadersGatewayFilter`). Mock 키(`PROXY_GOOGLE_TEST_API_KEY` 등)를 쓰면 Key Service의 `userId`는 사실상 mock 분기에서 무시되기 쉽다.

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

클라이언트가 보낸 `Authorization`(플랫폼 JWT)은 **Gateway에서 소비**한다. Proxy로 전달하지 않는 것을 원칙으로 한다(이중 검증이 필요하면 별도 내부 토큰 정책으로 확장).

### 4.1 Proxy의 API Key 조회와 `userId`

- Proxy는 `UserContext.userId()` **한 값만** API Key Service(또는 mock) 조회에 사용한다. 이 값은 **`X-User-Id` 헤더**(또는 게이트웨이가 JWT로부터 설정한 동일 헤더)에서 온다. 구현: [`UserContextResolver`](../../services/proxy-service/src/main/java/com/eevee/proxyservice/security/UserContextResolver.java), [`ApiKeyClient`](../../services/proxy-service/src/main/java/com/eevee/proxyservice/key/ApiKeyClient.java)(`GET /internal/api-keys/{provider}?userId=...`).
- **`X-Platform-User-Id` 헤더를 읽거나**, JWT의 **`userId` 클레임을 별도 헤더로 Proxy에 넘겨 키 조회에 쓰는 코드는 없다.** Key Service가 숫자 PK를 요구하면 `X-User-Id`(현재는 JWT `sub` = 이메일)와의 매핑을 **Key Service·Identity·팀 정책**으로 맞추거나, 향후 게이트웨이/계약 확장 시 본 절을 개정한다.

---

## 5. 스푸핑 방지 (`X-Gateway-Auth`)

- Gateway와 Proxy는 동일한 **`PROXY_GATEWAY_SHARED_SECRET`** (또는 설정으로 주입되는 동일 값)을 공유한다.
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

## 9. 포트(로컬 기본값)

| 서비스 | 기본 포트 |
|--------|-----------|
| API Gateway | 8080 |
| Proxy | 8081 |

팀이 변경 시 본 문서와 `application.yml`을 함께 갱신한다.
