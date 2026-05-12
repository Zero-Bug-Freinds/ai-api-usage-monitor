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

### 팀 초대 인앱 정리(void)

- **`TEAM_DELETED`:** `meta.teamId`가 삭제된 팀과 일치하는 `team:TEAM_INVITE_CREATED` 행을 void(`actions` 제거·`readAt`·`staleReason` 등) — 미가입 초대 수신자는 삭제 이벤트 멤버 스냅샷에 없을 수 있어, 인앱만으로 UX를 맞춘다.
- **초대 액션 실패:** `POST …/team-invitations/…/accept|reject` 처리 중 team-service가 **400/404/409**를 반환하면, 해당 `invitationId`의 초대 인앱을 void(`staleReason` 예: `INVITE_ACTION_FAILED`)한 뒤 동일 HTTP 상태로 응답한다. 내부 HTTP **타임아웃**은 **504**에 가깝게 매핑된다(`TEAM_SERVICE_INTERNAL_TIMEOUT_MS`).

## Billing 예산 임계 이벤트 (RabbitMQ, 인앱 알림)

notification-service는 billing-service가 발행하는 예산 임계 이벤트를 **두 개의 라우팅 키**로 나누어 소비할 수 있다.

### 개인·Identity API 키 (`billing.budget.threshold.reached`)

- **토폴로지(기본)**: exchange `billing.events`, routing key `billing.budget.threshold.reached`
- **활성화 플래그**: `BILLING_EVENTS_CONSUMER_ENABLED=true` (기본값; 비활성화 시 이 소비자 미기동)
- **큐(기본)**: `BILLING_EVENTS_QUEUE_NAME` → `notification.billing.events`
- **브로커 URL**: `RABBITMQ_URL` (필수)
- **멱등(dedupe)**: `NotificationDelivery.dedupeKey` 유니크 제약으로 동일 임계 이벤트의 중복 인앱 생성 방지
  - dedupe 키의 임계값 파트는 `thresholdPct`를 퍼센트 정수로 정규화해 `pct{n}` 형식을 사용한다(예: 0.8 → `pct80`).
- **인앱 `type`**: `billing:budget-threshold`

### 팀 API 키 (`billing.team.budget.threshold.reached`)

- **토폴로지(기본)**: exchange `billing.events`, routing key `billing.team.budget.threshold.reached` (**개인 키 스트림과 다른 전용 큐로 바인딩할 것**)
- **활성화 플래그**: `BILLING_TEAM_EVENTS_CONSUMER_ENABLED=true` (기본값)
- **큐(기본)**: `BILLING_TEAM_EVENTS_QUEUE_NAME` → `notification.billing.team.events`
- **assert**: `BILLING_TEAM_EVENTS_ASSERT_TOPOLOGY`(기본 `true`), `BILLING_TEAM_EVENTS_EXCHANGE_NAME`, `BILLING_TEAM_EVENTS_BINDING_KEYS`, `BILLING_TEAM_EVENTS_PREFETCH`
- **팀원 조회**: 이벤트의 `triggerUserId`를 `X-User-Id`로 넣어 team-service `GET /api/v1/teams/{teamId}/members`를 호출하고, 팀 이름은 `GET /internal/teams/{teamId}`로 조회한다(`TEAM_SERVICE_BASE_URL`).
- **멱등**: 수신 사용자(`targetUserId`)마다 별도 `NotificationDelivery.dedupeKey`(팀·키·월·임계·사용자 조합).
- **인앱 `type`**: `billing:team-api-key-budget-threshold`

페이로드·헤더 정본은 루트 [`docs/billing-outbound-events.md`](../../docs/billing-outbound-events.md) (§2 및 §2.1).
