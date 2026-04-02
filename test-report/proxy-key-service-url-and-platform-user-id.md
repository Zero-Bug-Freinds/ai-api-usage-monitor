# 프록시 키 서비스 URL 및 플랫폼 사용자 ID 조회 정합 변경 보고

문서 목적: 게이트웨이 경유 AI 프록시 호출 시 발생한 **HTTP 500** 및 내부 **키 서비스 401** 문제를 해결하기 위해 적용한 **설정·코드 변경**의 배경, 근거, 파일별 상세 내용을 기록한다.

---

## 1. 당시 실패 상황 (curl / API 응답)

### 1.1 증상

- 클라이언트가 게이트웨이(예: `localhost:8080`)를 통해 Google Gemini 프록시 경로로 요청한 경우, 응답이 **500 Internal Server Error** 로 떨어짐.
- 응답 본문 예시:

```json
{
  "timestamp": "2026-04-01T16:50:07.059Z",
  "path": "/proxy/google/v1beta/models/gemini-2.0-flash:generateContent",
  "status": 500,
  "error": "Internal Server Error",
  "requestId": "9e0c10d5-1"
}
```

- 표면적으로는 프록시의 `/proxy/google/...` 처리 중 예외가 전역 핸들러에 의해 500으로 매핑된 것으로 보인다.

### 1.2 게이트웨이 로그에서 보이는 것 (참고)

- 동일 시각대에 `ProxyTrustHeadersWebFilter` 가 `/api/v1/ai/google/v1beta/models/gemini-2.0-flash:generateContent` 경로에서 JWT 인증을 인식하고 `applyTrustHeaders` 를 수행한 로그가 남음.
- 이는 **게이트웨이 → 프록시로 라우팅되기 전/후** 트러스트 헤더 필터가 동작했음을 시사한다. 다만 **500의 직접 원인은 프록시 쪽 스택트레이스**에서 확인하는 것이 정확하다.

---

## 2. 프록시 로그 분석 및 원인 특정 근거

### 2.1 핵심 에러 메시지

프록시 컨테이너 로그에 다음이 명확히 기록됨.

- `java.lang.IllegalStateException: key service error: 401 UNAUTHORIZED`
- 발생 위치: `com.eevee.proxyservice.key.ApiKeyClient.loadKeyBlocking`

즉, **업스트림 Google 호출 전에** 프록시가 **내부 API 키 조회**를 시도했고, 그 HTTP 호출이 **401** 로 실패하여 상위에서 `IllegalStateException` 으로 감싸져 500으로 전파된 것이다.

### 2.2 WebClient 가 실제로 호출한 URL (결정적 근거)

같은 로그의 **Caused by** 에 다음이 포함됨.

```text
401 Unauthorized from GET http://host.docker.internal:8080/internal/api-keys/google
```

이 한 줄이 **원인 특정의 핵심 근거**다.

- 포트 **8080** 은 이 프로젝트의 **API Gateway** 가 기본으로 바인딩하는 포트이고,
- **identity-service(API Key 내부 조회)** 는 로컬/호스트에서 보통 **8090** 등 별도 포트에서 기동한다.
- 따라서 프록시의 `WebClient` **base URL** 이 잘못되어 **게이트웨이(8080)** 에 `GET /internal/api-keys/google` 를 보낸 것이다.
- 게이트웨이의 해당 경로는 내부 키 API가 아니거나, 인증 정책상 **Bearer 내부 토큰으로 통과하지 못해 401** 을 반환하는 것으로 해석할 수 있다.

**결론 (1차 직접 원인):**  
`proxy.key-service.base-url` (또는 이를 덮는 `PROXY_KEY_SERVICE_BASE_URL`) 이 **identity(8090)** 가 아니라 **8080(게이트웨이)** 으로 잡혀 있었고, 그 결과 키 조회가 401 로 실패했다.

### 2.3 클라이언트에 보이는 500 과의 연결

- `ApiKeyClient` 는 `WebClientResponseException` (401) 을 잡아 `IllegalStateException("key service error: " + status)` 로 다시 던진다.
- 프록시의 글로벌 예외 처리가 이를 **500** 으로 노출할 수 있어, 사용자는 **curl 500** 만 보게 된다.
- `requestId` (`9e0c10d5-1`) 는 프록시 로그의 동일 요청 추적에 대응된다.

---

## 3. 부차적 이슈: userId 타입 (이메일 vs Long PK)

설정을 8090 으로 맞춘 뒤에도, identity 의 내부 API가 `userId` 를 **숫자 PK(Long)** 로만 받는다면:

- 게이트웨이가 넘기는 `X-User-Id` 는 JWT `sub` 로 **이메일**일 수 있고,
- 그대로 `?userId=<이메일>` 로 조회하면 **400** 등으로 실패할 수 있다.

그래서 **2차 조치**로:

