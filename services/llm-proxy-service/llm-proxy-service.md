# llm-proxy-service 작업 정리

## 1) 이번에 반영한 내용

### 서비스 초기 골격 생성
- 경로: `services/llm-proxy-service/`
- 생성 파일:
  - `services/llm-proxy-service/build.gradle`
  - `services/llm-proxy-service/settings.gradle`
  - `services/llm-proxy-service/src/main/resources/application.yml`
  - `services/llm-proxy-service/src/main/java/com/eevee/llmproxyservice/LlmProxyServiceApplication.java`
  - `services/llm-proxy-service/src/main/java/com/eevee/llmproxyservice/config/WebClientConfig.java`

### 빌드/런타임 스택
- Spring Boot: `3.3.5`
- Java: `21` (toolchain)
- 의존성:
  - Spring Web
  - Spring WebFlux
  - Spring Data JPA
  - MySQL Driver (`com.mysql:mysql-connector-j`)
  - Lombok

### DB 로그 적재 뼈대
- 엔티티:
  - `services/llm-proxy-service/src/main/java/com/eevee/llmproxyservice/domain/UsageLog.java`
  - 테이블명: `usage_logs`
  - 필드: `id`, `apiKey`, `modelName`, `promptTokens`, `completionTokens`, `totalTokens`, `createdAt`
- 리포지토리:
  - `services/llm-proxy-service/src/main/java/com/eevee/llmproxyservice/repository/UsageLogRepository.java`
- JPA Auditing:
  - `services/llm-proxy-service/src/main/java/com/eevee/llmproxyservice/config/JpaAuditingConfig.java`
  - `@EnableJpaAuditing` 활성화, `createdAt`는 `@CreatedDate` 사용

### 전략 패턴 기반 Provider 구조 추가
- 공통 인터페이스:
  - `services/llm-proxy-service/src/main/java/com/eevee/llmproxyservice/service/LlmProviderService.java`
  - 메서드: `forwardAndLog(String payload, String apiKey, String uriPath)`
- Gemini 구현체:
  - `services/llm-proxy-service/src/main/java/com/eevee/llmproxyservice/service/GeminiProviderImpl.java`
  - 호출 URL: `https://generativelanguage.googleapis.com + uriPath`
  - 헤더: `x-goog-api-key`
  - 응답 파싱: `usageMetadata.promptTokenCount`, `candidatesTokenCount`, `totalTokenCount`
  - `UsageLog` 저장
- OpenAI 구현체:
  - `services/llm-proxy-service/src/main/java/com/eevee/llmproxyservice/service/OpenAiProviderImpl.java`
  - 호출 URL: `https://api.openai.com + uriPath`
  - 헤더: `Authorization: Bearer <OPENAI_API_KEY>`
  - 응답 파싱: `usage.prompt_tokens`, `completion_tokens`, `total_tokens`
  - `UsageLog` 저장

### 프록시 분기 컨트롤러 추가
- 파일: `services/llm-proxy-service/src/main/java/com/eevee/llmproxyservice/controller/ProxyController.java`
- 엔드포인트: `POST /proxy/**`
- 분기 규칙:
  - 경로에 `openai` 또는 `chat/completions` 포함 -> `OpenAiProviderImpl`
  - 경로에 `gemini` 또는 `models` 포함 -> `GeminiProviderImpl`
  - 그 외 -> `400 Bad Request`
- 클라이언트 식별 헤더: `X-Client-Api-Key` (없으면 `anonymous`)

## 2) 현재 application.yml 상태

- 파일: `services/llm-proxy-service/src/main/resources/application.yml`
- 주요 값:
  - `spring.datasource.url: jdbc:mysql://localhost:3307/proxy_db`
  - `spring.datasource.username: root`
  - `spring.datasource.password: root`
  - `spring.jpa.hibernate.ddl-auto: update`
  - `spring.jpa.show-sql: true`
  - `google.gemini.api-key: ${API_KEY:}`
  - `openai.api-key: ${OPENAI_API_KEY:}`

## 3) 이후 정리(삭제)된 항목

요청에 따라 루트 인프라/스크립트에서 llm-proxy 관련 항목을 제거함.

- `docker-compose.yml`
  - `llm-proxy-db` 서비스 삭제
  - `llm_proxy_mysql_data` 볼륨 삭제
- `scripts/bootrun.ps1`
  - `llm-proxy-service` 실행 대상 목록 제거
  - `LLM_PROXY_SERVICE_PORT` 기본값 분기 제거

## 4) 참고 메모

- 현재 `llm-proxy-service` 소스 코드는 유지되어 있음.
- 루트 `docker-compose.yml`/`bootrun.ps1`에서는 llm-proxy 전용 기동 구성이 제거된 상태임.
- 필요 시 다음 작업:
  - 루트 실행 스크립트/Compose에 llm-proxy 재등록
  - provider별 요청/응답 DTO 및 예외 처리 표준화
  - provider 분기 규칙(문자열 contains) 개선
