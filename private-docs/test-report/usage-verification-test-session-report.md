# 사용량 검증 테스트 세션 보고서

버전: 1.0  
작성 기준: 로컬에서 `docs/local-run-and-usage-verification.md` 절차에 따라 **게이트웨이 → 프록시 → Provider → RabbitMQ → usage-service → PostgreSQL** 흐름을 검증하려던 세션에서 수행한 작업·결과·수정 사항을 정리한 문서입니다.

---

## 1. 이번에 확인하고자 한 목표

| 구분 | 내용 |
|------|------|
| **주 목표** | [`local-run-and-usage-verification.md`](local-run-and-usage-verification.md)에 따라, 실제 호출 시 **`UsageRecordedEvent`가 발행되고 `usage-service`가 이를 소비해 DB(`usage_recorded_log`)에 저장**되는지 로컬에서 검증한다. |
| **부 목표** | Docker Compose로 띄운 인프라(PostgreSQL, RabbitMQ, Redis, `proxy-service`, `api-gateway-service`)와 호스트의 `usage-service`가 문서와 같이 동작하는지 확인한다. |

문서상 “최종 확인”은 PostgreSQL에서 `usage_recorded_log` 조회(§5)이며, 자동 검증으로는 `usage-service`의 통합 테스트(§6)가 해당된다.

---

## 2. 참조한 문서·파일

- 절차: [`docs/local-run-and-usage-verification.md`](local-run-and-usage-verification.md)
- Compose: 저장소 루트 `docker-compose.yml`
- 비밀·로컬 오버라이드: 루트 `.env`(커밋 대상 아님), `.env.example`

---

## 3. 세션에서 수행한 단계와 결과

### 3.1 인프라·서비스 가동 상태 확인

**한 일**

- 저장소 루트에서 `docker compose ps -a`로 컨테이너 상태를 확인했다.

**결과**

- **성공.** PostgreSQL, RabbitMQ, Redis, `proxy-service`, `api-gateway-service`가 기동 중이었고, 포트 매핑(예: 게이트웨이 `8080`, 프록시 `8081`)이 열려 있었다.

---

### 3.2 게이트웨이 헬스 확인

**한 일**

- `GET http://localhost:8080/actuator/health` 호출.

**결과**

- **성공.** HTTP 200, 애플리케이션 상태 UP.

---

### 3.3 문서 §2와 동일한 AI 호출 — 첫 시도 (PowerShell + 인라인 JSON)

**한 일**

- `POST http://localhost:8080/api/v1/ai/openai/v1/chat/completions`
- 헤더: `Content-Type: application/json`, `X-User-Id: …`
- 본문: `curl.exe`의 `-d`에 JSON 문자열을 직접 넣어 전송.

**결과**

- **실패(요청 형식).** PowerShell·`curl` 조합에서 JSON 이스케이프가 깨져 **본문이 수 바이트만 전송**되는 현상이 있었다. 게이트웨이 응답은 `404`였고, 이는 아래 3.5에서 밝힌 **라우트 미등록**과 겹쳐 단독 원인으로 보기 어려웠다.

---

### 3.4 동일 호출 — JSON 파일을 이용한 재시도

**한 일**

- 프로젝트 루트에 임시 JSON 파일(예: 모델·메시지가 담긴 chat 요청 본문)을 두고, `curl.exe`의 `--data-binary "@파일"`로 전송.

**결과**

- **부분적 개선.** 본문은 올바르게 전달되었다.
- 그러나 응답은 여전히 **`404 Not Found`**(경로는 `/api/v1/ai/openai/v1/chat/completions` 등). 원인 분석으로 3.5로 이어졌다.
- 검증 종료 후 해당 임시 파일은 **삭제**하여 저장소에 남기지 않았다.

---

### 3.5 게이트웨이 라우트 미적용 원인 조사

**한 일**

- Spring Cloud Gateway **5.x**와 `spring-cloud-starter-gateway-server-webflux` 조합에서, 구 설정 키 `spring.cloud.gateway.routes`만으로는 **런타임에 라우트가 바인딩되지 않을 수 있다**는 자료·동작을 참고했다.
- 실행 중인 게이트웨이 컨테이너의 JAR 내부 `application.yml`을 확인해, 설정이 기대와 일치하는지 검토했다.

**결과**

- **원인 확정(설정 네임스페이스).** 이 스택에서는 라우트를 **`spring.cloud.gateway.server.webflux.routes`** 아래에 두어야 한다. 기존 `spring.cloud.gateway.routes`만으로는 **유효 라우트가 비어** `/api/v1/ai/**` 요청이 매칭되지 않고 `404`가 났다.

---

### 3.6 게이트웨이 설정 수정 및 이미지 재빌드

