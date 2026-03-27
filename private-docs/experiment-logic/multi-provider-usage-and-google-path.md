# 다중 AI 프로바이더 사용량 표시 및 Google(Gemini) 경로

버전: 1.0  

ChatGPT(OpenAI)와 Google API(Gemini 등)를 함께 쓸 때, **어떤 제공자인지 DB에 남기고**, 게이트웨이·프록시가 지원하는 **`google` 세그먼트 경로**로 호출하려면 무엇을 맞춰야 하는지 정리한 문서입니다.  
관련 절차 문서: [`local-run-and-usage-verification.md`](local-run-and-usage-verification.md), 계약: [`contracts/gateway-proxy.md`](contracts/gateway-proxy.md).

---

## 1. 해결하고자 하는 문제

| 구분 | 설명 |
|------|------|
| **비즈니스 목표** | 다양한 AI API의 **사용량을 집계·저장**할 때, DB에서 **어느 제공자(OpenAI vs Google 등)인지** 구분해 조회·리포트하고 싶다. |
| **범위(현 단계)** | **OpenAI(ChatGPT 계열)** 와 **Google API(Gemini, 나노 바나나 등 모델)** 만 고려한다. |
| **기술적 과제** | (1) 클라이언트가 **프로바이더별 REST 경로·요청 본문**을 맞춰 호출해야 한다. (2) 로컬·개발에서 **OpenAI 키(`sk-...`)와 Google 키(`AIza...`)를 동시에** 쓰려면, 현재 **단일 mock 키**만으로는 부족할 수 있어 **키 해석 로직을 프로바이더별로 나눌** 필요가 있다. |

---

## 2. 현재 코드베이스에서 이미 성립하는 것

- **게이트웨이**는 `/api/v1/ai/**`를 `/proxy/**`로 넘기며, 첫 경로 세그먼트가 곧 프로바이더 이름(`openai`, `google`, …)이 된다.
- **프록시**는 `/proxy/{provider}/...`에서 `AiProvider`를 선택하고, `UsageRecordedEvent`에 그 enum을 넣어 Rabbit로 발행한다.
- **usage-service**는 `usage_recorded_log`에 **`provider`(문자열 enum)**와 **`model`** 등을 저장한다.

즉 **“DB에 어떤 AI인지”는 `provider` + `model`로 표현할 수 있는 구조**이며, 경로만 올바르면 **추가 스키마 변경 없이** OpenAI vs Google 구분이 반영된다.

---

## 3. 해결 방법

### 3.1 호출 측: 게이트웨이 URL과 요청 스펙

| 제공자 | 게이트웨이 예시 경로(개념) | 요청 본문 |
|--------|---------------------------|-----------|
| **OpenAI** | `POST .../api/v1/ai/openai/v1/chat/completions` | OpenAI Chat Completions JSON (`messages` 등) |
| **Google (Generative Language API)** | `POST .../api/v1/ai/google/<공식 API 경로 접미사>` | **Gemini REST 스펙** (`contents` 등). OpenAI 포맷을 그대로 쓰면 안 된다. |

프록시의 Google 핸들러는 upstream 베이스(`generativelanguage.googleapis.com` 등)에 **remainder 경로**를 붙이고, **`key` 쿼리 파라미터**로 API 키를 전달한다.  
클라이언트는 사용하는 **모델·메서드**(예: `generateContent`)에 맞는 **공식 문서의 경로와 JSON**을 따라야 한다.

### 3.2 게이트웨이·프록시 애플리케이션 설정

- **게이트웨이**: 프로바이더마다 별도 라우트를 추가할 필요는 없다. 기존 `Path=/api/v1/ai/**` + `RewritePath` 한 벌로 `openai`/`google` 모두 처리된다.
- **프록시**: `proxy.providers.google.base-url` 등은 `application.yml`·프로파일로 유지. 응답의 **`usageMetadata`**(및 필요 시 `model`) 필드가 실제 API 응답과 다르면 `GoogleProviderHandler`의 usage 파싱을 그 스펙에 맞게 조정한다(비스트리밍·스트리밍 각각).

### 3.3 키 해석: 프로바이더별 mock(또는 키 서비스)

- **운영**: API Key 서비스가 `provider` 세그먼트별로 키를 내려주는 모델이 이미 가정되어 있다.
- **로컬 mock**: `ApiKeyClient`가 `mockKey` 하나만 쓰면, **OpenAI 경로에는 `sk-`, Google 경로에는 `AIza-`를 동시에 넣을 수 없다.**  
  **권장**: 설정을 **프로바이더별 mock**(예: OpenAI용·Google용 환경 변수 또는 `application.yml` 필드)으로 나누고, `resolveApiKey(userId, provider)`에서 `provider`에 따라 반환한다.  
  **대안**: 키 문자열 패턴으로 자동 추측하기보다는 **요청 경로의 프로바이더와 설정 매핑**이 명확하고 안전하다.

