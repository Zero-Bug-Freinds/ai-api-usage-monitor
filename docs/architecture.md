# <Team Project> AI Usage & Billing Platform (MSA) 아키텍처 문서
버전: 0.4

---

## 0. 관련 문서

- **MSA 이론 배경**(일반 개념·특징·API Gateway 패턴): [`docs/msa-architecture-theory.md`](msa-architecture-theory.md)
  - 본 문서(`architecture.md`)는 **이 팀 프로젝트의 구조·스택·서비스 분해**를 다룬다. 위 이론 문서는 구현과 무관한 **일반 설명**을 위한 참고 자료이다.
- **이벤트 소비 흐름**(proxy 발행 `UsageRecordedEvent` → usage·analytics·billing·alarm 등): [`docs/event-consumer-flow.md`](event-consumer-flow.md)
- **usage ↔ analytics 관계**(이벤트 팬아웃, REST 조회, 하이브리드, DB 경계): [`docs/usage-analytics-relationship.md`](usage-analytics-relationship.md)

---

## 1. 목적과 범위

### 1.1 문제 정의
- 사용자는 OpenAI / Gemini / Claude 같은 AI Provider를 직접 호출할 때, 조직/팀/개인 단위 사용량과 비용을 “한 곳에서” 투명하게 관리하기 어렵다.
- 비용 폭증, 팀별 책임 불명확, 개인별 사용량 추적 부재, 사용량 제한(Quota/Budget) 부재가 발생한다.
- 공급사별 usage 구조가 달라 통합 분석이 어렵다.

### 1.2 목표(Goals)
- 조직 내 AI API 사용량을 투명하게 관리한다.
- 팀별 AI 비용을 자동 정산한다.
- 개인 사용자의 AI API 비용을 별도로 추적/분석한다.
- 사용량 폭증을 사전에 감지한다.
- 사용자/팀/조직별 비용 책임을 명확하게 분리한다.

### 1.3 MVP 범위(캡스톤 기준)
- 필수 기능
  - 회원가입/로그인
  - 개인 사용자 모드
  - 조직 생성
  - 팀 생성
  - API Key 등록/별칭 수정/키별 월 예산 설정/삭제 예약(7일 유예)
  - 프록시 서버를 통한 AI API 호출
  - 사용량 기록
  - 비용 계산
  - 개인 대시보드
  - 조직/팀 대시보드
- 선택 기능
  - Quota 설정(soft limit / hard block)
  - Slack/이메일 알림
  - 월별 리포트 생성(고정 주기 집계)

### 1.4 비목표(Non-Goals)
- 공급사별 완벽한 토큰 계산(가능하면 Provider의 usage 값을 신뢰하여 사용)

---

## 2. 핵심 아이디어

- 사용자는 AI Provider API를 직접 호출하지 않고, 본 플랫폼의 **Proxy 엔드포인트**로 호출한다.
- Proxy는 요청/응답을 중간에서 처리하며 **usage(토큰/비용) 정보를 즉시 기록**한다.
- 기록된 usage를 기반으로 Billing, Analytics, Quota, Notification을 비동기 이벤트로 연계한다.

### 2.1 기술 스택(결정)
- **애플리케이션 프레임워크(Proxy Service 등)**: **Spring Boot + Spring WebFlux**
  - 비동기 I/O·Provider HTTP 중계·스트리밍(SSE 등) 응답 처리에 적합하다.
  - **FastAPI(Python)는 사용하지 않는다.**