- 게이트웨이가 JWT 의 `userId` 클레임을 **`X-Platform-User-Id`** 로 전달하고,
- 프록시는 키 조회 시 **`keyLookupUserId()`** 로 플랫폼 ID를 우선 사용한다.

이 문서의 **1차 원인**은 로그상 **8080 호출 + 401** 이다. **2차**는 identity 계약에 따른 식별자 정합이다.

---

## 4. 변경을 “왜” 했는지 (요약)

| 목적 | 이유 |
|------|------|
| 키 서비스 base URL 을 identity 로 | 로그에 나온 대로 8080 은 게이트웨이였고, 내부 키 API는 identity 쪽으로 가야 함 |
| `application-docker.yml` 기본값 | 환경 변수 미설정 시에도 Docker 프로필에서 잘못된 기본(8080)을 쓰지 않게 함 |
| `docker-compose.yml` 에 `PROXY_KEY_SERVICE_BASE_URL` | 배포/로컬에서 동일 변수명으로 명시적으로 덮어쓰기 쉬움 |
| `UserContext` + `X-Platform-User-Id` | 키 조회용 ID와 과금/로그용 subject(`X-User-Id`)를 분리 |
| `ProxyTrustHeadersWebFilter` | JWT `userId` → `X-Platform-User-Id` 를 프록시에 실어 보냄 |
| 테스트 보강 | 회귀 시 헤더/클레임 동작을 단위 테스트로 고정 |

---

## 5. 파일별 변경 상세 (“무엇을”, “어떻게”)

### 5.1 `services/proxy-service/src/main/resources/application-docker.yml`

**무엇을:** `proxy.key-service.base-url` 의 **기본 호스트/포트**를 게이트웨이(8080)가 아닌 **identity(8090)** 에 맞춤.

**어떻게:**

- `base-url: ${PROXY_KEY_SERVICE_BASE_URL:http://host.docker.internal:8090}`
- 주석으로 “게이트웨이 8080 과 혼동하지 말 것 / identity 기본 8090” 을 명시.

**로직:** Spring 이 `docker` 프로필 활성 시 이 YAML 을 읽고, `ProxyProperties` 바인딩을 통해 `ApiKeyClient` 의 `WebClient` base URL 이 결정된다.

---

### 5.2 루트 `docker-compose.yml` (`proxy-service` 서비스)

**무엇을:** 컨테이너 환경에 **`PROXY_KEY_SERVICE_BASE_URL`** 을 명시적으로 주입.

**어떻게:**

```yaml
PROXY_KEY_SERVICE_BASE_URL: ${PROXY_KEY_SERVICE_BASE_URL:-http://host.docker.internal:8090}
```

**로직:** 호스트의 `.env` 나 셸에서 값을 주면 그것을 쓰고, 없으면 기본으로 `8090` 을 사용한다. `extra_hosts` 의 `host.docker.internal:host-gateway` 와 함께 컨테이너에서 호스트의 identity 에 접근한다.

---

### 5.3 `services/proxy-service/.../security/UserContext.java`

**무엇을:** 게이트웨이 subject(`userId`)와 플랫폼 PK(`platformUserId`)를 **한 컨텍스트**에 담음.

**어떻게:**

- record 필드 추가: `String platformUserId` (없으면 null 가능).
- 메서드 추가: `keyLookupUserId()` — `platformUserId` 가 비어 있지 않으면 그 값, 아니면 `userId`.

**로직:** Usage 이벤트 등에는 기존처럼 `userId`(이메일 등)를 쓰고, **키 조회 HTTP 의 `userId` 쿼리 파라미터**만 플랫폼 ID 우선으로 분리할 수 있다.

---

### 5.4 `services/proxy-service/.../security/UserContextResolver.java`

**무엇을:** 요청 헤더 **`X-Platform-User-Id`** 를 읽어 `UserContext.platformUserId` 에 넣음.

**어떻게:**

- 상수 `HDR_PLATFORM_USER = "X-Platform-User-Id"` 추가.
- `mapAuthentication` 초기에 `firstNonBlankHeader(exchange, HDR_PLATFORM_USER)` 로 값을 구함.
- 인증 분기(문자열 principal / 헤더 `X-User-Id`) **모두**에서 `new UserContext(userId, platformUserId, org, team, correlationId)` 형태로 전달.

**로직:** 게이트웨이가 프록시로 전달한 트러스트 헤더를 그대로 도메인 컨텍스트로 옮긴다.

---

### 5.5 `services/proxy-service/.../relay/ProxyRelayService.java`

**무엇을:** API 키 해석 시 **`ctx.userId()` 대신 `ctx.keyLookupUserId()`** 사용.

**어떻게:**

- `apiKeyClient.resolveApiKey(ctx.keyLookupUserId(), provider)`

**로직:** managed 키 경로에서 identity 가 기대하는 식별자(숫자 PK)가 오도록 하고, mock 키만 쓰는 환경에서는 여전히 동작 가능(캐시 키 문자열만 달라짐).