### 3.4 DB·usage-service

- **필수 스키마 변경은 없음**: `provider` = `OPENAI` / `GOOGLE`, `model` = 응답에서 파싱한 모델 식별자.
- UI에서 “ChatGPT”“Gemini” 등 **표시 이름**이 필요하면 enum·모델 id를 레이블로 매핑하는 레이어만 추가하면 된다.

---

## 4. 해결책을 적용했을 때 예상되는 결과

| 항목 | 예상 결과 |
|------|-----------|
| **호출** | 같은 게이트웨이 베이스 URL로 `.../openai/...`와 `.../google/...`를 구분해 호출할 수 있다. |
| **upstream** | Google 호출 시 Generative Language API로 올바른 경로·쿼리(`key`)·본문이 전달된다. |
| **사용량 이벤트** | 프록시가 `AiProvider.GOOGLE` 또는 `OPENAI`로 이벤트를 채워 발행한다. |
| **DB** | `usage_recorded_log`에 **`provider`·`model`·토큰 수** 등이 프로바이더별로 적재되어, 집계 시 제공자별 필터가 가능하다. |
| **로컬 개발** | mock 키를 프로바이더별로 두면 **문서의 OpenAI 예시와 Google 예시를 같은 `.env`/Compose로 번갈아 검증**하기 쉽다. |

주의: Google 응답 형식이 바뀌거나 스트리밍만 다른 스키마를 쓰는 경우, **토큰 파싱이 null이 될 수 있으므로** 그때는 핸들러 구현을 해당 응답 스펙에 맞게 보완해야 한다.

---

## 5. 정리

1. **문제**: 여러 AI의 사용량을 DB에서 구분해 보고 싶고, Google은 **전용 REST 경로·본문**이 필요하다.  
2. **방법**: 클라이언트는 **`/api/v1/ai/{openai|google}/...`** 로 구분 호출하고, Google은 **공식 Gemini API 스펙**을 따른다. 게이트웨이는 통합 라우트로 충분하다. 키는 **프로바이더별 설정(또는 키 서비스)** 으로 나눈다.  
3. **결과**: `usage_recorded_log.provider` 및 `model`로 **제공자·모델 단위** 사용량을 저장·조회할 수 있고, 로컬에서도 두 키를 동시에 쓸 수 있다(설정 분리 후).  
4. **코드 변경이 실제로 필요한 지점**: 주로 **`ApiKeyClient` + `ProxyProperties`/환경 변수**, 필요 시 **`GoogleProviderHandler` usage 파싱**, 문서·예시(`local-run-and-usage-verification.md` 보조) 정도이다.

---

## 6. 관련 파일(참고)

| 파일 | 역할 |
|------|------|
| `libs/usage-events/.../AiProvider.java` | `OPENAI`, `GOOGLE`, … |
| `services/proxy-service/.../ProxyRelayService.java` | 경로에서 프로바이더 선택·이벤트 구성 |
| `services/proxy-service/.../GoogleProviderHandler.java` | Google URI·usage 파싱 |
| `services/proxy-service/.../key/ApiKeyClient.java` | 키 해석(mock / 키 서비스) |
| `services/usage-service/.../UsageRecordedLogEntity.java` | DB `provider`, `model` |
| `services/api-gateway-service/.../application.yml` | `/api/v1/ai/**` → 프록시 rewrite |

---

## 7. 구현 상태(코드 반영)

로컬 mock 키를 **프로바이더별로 분리**하는 방식이 적용되었다.

| 설정(`application.yml`) | 환경 변수 | 의미 |
|---------------------------|-----------|------|
| `proxy.key-service.mock-key-openai` | `PROXY_OPENAI_TEST_API_KEY` | OpenAI upstream용 테스트 키 |
| `proxy.key-service.mock-key-google` | `PROXY_GOOGLE_TEST_API_KEY` | Google Generative Language API용 테스트 키 |
| `proxy.key-service.mock-key` | `PROXY_MOCK_KEY_FALLBACK` | 위 둘이 비어 있을 때만 쓰는 **공통 폴백**(선택) |

`ApiKeyClient`는 `resolveApiKey(userId, provider)`에서 **해당 프로바이더 전용 mock이 있으면 그것을**, 없으면 **폴백 `mock-key`**를, 둘 다 없으면 **키 서비스**를 사용한다. Docker Compose의 `proxy-service`는 위 환경 변수를 전달하도록 맞춰 두었다(`.env.example` 참고).
