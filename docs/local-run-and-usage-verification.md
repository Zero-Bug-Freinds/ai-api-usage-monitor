# 로컬 실행·테스트 — Gateway / Proxy / usage-service (사용량 이벤트까지)

버전: 1.0  

OpenAI 등 API 키를 두고 **게이트웨이 → 프록시 → Provider** 호출 시 **`UsageRecordedEvent`가 RabbitMQ로 발행**되고 **`usage-service`가 DB에 저장**하는 흐름을 로컬에서 재현·검증하는 절차입니다.  
관련 설계 문서: [`contracts/gateway-proxy.md`](contracts/gateway-proxy.md), [`event-consumer-flow.md`](event-consumer-flow.md), [`usage-analytics-relationship.md`](usage-analytics-relationship.md) §3.1(패턴 A).

---

## 전제

- **인증·로그인 서비스는 사용하지 않는다**고 가정(캡스톤/개발 모드). Gateway는 `GATEWAY_DEV_MODE=true`(기본에 가깝게)일 때 **`X-User-Id` 헤더**만으로 `/api/v1/ai/**` 진입 가능.
- **Google(Gemini)만** 테스트하려면 **`PROXY_GOOGLE_TEST_API_KEY`** 와 아래 §2.1의 **Google 경로**만 맞추면 된다. OpenAI를 쓰려면 **`PROXY_OPENAI_TEST_API_KEY`** 와 §2.2 경로가 필요하다. 두 키를 동시에 둘 수 있으며, **어느 쪽을 “기본”으로 강제하지는 않는다.** 선택적으로 **`PROXY_MOCK_KEY_FALLBACK`** 을 둘 수 있다. 키는 **커밋하지 말고** `.env` 등 로컬에만 둔다(`.env.example` 참고). 상세: [`multi-provider-usage-and-google-path.md`](multi-provider-usage-and-google-path.md).

---

## 1. 인프라·앱 기동 순서

### 1.1 Docker Compose (PostgreSQL, RabbitMQ, Redis, 선택: proxy·gateway)

저장소 루트에서:

```bash
docker compose up -d
```

- Postgres: 보통 `localhost:5432`. **identity-service** 는 `POSTGRES_*` 로 **`app`** DB만 사용하고, **usage-service** 는 **`USAGE_POSTGRES_*`** 로 **`usage_db`** 전용 계정만 사용한다(같은 Postgres 인스턴스, DB 두 개). `.env.example` 참고.
- RabbitMQ: AMQP `5672`, 관리 UI `15672`(guest/guest).
- `docker-compose.yml`에 **proxy-service**, **api-gateway-service**가 포함되어 있으면 함께 기동된다.

### 1.2 OpenAI / Google 테스트 키를 proxy에 전달

`proxy-service`는 로컬 mock 모드에서 **`proxy.key-service.mock-key-openai`** ← **`PROXY_OPENAI_TEST_API_KEY`**, **`mock-key-google`** ← **`PROXY_GOOGLE_TEST_API_KEY`** 를 읽는다. 둘 다 비어 있으면 **`proxy.key-service.mock-key`** ← **`PROXY_MOCK_KEY_FALLBACK`**(선택), 그다음 키 서비스 순이다. 키는 **절대 커밋하지 말고**, 저장소 루트의 **`.env`**에만 둔다(`.gitignore`에 포함됨).

**권장(Compose로 proxy 기동):**

1. 저장소 **루트**에서 `.env.example`을 복사해 `.env`를 만든다.  
   - 예: `copy .env.example .env`(cmd), `Copy-Item .env.example .env`(PowerShell).
2. `.env` 안에서 **`PROXY_OPENAI_TEST_API_KEY=`**, 필요 시 **`PROXY_GOOGLE_TEST_API_KEY=`** 등을 채운다.
3. 루트에서 **`docker compose up`**을 실행한다. Compose는 **같은 디렉터리의 `.env`**를 읽어 `docker-compose.yml`의 **`proxy-service`** `environment`로 컨테이너에 전달한다.

**프록시를 호스트에서** `gradlew bootRun`만 하는 경우: 루트 `.env`는 Spring Boot/Gradle이 자동으로 읽지 않으므로, IDE 실행 구성이나 셸에서 동일 이름으로 설정한다(예: PowerShell `$env:PROXY_OPENAI_TEST_API_KEY = "sk-..."`).

### 1.3 usage-service (호스트에서 실행)

Compose의 Postgres·Rabbit과 **같은 호스트·포트**로 붙인다. DB는 **`usage_db`** 및 **`USAGE_POSTGRES_USER` / `USAGE_POSTGRES_PASSWORD`** (`.env`와 `application.yml` 기본값과 맞출 것).

```bash
cd services/usage-service
# Windows PowerShell 예시
$env:POSTGRES_HOST = "localhost"
$env:SPRING_RABBITMQ_HOST = "localhost"   # application.yml 기본과 동일하면 생략 가능
$env:USAGE_POSTGRES_DB = "usage_db"
$env:USAGE_POSTGRES_USER = "usage_app"
$env:USAGE_POSTGRES_PASSWORD = "usage_app"
.\gradlew.bat bootRun
```