---

### 5.6 `services/proxy-service/.../key/ApiKeyClient.java`

**무엇을:** 메서드 시그니처 의미를 명확히 하고, 내부 변수명을 `keyLookupUserId` 로 통일.

**어떻게:**

- `resolveApiKey(String keyLookupUserId, AiProvider provider)` — Javadoc 으로 “숫자 플랫폼 ID 우선, 없으면 subject” 설명.
- `loadKeyBlocking` 내에서 캐시 키 파싱 후 `queryParam("userId", keyLookupUserId)` 로 전달.

**로직:** `WebClient` GET 경로는 그대로 `/internal/api-keys/{provider}?userId=...` 이며, **넘기는 문자열의 의미만** 플랫폼 ID 우선으로 바뀐다.

---

### 5.7 `services/api-gateway-service/.../filter/ProxyTrustHeadersWebFilter.java`

**무엇을:** JWT 모드에서 **`userId` 클레임 → `X-Platform-User-Id`** 헤더 추가. 개발 모드에서는 클라이언트가 넣은 `X-Platform-User-Id` 를 유지.

**어떻게:**

- `forwardWithJwt`: `jwt.getClaimAsString("userId")` 가 비어 있지 않으면 `req.header(HDR_PLATFORM_USER, platformUserId)`.
- `forwardDevHeaders`: 인바운드 요청에 `X-Platform-User-Id` 가 있으면 downstream 으로 다시 심음.

**로직:** 프록시는 게이트웨이를 신뢰하므로, 이 헤더만 있으면 `UserContextResolver` 가 PK 를 복원할 수 있다.

---

### 5.8 테스트 코드

| 파일 | 내용 |
|------|------|
| `services/proxy-service/src/test/.../security/UserContextResolverTest.java` | `X-Platform-User-Id` 가 있을 때 `keyLookupUserId()` 가 플랫폼 ID를 택하는지, 없을 때 `X-User-Id` 로 폴백하는지 검증 |
| `services/api-gateway-service/src/test/.../filter/ProxyTrustHeadersWebFilterTest.java` | JWT 에 `userId` 클레임이 있으면 `X-Platform-User-Id` 가 전달되는지, dev 모드에서 인바운드 헤더가 유지되는지 검증 |

---

## 6. 배포·검증 절차 (권장)

1. `services/proxy-service` 에서 `./gradlew bootJar` 로 JAR 갱신.
2. `docker compose up -d --build --force-recreate proxy-service` (게이트웨이 필터를 바꿨다면 `api-gateway-service` 도 동일하게 재빌드).
3. 컨테이너 내부 확인 (Windows PowerShell 예시):

   ```powershell
   docker compose exec proxy-service printenv | findstr PROXY_KEY
   ```

   기대: `PROXY_KEY_SERVICE_BASE_URL=http://host.docker.internal:8090` (또는 팀이 지정한 identity URL).

4. 호스트에서 identity 가 떠 있는 전제 하에, 내부 키 API가 허용하는 형태로:

   ```text
   curl "http://localhost:8090/internal/api-keys/google?userId=<숫자PK>"
   ```

   가 **200 + plainKey** 인지 확인 (내부 토큰 필요 시 헤더 포함).

5. 동일 사용자로 게이트웨이 경유 AI 호출 재시도 후, 프록시 로그에 **`401 ... host.docker.internal:8080/internal/api-keys`** 가 **더 이상 나오지 않는지** 확인.

---

## 7. 변경 파일 목록 (체크리스트)

- [x] `services/proxy-service/src/main/resources/application-docker.yml`
- [x] `docker-compose.yml` (`proxy-service.environment`)
- [x] `services/proxy-service/.../security/UserContext.java`
- [x] `services/proxy-service/.../security/UserContextResolver.java`
- [x] `services/proxy-service/.../relay/ProxyRelayService.java`
- [x] `services/proxy-service/.../key/ApiKeyClient.java`
- [x] `services/api-gateway-service/.../filter/ProxyTrustHeadersWebFilter.java`
- [x] `services/proxy-service/src/test/.../security/UserContextResolverTest.java` (신규)
- [x] `services/api-gateway-service/src/test/.../filter/ProxyTrustHeadersWebFilterTest.java` (케이스 추가)

---

## 8. 한 줄 요약

- **실패:** 키 조회가 `http://host.docker.internal:8080/internal/api-keys/google` 로 나가 **401** → 프록시 **500**.
- **1차 수정:** base URL 을 **identity(8090)** 로 고정 가능하게 YAML·Compose·재빌드.
- **2차 수정:** JWT `userId` → `X-Platform-User-Id` → 프록시 `keyLookupUserId()` 로 **Long PK** 조회 정합.

이 문서는 위 변경에 대한 **테스트·운영 보고용** 기록이다.