- **메시지 브로커**: **RabbitMQ** (이벤트 기반 연계의 단일 브로커)
- **기타 마이크로서비스**: 동일 Spring 생태계 내에서 **Spring MVC(Web) + JPA** 등으로 구현할 수 있다(Identity/Billing 등, 팀 합의).
- **프론트엔드(서비스 단위 풀스택)**: 사용자 대면 UI·BFF가 필요한 도메인은 **`services/<svc>/web/`** 의 **Next.js(App Router)**, **React**, **TypeScript**, **Tailwind CSS**, **Shadcn UI**(및 Radix), 차트는 **Recharts** 또는 **Chart.js** 등(팀 합의)으로 구현한다. 라우트·BFF·미들웨어 소유 경계는 `docs/contracts/web-split-boundary.md`. 런타임이 Spring과 달라도 MSA 원칙상 **HTTP API·BFF 계약**으로 연동한다.
- **Analytics·알림·집계 백엔드(백엔드 담당, 팀 합의)**: 집계·알림 워커는 **Spring MVC + JPA**, **Spring Boot + 메시지 소비**, 또는 팀 합의 하에 **Node(NestJS 등)** 로 둘 수 있다. 브로커·캐시는 §6, §10과 동일하게 **RabbitMQ**, **Redis**를 전제로 한다. 상세 책임은 **§12** 참고.

---

## 3. 전체 아키텍처(High-Level)

### 3.1 서비스 흐름(요청 흐름)
1) 개인 사용자 또는 조직 소속 개발자가 플랫폼 프록시 서버로 AI 요청 전송  
2) Proxy Service가 요청을 Provider로 중계  
3) Proxy Service가 usage/비용 정보를 계산하거나 Provider usage를 추출  
4) Usage Tracking Service(또는 usage 저장 로직)가 usage 로그 저장  
5) Billing/Analytics/Quota가 데이터를 사용해 집계 및 제한/알림 판단  
6) 대시보드에서 개인/팀/조직별 비용 및 사용량을 확인  
7) Quota 초과 상태가 되면 Notification(경고/차단)에 반영  
8) 정산 리포트를 생성

### 3.2 MSA 구성(서비스 분리 원칙)
- 각 서비스는 “하나의 기능(도메인)을” 책임지는 경계로 정의한다.
- 너무 잘게 쪼개지지 않되, 너무 비대해지지 않도록 한다.
- 데이터 소유권(Data Ownership)을 서비스 단위로 명확히 한다.

### 3.3 로컬 개발 실행 전략(캡스톤)
- **Kubernetes는 사용하지 않는다.** (배포·클러스터 운영 범위 밖)
- **의존성 인프라(DB·메시지 큐·캐시)** 는 **Docker Compose**로 기동한다.
  - 예: `PostgreSQL`, `RabbitMQ`, `Redis`
- **Proxy Service**는 **로컬(개발 PC)** 에서 실행하는 것을 기본으로 한다.
  - 이유: 프록시는 수정 빈도가 높고 스트리밍/디버깅이 중요하므로, 컨테이너보다 로컬 실행이 생산성에 유리하다.
  - 웹 UI(브라우저) 및 다른 서비스는 `localhost` + 환경변수로 Compose에 띄운 브로커/DB 주소에 연결한다.
- 다른 애플리케이션 서비스(Identity, Usage Tracking 등)도 동일하게 **로컬 실행**을 원칙으로 하며, 팀 합의 하에 통합 테스트용으로 Compose에 포함할 수 있다.

---

## 4. 서비스 분해(권장안)

아래는 캡스톤에서 현실적으로 구현 가능한 권장 분해(9개 내외)이다.

### 4.1 API Gateway Service
- 역할
  - 외부 요청 진입점
  - 인증 토큰 검증, 라우팅, 공통 로깅, rate limiting
- 책임 범위
  - 서비스 간 호출을 표준화하고 외부 경로를 정리한다.
- 입력/출력
  - 사용자 요청을 내부 서비스로 라우팅하고 공통 처리만 수행
- **Gateway와 Proxy의 경로·헤더·신뢰 경계**는 [`docs/contracts/gateway-proxy.md`](contracts/gateway-proxy.md)에 정본으로 둔다(구현: `services/api-gateway-service`, `services/proxy-service`).

### 4.2 Proxy Service
- 구현 스택: **Spring WebFlux** 기반(§2.1 참고).
- 역할
  - Provider(OpenAI/Gemini/Claude)에 대한 프록시 중계
  - 요청/응답 가로채기, 스트리밍 처리, usage 정보 추출
  - usage 관련 이벤트를 **RabbitMQ**로 발행
- 책임 범위(중요)
  - “AI 호출의 실시간 처리와 Provider 연동”의 핵심 서비스
  - quota 강제는 “차단/허용 판단” 관점에서 실시간으로 수행