**한 일**

- `services/api-gateway-service/src/main/resources/application.yml`에서 라우트 정의를 `spring.cloud.gateway.server.webflux.routes`로 이동(동일한 `proxy-ai` 라우트·`RewritePath` 유지).
- **`bootJar`로 `app.jar`를 갱신한 뒤**, Docker가 이전 레이어 캐시만 쓰지 않도록 **`docker compose build --no-cache api-gateway-service`** 후 **`docker compose up -d api-gateway-service`**로 컨테이너를 재생성했다.

**결과**

- **성공.** 컨테이너 JAR에 새 YAML이 포함되었고, 이후 동일 POST는 게이트웨이가 **프록시로 전달**되며, 응답 본문의 `path`가 **`/proxy/openai/v1/chat/completions`** 형태로 바뀌었다(즉, 라우트·리라이트가 동작).

---

### 3.7 프록시 직접 호출 및 로그 분석

**한 일**

- 게이트웨이 없이 `POST http://localhost:8081/proxy/openai/v1/chat/completions` 등으로 프록시만 호출해 비교했다.
- `docker logs`로 `proxy-service` 스택트레이스를 확인했다.

**결과**

- **실패(런타임 오류).** 한 시점에서는 컨테이너 내부 **DNS(`api.openai.com` 등) 오류** 로그가 보였고, 다른 시점에서는 **Jackson이 `java.time.Instant`를 직렬화하지 못해** `UsageRecordedEvent` 발행 단계에서 예외가 났다.
- 후자는 **`UsageEventPublisher`가 `ObjectMapper.writeValueAsString(event)`** 수행 시 **`Java 8 date/time type java.time.Instant not supported`** 로 터지는 형태였다.

---

### 3.8 프록시 Jackson(JSR-310) 수정 및 이미지 재빌드

**한 일**

- `ObjectMapper`에 **`JavaTimeModule` 등록**, **`WRITE_DATES_AS_TIMESTAMPS` 비활성화**, Bean에 **`@Primary`** 지정.
- `build.gradle`에 **`com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2`** 명시.
- `services/proxy-service`에서 **`bootJar`** 후 **`docker compose build --no-cache proxy-service`**, **`docker compose up -d proxy-service`**.

**결과**

- **성공(직렬화).** 동일 흐름에서 **Instant 직렬화 예외는 재현되지 않았고**, 요청이 OpenAI까지 도달하는 단계로 진행되었다.

---

### 3.9 엔드포인트·API 키 종류 불일치 (문서 §2 경로 + Google 키)

**한 일**

- 게이트웨이 경로 문서 §2: `.../api/v1/ai/openai/v1/chat/completions` (OpenAI 업스트림).
- 루트 `.env`의 `PROXY_OPENAI_TEST_API_KEY`에 **Google API 키 형식(`AIza...`)** 이 설정된 상태로 동일 POST를 재실행.

**결과**

- **실패(업스트림 인증).** HTTP **401**, OpenAI 오류 본문에 **`Incorrect API key provided`** 및 **`invalid_api_key`** 가 포함되었다.  
  즉, **OpenAI Chat Completions 경로에 Google(Gemini) 키를 넣은 것**과 맞지 않아, **성공적인 200 응답·토큰 usage 파싱·이벤트 발행**까지는 이 구성만으로 도달하지 못했다.

---

### 3.10 usage-service: 통합 테스트로 “Rabbit → DB” 검증

**한 일**

- `services/usage-service`에서 아래 테스트만 실행했다.  
  - `com.eevee.usageservice.integration.UsageRecordedEventPipelineIntegrationTest#jsonPublishedLikeProxy_isConsumedAndPersisted`
- Testcontainers로 RabbitMQ·PostgreSQL을 띄우는 방식이며, **로컬에 Docker 엔진**이 필요하다.

**결과**

- **성공.** Gradle **`BUILD SUCCESSFUL`**.  
  프록시가 Rabbit에 넣는 것과 **동일한 방식의 JSON 문자열**을 `usage.events` / `usage.recorded`로 보냈을 때, **리스너가 역직렬화하고 DB에 저장**하는 것이 확인되었다.

---

### 3.11 문서 §5: Compose PostgreSQL에서 `usage_recorded_log` 직접 조회

**한 일**

- 이번 세션에서는 **유효한 OpenAI 키로 §2 호출이 끝까지 성공한 뒤** 큐를 타고 Compose DB에 쌓이는 **완전한 E2E**까지는 실행하지 않았다(3.9 참고).

**결과**

- **미실행(세션 한계).**  
  다만 3.10 통합 테스트로 **“동일 이벤트 형식이면 usage-service는 DB에 저장 가능”** 은 검증되었다.  
  Compose의 `postgres`에 행을 남기려면 **§1.3대로 호스트에서 `usage-service`를 띄운 상태**에서, **프로바이더와 키가 일치하는 호출**으로 이벤트가 실제로 발행되어야 한다.