- 기본 HTTP 포트: **`8092`** (`USAGE_SERVICE_PORT`로 변경 가능).
- 상세 설정: `services/usage-service/src/main/resources/application.yml`.

**기존 `postgres_data` 볼륨만 쓰는 경우:** `usage_db`가 아직 없으면 Compose 첫 기동 시 돌아가는 `docker/postgres/init/02-create-usage-db.sh`는 **실행되지 않는다**(init 스크립트는 데이터 디렉터리가 비어 있을 때만). 아래 §5.1을 수동 실행한다.

### 1.4 실행 순서 요약

1. `docker compose up -d` (DB·큐·필요 시 gateway·proxy)
2. (호스트에서) **`usage-service` `bootRun`**
3. Gateway·Proxy가 이미 Compose에 있지 않다면 각각 `services/`에서 `bootRun` (Gateway `8080`, Proxy `8081`).

---

## 2. AI 호출 (Gateway 경로)

개발 모드에서는 **JWT 없이** `X-User-Id`만 보낸다.  
프로바이더는 URL의 **`/api/v1/ai/{openai|google|…}/…`** 로 구분한다. **Google 키만** 쓸 때는 **`google`** 세그먼트와 **Gemini REST** 본문을 쓴다( OpenAI `chat/completions` JSON과 호환되지 않는다).

### 2.1 Google (Gemini) — `PROXY_GOOGLE_TEST_API_KEY` 로 테스트

업스트림은 `generativelanguage.googleapis.com`이며, 게이트웨이에서는 **`/api/v1/ai/google/`** 뒤에 공식 API의 **경로 접미사**를 붙인다.

```http
POST http://localhost:8080/api/v1/ai/google/v1beta/models/gemini-2.0-flash:generateContent
Content-Type: application/json
X-User-Id: test-user-1

{
  "contents": [
    {
      "parts": [
        { "text": "hello" }
      ]
    }
  ]
}
```

- 모델 ID(`gemini-2.0-flash` 등)는 **Google AI Studio / Generative Language API 문서**에 맞게 바꿀 수 있다.
- 응답이 200이면, 프록시는 응답의 `usageMetadata` 등에서 usage를 파싱해 이벤트를 발행한다.

### 2.2 OpenAI — `PROXY_OPENAI_TEST_API_KEY` 로 테스트

```http
POST http://localhost:8080/api/v1/ai/openai/v1/chat/completions
Content-Type: application/json
X-User-Id: test-user-1

{
  "model": "gpt-4o-mini",
  "messages": [{ "role": "user", "content": "hello" }]
}
```

- 응답이 200이고 본문이 오면, 프록시는 upstream 호출 후 응답에서 usage를 파싱해 이벤트를 발행한다.

### 2.3 새 유효 키를 받아 다시 배정한 경우

**가능합니다.** `.env`에 새 키를 넣은 뒤, Compose로 proxy를 쓰는 경우 **`docker compose up -d --force-recreate proxy-service`** 로 컨테이너에 환경 변수를 다시 주입합니다. `proxy-service`의 `application.yml`이나 Java를 바꾼 경우에는 **`bootJar` 후 Docker 이미지 재빌드**까지 해야, 컨테이너 실행이 소스와 일치합니다(이전에 오래된 JAR만 패키징된 이미지로 mock 키가 반영되지 않는 문제가 있었음).

### 2.4 실제 AI 호출 없이 이벤트·DB 흐름만 검증

프록시가 Provider를 호출하지 않아도, **RabbitMQ에 `UsageRecordedEvent`와 동일한 JSON**이 `usage.events` / `usage.recorded`로 들어가면 `usage-service`가 소비해 **`usage_recorded_log`** 에 저장합니다. 테스트용으로 `correlationId`·`userId`에 `TEST-` 접두어 등을 넣어 구분하면 됩니다.

**(A) 통합 테스트 (권장)** — `UsageRecordedEventPipelineIntegrationTest`가 Testcontainers(Rabbit·Postgres)로 발행→소비→저장까지 검증합니다. Docker가 필요합니다.

```bash
cd services/usage-service
./gradlew test --tests UsageRecordedEventPipelineIntegrationTest
```

**(B) 로컬 Compose Rabbit + 호스트 `usage-service` + Management API 수동 발행**

**전제:** `docker compose up -d`로 Postgres·Rabbit이 떠 있고, 호스트에서 **`usage-service`가 `bootRun`** 중이어야 합니다(`usage.events` exchange·큐는 usage-service가 기동 시 선언).

1. 샘플: [`examples/usage-recorded-event-test.json`](examples/usage-recorded-event-test.json) 을 연다. **재실행마다 `eventId`를 새 UUID로 바꾼다**(같은 `eventId`는 멱등 처리로 스킵됨).
2. 아래 PowerShell로 발행한다(기본 guest/guest).