### 4.3 Identity & Organization Service
- 역할
  - 회원/개인 사용자 모드
  - 조직 생성, 멤버십, 권한(RBAC)
- 책임 범위
  - 사용자-조직-권한 모델을 관리한다.
  - Quote/Key 설정의 “정책 값(설정)”을 제공한다.

### 4.4 API Key Service
- 역할
  - 공급사별 API Key 등록/수정/삭제
  - 암호화 저장 및 키 조회
- 책임 범위
  - Proxy가 요청 직전에 사용할 “자격증명”을 제공한다.
  - 키 원문이 로그/다른 서비스에 남지 않도록 한다.

### 4.5 Usage Tracking Service
- 역할
  - usage 로그 저장(사용자/팀/조직 단위 멀티 테넌시)
- 책임 범위
  - Proxy에서 전달받은 요청/응답 기반 usage를 저장한다.
  - 저장된 usage를 기반으로 Billing/Analytics의 데이터 원천이 된다.

### 4.6 Billing Service
- 역할
  - usage 기반 비용 계산 및 정산 기록 저장
- 책임 범위
  - 개인/팀/조직 단위 billing_record 생성 및 조회를 담당
  - 정산 금액의 “최종 진실”을 관리한다.

### 4.7 Analytics & Reporting Service
- 역할
  - 대시보드용 집계 데이터 생성
  - 월별/주별 리포트 생성(선택)
- 책임 범위
  - 사용량/비용 추세, 모델/팀/사용자 분포 등 시각화 데이터를 준비한다.
  - 보고서는 배치/스케줄러 기반으로 생성 가능하다.

### 4.8 Quota Service
- 역할
  - 예산 및 사용량 제한 정책 관리
  - 현재 예산 상태 기준으로 soft/hard 정책을 적용하기 위한 판단 로직 제공
- 책임 범위
  - 정책 값(한도, soft/hard, 기간)을 관리하고
  - Proxy의 차단 판단 또는 알림 트리거에 필요한 기준을 제공한다.

### 4.9 Notification Service
- 역할
  - Slack/Email/앱 알림 발송
- 책임 범위
  - Quota/비용 임계치 도달 이벤트를 받아 “알림만” 수행한다.
  - 알림 중복 방지 및 발송 이력 관리(최소 구현 포함)

### 4.10 Team Service
- 역할
  - 팀 생성/조회
  - 팀 멤버(아이디 기반) 초대
- 책임 범위
  - 팀 도메인의 데이터 소유권을 분리해 관리한다.
  - 팀 멤버십 기준 권한(예: 팀 멤버만 초대 가능)을 서버에서 강제한다.

---

## 5. 데이터 모델(개념 수준)

### 5.1 Usage Log(개념)
- `usage_log`
  - `log_id`
  - `user_id`
  - `organization_id`
  - `team_id`
  - `provider`
  - `model`
  - `prompt_tokens`
  - `completion_tokens`
  - `total_tokens`
  - `estimated_cost`
  - `request_path`
  - `timestamp`
- 목적
  - Provider 중립 형태로 통합 사용량/비용을 기록한다.

### 5.2 Billing Record(개념)
- `billing_record`
  - `billing_id`
  - `scope_type` (personal/team/org)
  - `scope_id`
  - `total_cost`
  - `billing_period`
  - `created_at`
- 목적
  - 책임(누가 얼마를 부담하는지) 기준으로 “정산 결과”를 저장한다.

---

## 6. 이벤트 기반 통신(Event-Driven)

### 6.1 기본 이벤트 예시
- `usage-recorded`
  - 발행 주체: Proxy Service
  - 소비 주체: Billing Service, Analytics Service(집계), Quota Service(임계치 판단), Notification Service(트리거)
- `quota-warning` / `quota-exceeded`
  - 발행 주체: Quota Service
  - 소비 주체: Notification Service
- `billing-updated`(선택)
  - 발행 주체: Billing Service
  - 소비 주체: Analytics Service

