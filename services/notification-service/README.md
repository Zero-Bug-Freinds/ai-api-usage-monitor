# Notification Service (Node / NestJS)

`docs/architecture.md` §12 알림 파이프라인의 **백엔드** 스캐폴딩. 브라우저 UI·BFF는 추후 `web/` 로 분리할 수 있다(`project-common-nextjs.mdc`).

## 로컬 실행

1. 루트에서 `pnpm install` (워크스페이스에 포함됨).
2. PostgreSQL에 DB·유저 준비 후 `.env`에 `DATABASE_URL` 설정 (`.env.example` 참고).
3. `pnpm prisma:migrate:dev` 로 마이그레이션 적용.
4. `pnpm start:dev` (기본 포트 `8096`, billing과 구분).

- Health: `GET /health`
- OpenAPI: `GET /api/docs`

Cursor 규칙: `.cursor/rules/notification-backend-node.mdc`

문서 연계: 팀 이벤트 스키마·exchange 요약은 [`docs/contracts/web-team-bff.md`](../../docs/contracts/web-team-bff.md) §6.2, 플랫폼 아키텍처·브로커 토폴로지는 [`docs/architecture.md`](../../docs/architecture.md) §4.9·§6.

## 팀 도메인 이벤트 (RabbitMQ)

notification-service는 선택적으로 **팀 도메인 이벤트**(`TEAM_CREATED`, `TEAM_INVITE_CREATED` 등)를 소비해 **인앱 알림**을 만든다. 저장소 밖에서 배포하는 경우 다음을 인프라/플랫폼 팀과 맞춘다.

1. **브로커 URL**을 비밀/설정으로 주입한다 (`RABBITMQ_URL`).
2. **큐**를 생성하고, team-service가 발행하는 **exchange**(`TEAM_EVENTS_EXCHANGE_NAME`, 예: `team.events`)에 **라우팅 키**로 바인딩한다. 이 서비스는 `TEAM_EVENTS_QUEUE_NAME`(기본 `notification.team.events`)을 구독한다.
3. 로컬에서 토폴로지를 앱이 직접 만들도록 할 수 있다 (`TEAM_EVENTS_ASSERT_TOPOLOGY=true`, exchange + `TEAM_EVENTS_BINDING_KEYS`). 운영에서는 보통 인프라가 큐·바인딩을 소유하고, 앱은 **assert를 끈다**(`false`).
4. 브로커 없이 HTTP만 쓰려면 `TEAM_EVENTS_CONSUMER_ENABLED=false`를 둔다.

루트 `docker compose --profile web` 의 `notification-service` 는 `rabbitmq`에 의존하고, 기본으로 `RABBITMQ_URL`·소비자·`TEAM_EVENTS_ASSERT_TOPOLOGY`를 켜며 team-service 와 동일한 `team.events` / `team-member-added` 바인딩을 assert한다(`.env`에서 `TEAM_EVENTS_CONSUMER_ENABLED` 등으로 덮어쓸 수 있음).

환경 변수 전체는 `services/notification-service/.env.example`을 본다.

**제품 규칙:** `TEAM_INVITATION_ACCEPTED`는 초대한 사람에게, `TEAM_MEMBER_JOINED`는 **참여한 사용자(`receiverId`)에게만** 인앱을 생성한다. 초대자는 수락 알림만 받고, 동일 흐름에서 `TEAM_MEMBER_JOINED`로 중복 행이 생기지 않는다.

## Billing 예산 임계 이벤트 (RabbitMQ, 인앱 알림)

notification-service는 billing-service가 발행하는 `billing.budget.threshold.reached` 이벤트를 선택적으로 소비해 **인앱 알림**을 생성한다.

- **토폴로지(기본)**: exchange `billing.events`, routing key `billing.budget.threshold.reached`
- **활성화 플래그**: `BILLING_EVENTS_CONSUMER_ENABLED=true` (기본값; 비활성화 시 소비자 미기동)
- **브로커 URL**: `RABBITMQ_URL` (필수)
- **멱등(dedupe)**: `NotificationDelivery.dedupeKey` 유니크 제약으로 동일 임계 이벤트의 중복 인앱 생성 방지
  - dedupe 키의 임계값 파트는 `thresholdPct`를 퍼센트 정수로 정규화해 `pct{n}` 형식을 사용한다(예: 0.8 → `pct80`).