---

## 4. 코드·빌드 설정에서 바뀐 내용 요약

| 위치 | 변경 요약 |
|------|-----------|
| `services/api-gateway-service/src/main/resources/application.yml` | 라우트를 `spring.cloud.gateway.server.webflux.routes` 하위로 이동. SCG 5 + `gateway-server-webflux`에 맞춤. |
| `services/proxy-service/src/main/java/.../JacksonConfiguration.java` | `JavaTimeModule` 등록, 날짜 타임스탬프 직렬화 비활성, `@Primary` ObjectMapper. |
| `services/proxy-service/build.gradle` | `jackson-datatype-jsr310` 의존성 추가. |

**이미지 반영 시 유의:** `docker-compose.yml`이 `COPY build/libs/app.jar` 방식이면, **소스만 고치고 이미지를 재빌드해도 예전 JAR이 캐시될 수 있으므로**, 반드시 **`./gradlew bootJar` 후 `docker compose build`(필요 시 `--no-cache`)** 를 권장한다.

---

## 5. 성공 / 실패 / 부분 성공 정리

| 항목 | 판정 | 설명 |
|------|------|------|
| Compose 인프라·게이트웨이·프록시 기동 | 성공 | `docker compose ps` 및 헬스 체크로 확인. |
| 게이트웨이 → 프록시 라우팅 | 성공(수정 후) | `server.webflux.routes` 적용 및 JAR 재빌드·이미지 재생성 후, 요청이 `/proxy/...`로 전달됨. |
| 프록시에서 사용량 이벤트 JSON 발행 | 성공(수정 후) | JSR-310 직렬화 오류 제거. |
| 문서 §2 경로 + 현재 `.env` 키로 OpenAI 200 응답 | 실패 | OpenAI 경로에 Google 키 사용으로 **401 invalid_api_key**. |
| `usage-service` Rabbit→DB (통합 테스트) | 성공 | `UsageRecordedEventPipelineIntegrationTest` 통과. |
| 문서 §5 Compose DB에서 직접 SELECT로 E2E 확인 | 미완 | 이번 세션에서 성공 200 호출 후까지는 가지 않음. |

---

## 6. 최종 테스트 결과(세션 종료 시점)

1. **아키텍처/설정 관점**  
   - 게이트웨이 라우트가 Spring Cloud Gateway 5 요구에 맞게 동작하도록 수정·재배포한 뒤, **게이트웨이 경유 호출이 프록시까지 도달**함을 확인했다.  
   - 프록시는 **`UsageRecordedEvent` 직렬화**가 가능하도록 수정한 뒤, **발행 단계의 Jackson 오류는 해소**된 상태다.

2. **업스트림·키 관점**  
   - 문서 §2는 **OpenAI** Chat Completions를 가정한다. 로컬 `PROXY_OPENAI_TEST_API_KEY`에 **Google 키만** 두면 **401**이 나와 **토큰 usage 추출·Rabbit 발행**까지 가지 못한다.  
   - **Gemini(구글) 키**를 쓰려면 **구글 프로바이더용 URL·요청 형식**으로 맞추는 것이 별도 필요하다(세부는 [`contracts/gateway-proxy.md`](contracts/gateway-proxy.md) 및 프록시 라우팅 규칙 참고).

3. **usage-service·DB 관점**  
   - **통합 테스트**로 “프록시가 보내는 것과 같은 JSON이 Rabbit에 들어오면 **DB에 저장된다**”는 것은 **성공**으로 확인했다.  
   - **Compose PostgreSQL에 직접 `usage_recorded_log`를 조회하는 전체 E2E**는, 이번 세션에서는 **OpenAI 성공 응답까지 연결되지 않아** 수행하지 않았다.

---

## 7. 후속으로 하면 좋은 확인(참고)

- 문서 §2대로 **OpenAI `sk-...` 키**를 `.env`에 두고 `proxy-service` 재생성 후, 호스트에서 **`usage-service` `bootRun`**, 그다음 §5 `psql` 조회.
- 또는 **구글 API**를 쓸 경우, 게이트웨이·프록시가 지원하는 **google 세그먼트 경로**와 요청 스펙에 맞춰 호출.
- PowerShell에서 `curl` JSON 본문을 쓸 때는 **`--data-binary "@파일"`** 방식이 안전하다.

---

## 8. 관련 문서

- 로컬 절차(정본): [`local-run-and-usage-verification.md`](local-run-and-usage-verification.md)
- 이번 세션 보고(본 문서): `usage-verification-test-session-report.md`
