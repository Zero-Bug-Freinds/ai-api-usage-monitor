# 사용량 검증 테스트 세션 보고서 2

버전: 1.0  
작성 기준: 로컬에서 **`http://localhost:8092/actuator/health`** 확인 시 **`curl` 응답 코드가 `000`으로 나오는 문제**와 **`usage-service`를 호스트에서 `bootRun`할 때 Gradle이 오랫동안 `88% EXECUTING`에 머무는 현상**을 출발점으로, 이후 진행된 **usage-service HTTP 기동·프록시(Google)·RabbitMQ·DB·통합 테스트·문서** 관련 작업을 정리한 문서입니다.

선행 세션: [`usage-verification-test-session-report.md`](usage-verification-test-session-report.md) (게이트웨이 라우트·프록시 Jackson 등).  
절차 정본: [`local-run-and-usage-verification.md`](local-run-and-usage-verification.md).

---

## 1. 이번 세션의 시작 조건

| 항목 | 내용 |
|------|------|
| **증상 1** | `curl.exe -s -o NUL -w "%{http_code}" http://localhost:8092/actuator/health` 실행 시 **`000`** 응답. `curl`에서 `000`은 일반적으로 **연결 실패·응답 없음**(해당 포트에 HTTP 서버가 없거나 연결 거부)을 의미한다. |
| **증상 2** | 새 터미널(예: 3번)에서 **`usage-service`를 호스트에서 기동**(`gradlew bootRun` 등)하는 과정에서 Gradle이 **오랫동안 `88% EXECUTING`** 상태로 보였다. |

---

## 2. 단계별 수행 내용·결과·실패 및 해결

---

### 2.1 `8092` 헬스 체크가 `000`인 원인 조사

**한 일**

- `usage-service`가 `localhost:8092`에서 HTTP를 제공하는지, 설정상 포트(`application.yml`의 `server.port`, 기본 `8092`)와 일치하는지 확인했다.
- 의존성을 검토해, **액추에이터만 있고 서블릿 웹 스택이 없어** 임베디드 웹 서버(Tomcat 등)가 뜨지 않는 구성이었는지 판단했다.

**결과**

- **원인:** `spring-boot-starter-web`이 없으면, **포트에 바인딩되는 HTTP 서버가 없어** `curl`이 `000`을 반환할 수 있다. (액추에이터·AMQP·JPA만으로는 이 프로젝트 구성에서 기대한 `8092` HTTP 헬스가 없었다.)

**실패 / 성공**

- **처음 시도(헬스 호출):** **실패** — HTTP 코드 `000`.
- **원인 분석:** **성공** — 위와 같이 정리.

---

### 2.2 `usage-service`에 `spring-boot-starter-web` 추가

**한 일**

- `services/usage-service/build.gradle`의 `dependencies`에 **`implementation 'org.springframework.boot:spring-boot-starter-web'`** 를 추가했다.

**결과**

- **성공.** Spring Boot가 **임베디드 Tomcat**을 기동하고, `server.port`(기본 `8092`)에서 HTTP를 받는다.

**참고 (파일)**

- `services/usage-service/build.gradle` — `spring-boot-starter-web` 포함.

---

### 2.3 Gradle `bootRun`이 `88% EXECUTING`에 오래 머무는 현상

**한 일**

- 호스트에서 `services/usage-service`에서 **`bootRun`** 을 실행(새 터미널에서 진행)했다.

**결과**

- Gradle이 **`EXECUTING` 단계(예: 약 88% 근처)** 에서 **한동안 멈춘 것처럼 보일 수 있다.** 이는 **의존성 해석·Spring 컨텍스트 기동·DB/Rabbit 연결** 등으로 시간이 걸리는 일반적인 현상이다.
- **애플리케이션이 완전히 기동되면** 로그에 Spring Boot 시작 완료 메시지가 나온다.

**실패 / 성공**

- **“멈춘 것처럼 보임”:** **성격상 정상 범위**일 수 있음(단, DB/Rabbit 미기동 시 연결 재시도로 더 길어질 수 있음).
- **기동 완료 후:** **성공** — 아래 2.4에서 헬스로 확인.

---

### 2.4 헬스 재확인 (`8092`)

**한 일**