### 6.2 브로커 및 연동
- **브로커**: **RabbitMQ** (Kafka 등은 본 프로젝트 범위에서 사용하지 않는다).
- **연동**: Spring 생태계에서는 **Spring AMQP**(`RabbitTemplate`, `@RabbitListener` 등)로 발행·구독한다.
- **주의(WebFlux)**: `RabbitTemplate` 등 블로킹 API는 reactive 스레드에서 직접 호출하지 말고, **전용 스케줄러에 오프로드**하거나 Reactor와 호환되는 방식으로 호출해 이벤트 루프를 막지 않도록 한다.

---

## 7. Quota(제한)와 알림 정책

### 7.1 정책 종류
- Soft Limit
  - 경고 알림, 차단은 하지 않거나 제한적으로 적용
- Hard Limit
  - 차단(요청 거부) 또는 즉시 중단

### 7.2 역할 분담(중요)
- Proxy Service(실시간 강제)
  - 요청을 받는 즉시 Redis/캐시된 카운터 또는 빠른 조회 데이터를 기반으로 “허용/거부”를 판단한다.
- Quota Service(정책/설정 소유)
  - 한도, 기간, soft/hard 방식 등 정책 값을 관리한다.
- Notification Service(알림 발송)
  - 임계치 도달에 대한 알림 전송만 수행한다.
  - “알림 계산/중복 방지/발송”에 집중한다.

---

## 8. 보안(Security) 설계 원칙

### 8.1 민감 정보 범주
- 공급사 API Key(강기밀)
- 사용량/비용 데이터(민감 정보로 취급)
- 개인 식별 정보(민감 정보로 취급)

### 8.2 Git/코드 유출 방지
- 키/비밀번호/인증서 원문은 절대 커밋하지 않는다.
- `.env` 및 시크릿 파일을 `.gitignore`로 차단한다.
- pre-commit/CI에서 비밀 스캐닝(gitleaks/trufflehog 등) 도입을 권장한다.
- **`.env.example`:** 키 **이름**과 로컬 전용 **문서화된 플레이스홀더**만 둔다. 실 운영 비밀은 커밋하지 않는다. CI·gitleaks와의 관계는 `docs/CI.md`를 본다.

### 8.3 저장/전송 시 암호화
- API Key는 DB에 평문 저장 금지
- 암호화 저장(AES-256-GCM 등) + 마스터키는 환경변수(또는 로컬 Secret)로 관리
- 복호화는 필요한 서비스 내부에서만, 요청 직전에만 수행한다.

### 8.4 테넌트 경계 강제
- 모든 조회/수정 API는 서버에서 `org_id/team_id/user_id` 스코프를 강제한다.
- 클라이언트가 전달한 org_id를 그대로 신뢰하지 않는다.
- RBAC(Role-Based Access Control) 적용

### 8.5 로그 마스킹
- Authorization 헤더, API Key, 비밀값은 로그에 남기지 않거나 마스킹한다.
- 요청/응답 전문 로깅을 운영/디버그 모드에서 제한한다.

---

## 9. 관측 가능성(Observability)

### 9.1 필수 관측
- 중앙 집중 로그(어느 요청이 실패했는지, 어느 서비스에서 실패했는지 추적)
- 메트릭(에러율, 응답시간, 요청 수)
- 분산 트레이싱(프록시 기반 요청 흐름 추적)

### 9.2 캡스톤 추천 도구
- Prometheus + Grafana
- Loki + Grafana(로그)
- Jaeger/Zipkin(트레이싱, 시간 허용 시)

---

## 10. 최소 인프라 스택(권장)

본 프로젝트는 **배포·Kubernetes 클러스터를 사용하지 않는다**는 전제에서 아래를 따른다.

### 10.1 컨테이너·배포 패턴 (팀 확정)

배포 시 **단일 Docker 이미지 안에 백엔드·프론트를 한꺼번에 넣고 supervisord 등으로 멀티프로세스 기동하는 방식(패턴 A)** 과, **서비스·계층마다 이미지를 나누고 Docker Compose로 스택을 올리는 방식(패턴 B)** 을 검토했으며, **패턴 B로 확정**한다.

