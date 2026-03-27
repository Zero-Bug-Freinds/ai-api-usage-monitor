# <Team Project> AI Usage & Billing Platform (MSA)

프록시 서버 기반으로 OpenAI / Gemini / Claude 등 AI Provider의 **실시간 사용량과 비용**을 수집·분석하고, **개인/팀/조직 단위 Quota 및 정산**을 돕는 MSA 플랫폼입니다.

## 목표(Goals)
- 조직 내 AI API 사용량을 투명하게 관리
- 팀별 AI 비용 자동 정산
- 개인 사용자의 AI 비용/사용 패턴 별도 추적 및 분석
- 사용량 폭증을 사전에 감지
- 사용자/팀/조직별 비용 책임을 명확히 분리

## 핵심 기능(요약)
- **Proxy 기반 사용량 수집**: 사용자는 Provider를 직접 호출하지 않고 플랫폼 프록시로 호출
- **비용 계산 및 정산**: 사용량(토큰/estimated cost)을 기반으로 개인/팀/조직 비용 집계
- **대시보드**: 개인/조직·팀별 사용량 및 비용 확인
- **Quota / Budget 제한(선택)**: soft limit(경고) / hard block(차단) 정책
- **알림(선택)**: Slack/Email 등으로 임계치 도달 알림

## 아키텍처(High-Level)
- 외부 요청 진입: `API Gateway`
- 핵심 도메인: `Proxy Service`(AI 호출 중계 + usage 기록)
- 비동기 처리: `RabbitMQ` 이벤트 기반 연계(usage-recorded 등)
- 도메인 서비스 분리:
  - `Identity & Organization Service`(개인/조직/팀/RBAC)
  - `API Key Service`(공급사 키 암호화 저장/조회)
  - `Usage Tracking Service`(usage 저장)
  - `Billing Service`(비용 계산/정산 기록 저장)
  - `Analytics & Reporting Service`(대시보드 집계/리포트, 선택)
  - `Quota Service`(정책/제한 기준)
  - `Notification Service`(알림 발송, 선택)

## 기술 스택(결정)
- **백엔드(Proxy 등)**: **Spring Boot + Spring WebFlux** — 비동기 I/O·스트리밍·Provider 중계에 사용. **FastAPI(Python)는 사용하지 않습니다.**
- **메시지 브로커**: **RabbitMQ** — `usage-recorded` 등 이벤트 발행·구독(Spring AMQP).
- **기타 서비스**: 동일 Spring 생태계에서 Spring MVC + JPA 등으로 구현 가능(팀 합의).
- 상세: `docs/architecture.md` §2.1, §6.2

## 로컬 개발 관련(중요)
- **Kubernetes는 사용하지 않음**: 배포하지 않는 캡스톤 환경을 전제로 합니다.
- **실행 순서·(Google / OpenAI) 키·경로·로그/Rabbit/DB로 사용량 파이프라인 검증**은 **[`docs/local-run-and-usage-verification.md`](docs/local-run-and-usage-verification.md)** 를 본다.
- **의존성(DB·큐·캐시)**: **Docker Compose**로 실행합니다. 예: `PostgreSQL`, `RabbitMQ`, `Redis`.
- **API Gateway + Proxy**: `docker-compose.yml`에서 **컨테이너로 함께 기동**할 수 있습니다(호스트 포트 기본 `8080` / `8081`). 계약·경로는 `docs/contracts/gateway-proxy.md`를 참고합니다.
- **identity-service** 등 그 외 앱도 **로컬 JVM** 실행을 기본으로 하며, 필요 시 Compose에 추가할 수 있습니다.

## 모노레포 레이아웃(요약)
실행 가능한 앱은 `services/` 하위에 둡니다. (`docs/repository-structure.md` 참고)
- `services/api-gateway-service` — API Gateway(Spring Cloud Gateway), JWT·라우팅·Proxy로 신뢰 헤더 전달
- `services/proxy-service` — AI Provider 프록시(WebFlux), usage 이벤트 발행
- `services/usage-service` — `UsageRecordedEvent` 소비·PostgreSQL 원장 저장(Spring AMQP + JPA)
- `services/identity-service` — 계정·조직·API Key 등(Identity)
- `libs/usage-events` — 공유 이벤트(`UsageRecordedEvent` 등)

## 문서
- **로컬 실행·테스트(Gateway / Proxy / usage·이벤트·DB)**: `docs/local-run-and-usage-verification.md`
- Gateway ↔ Proxy 계약: `docs/contracts/gateway-proxy.md`
- Usage 이벤트 소비·팬아웃: `docs/event-consumer-flow.md` · usage/analytics 경계: `docs/usage-analytics-relationship.md`
- C4 다이어그램(코드 기준): `docs/c4-architecture-diagrams.md`
  - Mermaid Live Editor에서 렌더링/검증 가능: https://mermaid.live/
- 아키텍처 문서: `docs/architecture.md`
- 시퀀스 다이어그램(AI 호출·이벤트·대시보드 조회): `docs/sequence-diagrams.md`
- 저장소·디렉터리 구조(모노레포): `docs/repository-structure.md`
- MSA 이론 배경: `docs/msa-architecture-theory.md`
- 코드 컨벤션(네이밍·스타일): `docs/code-conventions.md`
- 브랜치·Git Flow·보호 규칙: `docs/branch-conventions.md`
- 커밋 메시지 규칙: `docs/commit-conventions.md`
- GitHub Actions CI: `docs/CI.md`

## 팀 정보
- 팀원: 김민진, 박예나, 조민선

해당 프로젝트는 2026년 1학기 팀프로젝트1 캡스톤 디자인에서 개발하는 MSA 기반 서비스입니다.