- `usage-service` 재기동 후 `GET http://localhost:8092/actuator/health` 를 다시 호출했다(예: `curl` 또는 브라우저).

**결과**

- **성공.** HTTP **`200`** (애플리케이션 상태 UP).

**실패 / 성공**

- **이전:** `000` → **실패**.
- **수정 후:** `200` → **성공**.

---

### 2.5 프록시 Docker 이미지와 `mock-key-google` 불일치 (Google 경로)

**한 일**

- 게이트웨이·프록시를 Compose로 띄운 상태에서 **Google(Gemini) 경로**로 호출을 시도했다.
- 프록시가 **키 서비스**(`host.docker.internal:8080` 등)의 내부 API를 호출하는지, **mock 키**로 우회해야 하는지 확인했다.

**결과**

- **실패(401 등).** **Docker로 빌드된 `proxy-service` 이미지**가 **오래된 `app.jar`** 를 포함하고 있었고, 그 JAR의 `application.yml`에는 **`mock-key-google`** 등 최신 mock 키 설정이 반영되지 않았다. 그 결과 Google 경로에서 **mock이 아닌 키 서비스**로 가 **401**이 나는 상황이 재현되었다.

**해결**

- `services/proxy-service`에서 **`./gradlew bootJar`** 로 최신 JAR을 만든 뒤,
- 저장소 루트에서 **`docker compose build --no-cache proxy-service`** 및 **`docker compose up -d --force-recreate proxy-service`** 로 **이미지·컨테이너를 갱신**했다.

**실패 / 성공**

- **갱신 전:** **실패** — 키·mock 불일치로 401.
- **JAR 재빌드 + 이미지 재빌드 후:** **성공** — 구성이 최신 `application.yml`과 일치(이후 Google 업스트림까지 요청이 도달).

---

### 2.6 Google 업스트림 호출 — HTTP 429 (쿼터)

**한 일**

- Google Generative Language API로 실제 호출(예: `generateContent` 형식의 본문)을 보냈다.

**결과**

- **성공(경로·키 관점).** 프록시가 **Google까지 도달**했음을 확인.
- **실패(업스트림 비즈니스).** 응답 **HTTP 429**, 본문에 **`RESOURCE_EXHAUSTED`** 등 **쿼터/한도** 관련 메시지가 포함된 경우가 있었다. 이는 **애플리케이션 버그가 아니라 Google 계정·프로젝트·빌링/쿼터 설정** 문제로 해석된다.

**실패 / 성공**

- **E2E “200 응답”:** **실패** (429).
- **“프록시→Google 연결·키 사용” 검증:** **부분 성공** — 429는 **연결이 되었다는 증거**로 볼 수 있음.

---

### 2.7 RabbitMQ 큐·컨슈머 확인

**한 일**

- RabbitMQ 컨테이너에 접속해 **`rabbitmqctl list_queues`** 등으로 **`usage-service.queue`** 와 **consumer 수**를 확인했다.

**결과**

- **성공.** 큐에 **컨슈머(예: 1)** 가 붙어 있음을 확인했다.
- **참고:** 호스트에서 **`usage-service`를 중복 기동**하면 잠시 **컨슈머가 2**로 보일 수 있으며, 중복 인스턴스를 종료하면 **1**로 돌아온다.

---

### 2.8 PostgreSQL `usage_recorded_log` 확인

**한 일**

- Compose의 PostgreSQL에 접속해 **`usage_recorded_log`** 를 조회했다(문서 §5 절차와 유사).

**결과**

- **성공.** 행이 존재했고, **`provider = GOOGLE`** 등 기대한 구분자가 보였다. (업스트림이 에러 응답이어도 이벤트 발행·저장 로직에 따라 토큰 필드가 비어 있을 수 있음.)

---

### 2.9 `usage-service` 통합 테스트 재실행

**한 일**

- `services/usage-service`에서 아래만 실행했다.  
  - `UsageRecordedEventPipelineIntegrationTest` (Testcontainers: RabbitMQ·PostgreSQL)

```bash
cd services/usage-service
./gradlew test --tests UsageRecordedEventPipelineIntegrationTest
```

(Windows: `gradlew.bat`.)

**결과**