| 구분 | 패턴 A (비채택) | 패턴 B (팀 확정) |
|------|----------------|------------------|
| 이미지 | 하나의 이미지에 여러 프로세스(예: API + 웹) | **백엔드 마이크로서비스별·도메인별 `web/`** 로 **이미지 분리** |
| 런타임 | 컨테이너 내부 프로세스 관리(supervisord 등) | **Docker Compose**로 서비스별 컨테이너를 조합 |

- **원칙**: Spring Boot 서비스는 **해당 서비스 디렉터리**의 `Dockerfile`로 빌드하고, Next.js는 **`services/<svc>/web/Dockerfile`**(standalone)로 빌드하되 **build context는 저장소 루트**(루트 `pnpm` workspace·`packages/ui` 포함)를 쓴다. 운영·스테이징에서도 **Compose(또는 동등한 오케스트레이션)로 각 이미지를 나란히 띄우는 모델**을 따른다.
- **로컬**: 루트 `docker-compose.yml`은 인프라·일부 앱(예: proxy·gateway)을 포함할 수 있으며, **웹 컨테이너는 `profile: web`(`identity-web`, `usage-web`, `web-edge`) 등으로 선택 기동**해 호스트 개발과 병행할 수 있다. 호스트에서 Next를 직접 띄울 때는 저장소 루트 **`pnpm install`** 후 **`pnpm --filter identity-web dev`** / **`pnpm --filter usage-web dev`** 등(`packages/ui` workspace 포함 — `README.md`, `docs/repository-structure.md` §6).
- **Compose 환경변수:** `docker compose`는 루트 **`.env`**만 자동 로드한다. `GATEWAY_SHARED_SECRET`처럼 compose 파일에서 `${VAR:-}` 형태로 넘기는 값은, `.env`에 **빈 할당(`VAR=`)만** 있으면 컨테이너에 빈 문자열이 들어가 **Spring `application.yml`의 기본값이 적용되지 않을 수 있다**(게이트웨이 기동 실패 등). **비어 있지 않은 값**으로 맞추거나 변수 줄을 제거하고, 게이트웨이·Proxy·usage-service의 공유 비밀은 [`docs/contracts/gateway-proxy.md`](contracts/gateway-proxy.md) §5와 동일하게 유지한다.

### 10.2 단일 도메인·엣지 라우팅(브라우저 URL 하나)

운영·로컬 통합 진입점에서 **호스트명은 하나**로 두고, **경로 prefix**로 트래픽을 나누는 것을 권장한다.

- **엣지:** Nginx·Traefik 등 **리버스 프록시** 한 계층에서 `location`(또는 동등 규칙)으로 upstream을 고정한다.
- **로컬 Compose(`profile: web`):** **`web-edge`** 서비스(이미지 `nginx`, 설정 **`docker/web-edge/nginx.conf`**)가 기본 **`${WEB_EDGE_PORT:-8888}:80`** 으로 호스트에 노출된다. 현재 저장소 규칙(정본은 설정 파일): **`/dashboard`** 는 **`308`** 으로 **`/dashboard/`** 로 보내고, **`/dashboard/`** 로 시작하는 경로만 **usage `web`** 으로 프록시한다(`/dashboard2` 등은 매칭되지 않아 **identity `web`**). **`/api/v1`** 은 **`/api/v1/`** 로 **`308`** 리다이렉트 후, **`/api/v1/*`** 는 **API Gateway**로 프록시한다(스트리밍 대비 **`proxy_buffering off`**·긴 read timeout). **그 외**는 **identity `web`**. (운영 엣지는 팀이 동일한 의미로 맞춘다.)
- **Usage Next `basePath`:** 단일 도메인에서 `/_next` 등 충돌을 피하기 위해 Usage 쪽 기본값은 **`/dashboard`**(`NEXT_PUBLIC_BASE_PATH`, Compose 빌드 args). 브라우저의 Usage BFF는 **`/dashboard/api/usage/...`** 형태가 된다(`docs/contracts/web-split-boundary.md`, `web-gateway-bff.md`).
- **쿠키·세션:** 동일 **`Site`/도메인**에서 경로만 나뉘면 `httpOnly` 세션 쿠키는 대부분 유지 가능하지만, **`Path`·`SameSite`** 는 분리 후 반드시 재검증한다(BFF 계약: `docs/contracts/web-identity-bff.md`).