```powershell
$payload = Get-Content -Raw "docs/examples/usage-recorded-event-test.json"
$body = @{ properties = @{}; routing_key = "usage.recorded"; payload = $payload; payload_encoding = "string" } | ConvertTo-Json -Depth 10
$pair = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("guest:guest"))
Invoke-RestMethod -Uri "http://localhost:15672/api/exchanges/%2F/usage.events/publish" -Method Post -Headers @{ Authorization = "Basic $pair" } -ContentType "application/json; charset=utf-8" -Body $body
```

3. §5의 `psql`로 `correlation_id` 또는 `user_id`가 `TEST-` 샘플과 맞는 행이 생겼는지 확인한다.

---

## 3. 로그로 확인하기

### 3.1 proxy-service

현재 구현에서는 **usage 발행 성공 전용 `INFO` 로그가 거의 없다.** 실패는 upstream/API 키에서 드러난다.

### 3.2 usage-service

성공 시 **`DEBUG`** 레벨 로그만 남는다:

- `Stored usage event eventId=… userId=…`
- 중복 `eventId`: `Skipping duplicate usage event …`

실패 시 **`ERROR`**:

- `Failed to deserialize or persist UsageRecordedEvent`

`application.yml` 또는 환경 변수로 로그 레벨을 올린다:

```yaml
logging:
  level:
    com.eevee.usageservice: DEBUG
```

(Spring Boot 속성: `logging.level.com.eevee.usageservice=DEBUG`)

---

## 4. RabbitMQ 관리 UI로 확인

1. 브라우저: `http://localhost:15672` (guest/guest).
2. Exchange **`usage.events`**, 라우팅 키 **`usage.recorded`**, 큐 **`usage-service.queue`** 를 확인한다.
3. AI 호출 전후 **message rates / ready messages** 변화를 본다(소비가 빠르면 큐에 안 쌓일 수 있음).

---

## 5. PostgreSQL로 최종 확인 (권장)

`usage-service` 테이블 **`usage_recorded_log`** 는 **`usage_db`** 안에 있다. 소비·저장까지 성공하면 아래 조회에 행이 보인다.

```bash
docker compose exec postgres psql -U usage_app -d usage_db -c "SELECT event_id, user_id, provider, model, prompt_tokens, completion_tokens, total_tokens, occurred_at, persisted_at FROM usage_recorded_log ORDER BY persisted_at DESC LIMIT 10;"
```

(`USAGE_POSTGRES_USER` / `USAGE_POSTGRES_DB` 는 팀 `.env`에 맞게 조정. `identity-service`용 **`app`** DB와는 별도.)

### 5.1 기존 볼륨에서 `usage_db` 수동 생성

이미 `postgres_data`가 있는데 **`usage_db`가 없다면** (또는 팀원이 볼륨을 초기화하지 않은 경우), 슈퍼유저(`POSTGRES_USER`, 보통 `app`)로 한 번 실행한다(비밀번호는 `.env`의 `USAGE_POSTGRES_PASSWORD`와 맞출 것):

```bash
docker compose exec postgres psql -U app -d postgres -c "CREATE USER usage_app WITH PASSWORD 'usage_app';"
docker compose exec postgres psql -U app -d postgres -c "CREATE DATABASE usage_db OWNER usage_app;"
```

이미 사용자·DB가 있으면 오류가 날 수 있다. 그 경우 생략하거나 DBA에 맡긴다.

---

## 6. 자동 테스트 (코드 검증)

저장소에 **Wire 형식·멱등·Testcontainers 통합** 테스트가 있다:

```bash
cd services/usage-service
./gradlew test
# Windows: gradlew.bat test
```

- **통합 테스트**(`UsageRecordedEventPipelineIntegrationTest`)는 **Docker(Testcontainers)** 가 필요하다.

---

## 7. 자주 나는 문제

| 증상 | 점검 |
|------|------|
| Proxy 401/키 오류 | OpenAI 경로: `PROXY_OPENAI_TEST_API_KEY` 미설정 또는 Compose 미전달. Google 경로: `PROXY_GOOGLE_TEST_API_KEY` 확인 |
| usage-service DB 연결 실패 | `POSTGRES_HOST`·포트·`USAGE_POSTGRES_DB` / `USAGE_POSTGRES_USER` / `USAGE_POSTGRES_PASSWORD` 가 Compose·`usage_db` 존재 여부와 일치하는지 |
| usage-service Rabbit 연결 실패 | `localhost:5672`, guest 계정, Rabbit 기동 여부 |
| Gateway 401 | 개발 모드가 꺼져 있으면 JWT 필요. `GATEWAY_DEV_MODE`·`X-User-Id` 확인 |
| 로그에 저장 흔적 없음 | usage-service에 `com.eevee.usageservice` **DEBUG** 활성화 |

---

## 8. 관련 문서

- Gateway ↔ Proxy 경로·헤더: [`contracts/gateway-proxy.md`](contracts/gateway-proxy.md)
- 이벤트 팬아웃: [`event-consumer-flow.md`](event-consumer-flow.md)
- usage / analytics 역할: [`usage-analytics-relationship.md`](usage-analytics-relationship.md)