- **성공.** Gradle **종료 코드 `0`** (테스트 통과로 간주).
- **로그에 WARN:** 테스트 종료 직후 **Hikari 연결 종료**, **Rabbit 연결 EOF** 등 **셧다운 과정**에서 경고가 나올 수 있다. 이는 **테스트 본문 실패**와 동일하게 보지 않았다.

**실패 / 성공**

- **테스트 판정:** **성공** (exit 0).
- **WARN 로그:** **성공 판정에 반하지 않음** (종료 시점 부수 현상).

---

### 2.10 문서·예제 보강 (수동 이벤트 발행·키 재배정)

**한 일**

- 실제 AI 호출 없이 **RabbitMQ에 `UsageRecordedEvent` 형식 JSON**을 넣어 DB까지 확인하는 절차를 [`local-run-and-usage-verification.md`](local-run-and-usage-verification.md) **§2.3, §2.4**에 추가했다.
- 샘플 페이로드 파일을 [`examples/usage-recorded-event-test.json`](examples/usage-recorded-event-test.json) 에 두었다.

**결과**

- **성공.** 팀이 **재현 가능한 절차**로 파이프라인만 검증할 수 있다.

---

## 3. 코드·설정 변경 요약 (이번 세션)

| 위치 | 변경 요약 |
|------|-----------|
| `services/usage-service/build.gradle` | `spring-boot-starter-web` 추가 — `8092`에서 HTTP·액추에이터 헬스 제공. |
| `services/proxy-service` | (이미지 재빌드 절차) 소스·`application.yml` 변경 시 **`bootJar` + `docker compose build`(필요 시 `--no-cache`)** 로 컨테이너에 반영. |
| `docs/local-run-and-usage-verification.md` | §2.3 새 키 재배정, §2.4 실제 AI 호출 없이 이벤트·DB 검증. |
| `docs/examples/usage-recorded-event-test.json` | 수동 발행용 샘플 JSON. |
| `services/usage-service/Dockerfile` | (선택) Compose에 포함할 때를 위한 이미지 빌드 정의. |

---

## 4. 최종 성공·실패 정리

| 항목 | 판정 | 설명 |
|------|------|------|
| `8092` 헬스 (`curl` HTTP 코드) | **성공(수정 후)** | `spring-boot-starter-web` 추가 후 **200**. 이전 **`000`** 은 미기동·무응답. |
| `bootRun` `88% EXECUTING` | **성격상 정상 가능** | 기동·연결에 시간 소요; 완료 후 헬스로 검증. |
| 프록시 Google mock + 최신 JAR | **성공(재빌드 후)** | 오래된 이미지는 **401** 등; **JAR 재빌드 + 이미지 재생성**으로 해결. |
| Google 실제 200 응답 | **실패(세션 중)** | **429 쿼터** 등으로 200 미도달 가능. |
| `usage_recorded_log` (Compose DB) | **성공** | GOOGLE 등 행 확인. |
| `UsageRecordedEventPipelineIntegrationTest` | **성공** | Gradle exit 0. |
| 문서·예제 보강 | **성공** | 수동 발행·키 재배정 절차 문서화. |

---

## 5. 이번 세션에서 얻은 운영 메모

1. **`proxy-service`를 Docker로 돌릴 때**는 소스만 수정하고 **JAR을 다시 빌드하지 않으면** 컨테이너 안 설정이 옛날 그대로일 수 있다. **항상 `bootJar` 후 이미지 재빌드**를 습관화한다.
2. **Google 키**는 경로가 **Gemini REST**와 맞아야 하며, **OpenAI 전용 URL**과 섞지 않는다(선행 보고서 3.9와 동일 원칙).
3. **429**는 **배선 문제가 아닐 때**가 많으므로, 콘솔에서 **쿼터·빌링·모델**을 확인한다.
4. **통합 테스트**는 **Compose DB가 아닌** Testcontainers DB를 쓰므로, “로컬 Docker로 **같은 DB 파일**에 쌓였는지”와는 별개다. Compose DB를 보려면 **호스트 `usage-service` + Compose Rabbit/Postgres** 조합이 필요하다.

---

## 6. 관련 문서

- 로컬 절차(정본): [`local-run-and-usage-verification.md`](local-run-and-usage-verification.md)
- 선행 세션 보고: [`usage-verification-test-session-report.md`](usage-verification-test-session-report.md)
- 본 세션 보고: `usage-verification-test-session-report2.md` (이 문서)