- **로컬 개발(필수에 가까운 구성)**
  - **Docker Compose**: `PostgreSQL`, `RabbitMQ`, `Redis` 등 의존성 컨테이너 기동
  - **애플리케이션(Proxy WebFlux 등)**: 로컬 JVM에서 실행(IDE/터미널)하거나, 패턴 B에 맞게 **서비스별 이미지**로 Compose에 포함
- **선택**
  - API Gateway·Proxy: 저장소 `docker-compose.yml`에 포함 가능(계약: `docs/contracts/gateway-proxy.md`)
  - Next.js: **도메인별 `services/<svc>/web`** — `docker compose --profile web up` 시 **`identity-web`**, **`usage-web`**, **`web-edge`**(루트 `docker-compose.yml`). 구 통합 앱 경로 `apps/web`에는 안내용 `README.md`만 둔다(`docs/repository-structure.md` §6.2).
  - GitHub Actions(CI): 저장소 정책에 따라 도입(`docs/CI.md`)
  - Prometheus + Grafana, Loki, Jaeger: 시간 여유 시(관측 강화)
- **Kubernetes / Ingress / ConfigMap·Secret(K8s)**
  - 현재 범위에서는 **사용하지 않음**. 설정·비밀값은 **환경변수·`.env`(비커밋)·GitHub Secrets** 등으로 관리한다.

---

## 11. 서비스 간 책임 요약(개발자 참고)

- Proxy Service: “AI 요청을 실제 Provider로 전달하고, usage를 즉시 기록”
- Usage Tracking Service: “usage 로그의 사실 저장”
- Billing Service: “usage 기반 정산 금액 계산 및 billing_record 저장”
- Analytics & Reporting Service: “대시보드/리포트용 집계 데이터 생성”
- Quota Service: “예산/제한 정책 소유 및 초과 기준 제공”
- Notification Service: “Slack/Email 등 알림 발송”
- Identity & Organization Service: “사용자/조직/팀/멤버십/RBAC”
- API Key Service: “공급사 API Key의 암호화 저장/조회”
- **집계·알림 전담 백엔드(Analytics·Notification 등)**: 아래 **§12** 참고 — **도메인 UI(`web/`) 담당과 동일 사람이 아닐 수 있음**(서비스 경계 유지).
- **서비스 단위 웹·BFF**: 아래 **§13** 참고.

---

## 12. 백엔드 — Analytics·Reporting·Notification (집계·알림)

**§4.7 Analytics & Reporting**, **§4.9 Notification** 에 해당하는 **집계·알림·리포트 파이프라인**은 백엔드 마이크로서비스(또는 동일 책임 모듈) 범위다. 구현 주체는 팀 합의에 따르며, **Identity·Usage 등 각 도메인의 `web/` 풀스택 작업**과 **혼동하지 않는다**(UI는 집계 API를 소비할 뿐, §12 파이프라인 구현 책임은 별도).

### 12.1 담당 서비스·산출물

- **Analytics & Reporting Service**(또는 동일 책임의 모듈): 집계 API, 리포트 생성 파이프라인.
- **Notification Service**(또는 동일 책임의 워커): Slack·이메일 등 외부 채널 발송(§4.9와 정합).

### 12.2 데이터 집계(Aggregation)

- **입력**: Proxy 등에서 발행되는 **`usage-recorded`** 등 이벤트(§6.1), 필요 시 Usage/Billing 저장소 또는 이들을 조회하는 **허용된 API**만 사용한다(타 서비스 DB 직접 접근 지양, `docs/repository-structure.md` 참고).
- **처리 방식**: 실시간 스트림 소비(**RabbitMQ** 소비자)와 **배치·스케줄 기반** 집계를 병행할 수 있다.
- **산출 예시**: 일별·월별·모델별·팀/조직별 **비용·토큰 통계**, 대시보드용 요약 시계열.
- **실시간 성능**: 대시보드 조회 부하 완화를 위해 **Redis** 카운터(예: 기간·스코프별 `INCRBY`)로 당일/당월 누적을 유지하는 패턴을 사용할 수 있다(§3.3·§10의 Redis 전제와 연계).

