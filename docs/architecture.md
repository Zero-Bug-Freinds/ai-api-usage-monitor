# <Team Project> AI Usage & Billing Platform (MSA) 아키텍처 문서
버전: 0.2

---

## 0. 관련 문서

- **MSA 이론 배경**(일반 개념·특징·API Gateway 패턴): [`docs/msa-architecture-theory.md`](msa-architecture-theory.md)
  - 본 문서(`architecture.md`)는 **이 팀 프로젝트의 구조·스택·서비스 분해**를 다룬다. 위 이론 문서는 구현과 무관한 **일반 설명**을 위한 참고 자료이다.
- **이벤트 소비 흐름**(proxy 발행 `UsageRecordedEvent` → usage·analytics·billing·alarm 등): [`docs/event-consumer-flow.md`](event-consumer-flow.md)

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
  - API Key 등록
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
- **프론트엔드**: 웹 브라우저(별도 FE 저장소 또는 모노레포 시 팀 규칙에 따름).

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
  - 조직/팀 생성, 멤버십, 권한(RBAC)
- 책임 범위
  - 사용자-조직-팀-권한 모델을 관리한다.
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

- **로컬 개발(필수에 가까운 구성)**
  - **Docker Compose**: `PostgreSQL`, `RabbitMQ`, `Redis` 등 의존성 컨테이너 기동
  - **애플리케이션(Proxy WebFlux 등)**: 로컬 JVM에서 실행(IDE/터미널), Compose에 올린 브로커·DB 주소로 연결
- **선택**
  - API Gateway: 로컬 실행 또는 추후 게이트웨이만 Compose에 포함(팀 합의)
  - GitHub Actions(CI): 저장소 정책에 따라 도입
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

---

## 12. 문서 유지 규칙(팀 공통)
- 아키텍처 변경(서비스 경계/이벤트/테이블 소유권)이 발생하면 이 문서를 먼저 업데이트한다.
- 서비스 추가/삭제는 “책임(도메인) 기준”으로 판단한다.
- 이벤트 스키마/키 이름은 문서에 명시한다.
- 요청·이벤트 흐름 시각화: `docs/sequence-diagrams.md` (Mermaid)를 함께 갱신한다.