### 12.3 알림 엔진(Notification)

- **입력**: Quota/비용 **임계치**(예: 예산 **80%**, **100%**)는 **Quota Service·Billing·Identity 등이 제공하는 정책·집계**를 기준으로 한다(§7).
- **동작**: 조건 충족 시 **Slack Webhook**, **이메일(Resend 등)** 등으로 발송; **동일 임계치에 대한 중복 알림 방지** 및 최소한의 **발송 이력**을 둔다(§4.9).
- **이벤트 연계**: `quota-warning` / `quota-exceeded` 등(§6.1)을 소비해 “발송만” 수행하는 구조를 권장한다.

### 12.4 리포트 생성(Reporting)

- **주기**: 월별·주별 등 팀이 정한 주기로 **사용량·비용 분석 리포트**를 생성한다(MVP 선택 기능, §1.3).
- **형식**: **JSON** API로 내려주거나, **PDF**(렌더링 라이브러리·헤드리스 브라우저 등 팀 합의), **CSV** 다운로드 등을 제공할 수 있다.

### 12.5 비용 분석(가공)

- 공급사·모델 간 **비용 효율성 비교**를 위한 파생 지표(예: 단위 토큰당 비용, 모델별 점유율)를 산출해 **API·리포트**로 노출할 수 있다. 프론트엔드는 이 데이터를 소비해 표시한다(§13).

---

## 13. 서비스 단위 웹·BFF(풀스택 소유)

**별도 “프론트 전담” 역할을 두지 않는다.** 각 도메인 서비스를 맡은 팀·개발자가 **같은 저장소 경계 안에서 Spring 앱과(필요 시) `web/` Next 앱**을 함께 유지한다(`docs/repository-structure.md` §6).

### 13.1 담당·연동

- **Identity 계열**: `services/identity-service` + `services/identity-service/web/` — 랜딩·인증·조직/팀 설정 UI, `/api/auth/**`·`/api/identity/**` BFF 등. 계약: `docs/contracts/web-identity-bff.md`.
- **Usage·대시보드 계열**: `services/usage-service` + `services/usage-service/web/` — 사용량 대시보드, `/api/usage/**` BFF → 게이트웨이. 계약: `docs/contracts/web-gateway-bff.md`, `docs/contracts/gateway-proxy.md`.
- **웹 경계**: `docs/contracts/web-split-boundary.md` — 경로·BFF·미들웨어 변경 시 **본 문서·계약 문서**를 코드와 같이 갱신한다.
- **Proxy·API Gateway**: 공개 AI·Usage HTTP 진입·신뢰 헤더 — 게이트웨이·프록시 구현 팀과 **HTTP 계약**만 맞춘다.

### 13.2 대시보드·시각화·보안(UI)

- **권한별 UI**: 로그인 사용자의 **역할(RBAC)** 에 따라 뷰를 구분한다(§8.4).
- **시각화**: §12 등이 노출하는 **집계·조회 API** 응답을 차트·테이블로 표현한다.
- **보안**: 공급사 API Key·내부 토큰을 **브라우저 번들에 넣지 않는다**(§8). 플랫폼 JWT는 BFF·`httpOnly` 쿠키 패턴을 유지한다.

---

## 14. 문서 유지 규칙(팀 공통)
- 아키텍처 변경(서비스 경계/이벤트/테이블 소유권)이 발생하면 이 문서를 먼저 업데이트한다.
- 서비스 추가/삭제는 “책임(도메인) 기준”으로 판단한다.
- 이벤트 스키마/키 이름은 문서에 명시한다.
- 요청·이벤트 흐름 시각화: `docs/sequence-diagrams.md` (Mermaid)를 함께 갱신한다.
- **§12(백엔드 집계·알림)** 또는 **§13(서비스 단위 웹·BFF)**·**§2.1**·**§10.2(단일 도메인)** 범위가 바뀌면 해당 절을 함께 갱신한다.
