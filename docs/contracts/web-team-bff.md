# Web(Next.js) ↔ Team Service BFF 계약

버전: 0.8  
관련: [web-split-boundary.md](./web-split-boundary.md), [web-identity-bff.md](./web-identity-bff.md) — `/teams` UI 소유·경로: §2.3

---

## 1. 목적

- `services/team-service/web/`에서 팀 생성/조회/아이디 초대/팀 API Key 등록·조회를 BFF로 처리한다.
- 브라우저는 `access_token` httpOnly 쿠키를 유지하고, Team BFF는 Authorization 헤더를 보존해 Gateway로 전달한다.

---

## 2. 엔드포인트 계약

| 브라우저 경로 (`web-edge` 기준) | Upstream |
|---|---|
| `GET /api/team/v1/me/teams` | Team BFF `GET /api/team/v1/me/teams` → Gateway `GET /api/team/v1/me/teams` → Team Service `GET /api/v1/me/teams` |
| `POST /api/team/v1/teams` | Team BFF `POST /api/team/v1/teams` → Gateway `POST /api/team/v1/teams` → Team Service `POST /api/v1/teams` |
| `POST /api/team/v1/teams/{id}/members` | Team BFF `POST /api/team/v1/teams/{id}/members` → Gateway `POST /api/team/v1/teams/{id}/members` → Team Service `POST /api/v1/teams/{id}/members` |
| `GET /api/team/v1/teams/{id}/members` | Team BFF `GET ...` → Gateway `GET ...` → Team Service `GET /api/v1/teams/{id}/members` |
| `GET /api/team/v1/teams/{id}/owner` | Team BFF `GET ...` → Gateway `GET ...` → Team Service `GET /api/v1/teams/{id}/owner` (`data`: 팀장 여부 `boolean`) |
| `DELETE /api/team/v1/teams/{teamId}/members/{userId}` | Team BFF `DELETE ...` → Gateway `DELETE ...` → Team Service `DELETE /api/v1/teams/{teamId}/members/{userId}` |
| `DELETE /api/team/v1/teams/{teamId}` | Team BFF `DELETE ...` → Gateway `DELETE ...` → Team Service `DELETE /api/v1/teams/{teamId}` |
| `GET /api/team/v1/me/team-invitations` | Team BFF `GET ...` → Gateway `GET ...` → Team Service `GET /api/v1/me/team-invitations` |
| `POST /api/team/v1/me/team-invitations/{invitationId}/accept` | Team BFF `POST ...` → Gateway `POST ...` → Team Service `POST /api/v1/me/team-invitations/{invitationId}/accept` |
| `POST /api/team/v1/me/team-invitations/{invitationId}/reject` | Team BFF `POST ...` → Gateway `POST ...` → Team Service `POST /api/v1/me/team-invitations/{invitationId}/reject` |
| `GET /api/team/v1/teams/{id}/api-keys` | Team BFF `GET /api/team/v1/teams/{id}/api-keys` → Gateway `GET ...` → Team Service `GET /api/v1/teams/{id}/api-keys` |
| `POST /api/team/v1/teams/{id}/api-keys` | Team BFF `POST /api/team/v1/teams/{id}/api-keys` → Gateway `POST ...` → Team Service `POST /api/v1/teams/{id}/api-keys` |
| `PUT /api/team/v1/teams/{teamId}/api-keys/{keyId}` | Team BFF `PUT ...` → Gateway `PUT ...` → Team Service `PUT /api/v1/teams/{teamId}/api-keys/{keyId}` |
| `DELETE /api/team/v1/teams/{teamId}/api-keys/{keyId}` | Team BFF `DELETE ...` → Gateway `DELETE ...` → Team Service `DELETE /api/v1/teams/{teamId}/api-keys/{keyId}` (선택 쿼리 `gracePeriodDays`) |
| `POST /api/team/v1/teams/{teamId}/api-keys/{keyId}/deletion/cancel` | Team BFF `POST ...` → Gateway `POST ...` → Team Service `POST /api/v1/teams/{teamId}/api-keys/{keyId}/deletion/cancel` |

- **팀 콘솔 UI**는 `web-edge`의 **`/teams` → `web-host`(Main Shell, `apps/web`)** 에서 렌더링한다(Task37-13·MFE). `team-web`은 BFF만 담당한다.
- `web-edge`는 **`/teams/api/*`** 및 **`/api/team/v1/*`** 를 Team BFF(`team-web`)로 넘긴다(삭제 예정 해제용 `POST .../deletion/cancel` 포함). 풀페이지 `/teams` HTML은 **web-host**가 제공한다.
- Team BFF는 `GATEWAY_URL` 환경 변수로 Gateway를 프록시한다.
- Team BFF는 `IDENTITY_SERVICE_URL`로 세션 확인(`GET /api/auth/session`)을 프록시한다.

---

## 3. 요청/응답 공통 규칙

- `Authorization` 헤더가 있으면 그대로 업스트림(Gateway)으로 전달한다.
- `Authorization` 헤더가 없고 `access_token` 쿠키가 있으면 BFF가 `Bearer <token>`을 구성해 전달한다.
- 두 인증 정보가 모두 없으면 BFF는 업스트림 호출 없이 `401`을 반환한다.
- 응답에는 `Cache-Control: no-store`를 적용한다.
- 성공/실패 응답은 Team Service의 `ApiResponse` 포맷을 그대로 전달한다.

---

## 4. 보안/권한

- 아래는 모두 Gateway를 경유한 인증이 필요하다. 권한은 Team Service가 최종 판단한다.
- **팀장(OWNER) 전용**
  - `DELETE /api/v1/teams/{teamId}/members/{userId}` (팀원 삭제; 팀장 본인 삭제는 불가)
  - `DELETE /api/v1/teams/{teamId}` (팀 삭제; 팀 API Key 행이 하나라도 남아 있으면 `409`)
  - `POST /api/v1/teams/{id}/api-keys` (팀 API Key 등록)
  - `DELETE /api/v1/teams/{teamId}/api-keys/{keyId}` (삭제 예정 등록 또는 `gracePeriodDays=0` 즉시 삭제)
- **팀 멤버면 가능**(팀장 아님 포함)
  - `GET /api/v1/me/teams`, `GET /api/v1/teams/{id}/members`, `GET /api/v1/teams/{id}/api-keys`, `GET /api/v1/teams/{id}/owner`
  - `POST /api/v1/teams/{id}/members` (초대)
  - `GET /api/v1/me/team-invitations`, 초대 수락/거절
  - `PUT /api/v1/teams/{teamId}/api-keys/{keyId}` (별칭·예산 등 수정)
  - `POST /api/v1/teams/{teamId}/api-keys/{keyId}/deletion/cancel` (삭제 예정 해제)
- 팀원 초대 시 Team Service는 아래 순서로 사용자 존재 여부를 확인한다.
  1) `identity_user_sync` 로컬 캐시(`IdentityUserSyncListener`가 RabbitMQ로 수신한 사용자 동기화 이벤트 기반)
  2) Identity 내부 API fallback (`GET /internal/users/exists?email=...` 또는 user-id 일괄 확인)
  → 둘 다 실패/미존재면 초대를 거부한다.
- 팀 API Key는 Team Service DB에 **평문 저장하지 않고 암호화 저장**한다.
- 팀 API Key 조회·등록·수정 응답에는 **원문 키를 포함하지 않는다**(미리보기 필드 없음). 저장은 DB에 암호화된다.
- 팀 삭제(`DELETE /teams/{teamId}`)는 팀 API Key가 하나라도 남아 있으면 `409`으로 거절된다.
- Identity `web`의 `TeamsView`는 펼친 팀에 대해 `GET /api/team/v1/teams/{id}/owner`로 팀장 여부를 조회하고, **팀장일 때만** 팀원 삭제·팀 삭제·팀 API Key 등록·삭제(및 삭제 취소) UI를 노출한다.

---

## 5. 팀원 초대 검증 규칙

- 입력값 `inviteeUserId`는 가입/로그인에 사용한 이메일 형식의 아이디를 기준으로 한다.
- 존재하지 않는 아이디(이메일)이면 Team Service는 초대를 거부한다.
- Identity 연동 실패(네트워크/5xx/비정상 응답) 시 안전하게 초대를 거부한다.
- Team Service는 사용자 전체 프로필을 복제하지 않고, 존재성 판단에 필요한 최소 필드만 `identity_user_sync` 테이블에 저장한다(`userId`, `email`, `displayName`, `lastEventType`, `updatedAt`).

### 5.2 identity → team 사용자 동기화 이벤트

- Queue/Binding 설정 정본:
  - `identity.user-sync.exchange` (기본 `identity.events`)
  - `identity.user-sync.routing-key` (기본 `identity.user.sync`)
  - `identity.user-sync.queue` (기본 `team.identity.user-sync.queue`)
- 리스너: `IdentityUserSyncListener` (`@RabbitListener`)
- DTO: `IdentityUserSyncEvent`
  - `@JsonAlias`로 필드명 변형(`eventType/type`, `userId/identityUserId/id`, `email/userEmail`, `name/displayName/username`, `occurredAt/createdAt/updatedAt`)을 허용한다.
- 장애 분석을 위해 리스너는 실패 시 payload 원문과 stacktrace를 함께 기록한다.

### 5.1 실패 응답 예시

- `POST /api/team/v1/teams/{id}/members`
  - `400` (`success=false`): 존재하지 않는 사용자 아이디(이메일)로 초대 요청한 경우
  - `403` (`success=false`): 요청자가 팀 멤버가 아닌 경우
  - `404` (`success=false`): 대상 팀이 존재하지 않는 경우

- `DELETE /api/team/v1/teams/{teamId}/members/{userId}`
  - `403` (`success=false`): 요청자가 팀장이 아닌 경우(팀장 전용)
  - `400` (`success=false`): 삭제 대상 멤버가 없거나, 팀장(OWNER) 삭제 시도
  - `404` (`success=false`): 대상 팀이 존재하지 않는 경우

- `DELETE /api/team/v1/teams/{teamId}`
  - `403` (`success=false`): 요청자가 팀장이 아닌 경우(팀장 전용)
  - `409` (`success=false`): 팀 API Key가 남아 있어 삭제 선행조건 미충족
  - `404` (`success=false`): 대상 팀이 존재하지 않는 경우

### 5.2 팀 초대·수락/거절/만료 (현행 구현)

- `POST /api/team/v1/teams/{id}/members` 호출 시 Team Service는 **PENDING 초대 행**을 만들고 RabbitMQ로 초대 이벤트(`TEAM_INVITE_CREATED`)를 발행한다. 이 단계에서는 **팀 멤버를 추가하지 않는다**.
- `GET /api/team/v1/me/team-invitations` 로 내 초대 목록을 조회한다. 기본은 **대기(PENDING)**만 반환한다.
- `GET /api/team/v1/me/team-invitations?includeExpired=true` 호출 시:
  - `invitee` 기준 `PENDING` + `EXPIRED`를 반환한다.
  - 추가로 `inviter` 기준 `EXPIRED`도 함께 반환한다(중복 `invitationId`는 서버에서 1건으로 정리).
- 응답 항목에는 `viewerRole`이 포함된다.
  - `INVITER`: 조회 사용자가 초대한 사람
  - `INVITEE`: 조회 사용자가 초대받은 사람
  - `UNKNOWN`: 식별자 매칭 실패 시 fallback 값
- `POST /api/team/v1/me/team-invitations/{invitationId}/accept` 성공 시 초대 상태를 `ACCEPTED`로 바꾸고, 초대 대상 사용자를 팀 멤버(MEMBER)로 추가한다(이미 멤버인 경우 중복 추가는 생략).
- `POST /api/team/v1/me/team-invitations/{invitationId}/reject` 성공 시 초대 상태를 `REJECTED`로 바꾸며, 멤버는 추가되지 않는다.
- 초대 생성 후 `team.invitation.expiration-days`(기본 7일)를 초과하면 상태가 `EXPIRED`로 자동 전환된다.
- 만료된(`EXPIRED`) 초대 또는 이미 처리된(`ACCEPTED`/`REJECTED`) 초대를 다시 수락/거절하면 `400`을 반환한다.
- `GET /api/team/v1/me/team-invitations`는 조회 시점에 만료 스윕을 먼저 수행한다. 응답 객체에는 만료/처리 시점을 위한 `respondedAt`이 포함된다.
- 스케줄러(`TeamInvitationLifecycleScheduler`)가 주기적으로 만료 처리 + 오래된 초대 정리를 수행한다.
  - `team.invitation.lifecycle-fixed-delay-ms` (기본 3600000)
  - `team.invitation.lifecycle-initial-delay-ms` (기본 60000)
  - `team.invitation.cleanup-retention-days` (기본 30): `ACCEPTED`/`REJECTED`/`EXPIRED` 상태 중 `respondedAt`이 보존 기간을 지난 행 삭제

### 5.3 팀 API Key 등록/조회/수정·삭제 예정

#### 팀 API Key 요약 객체 (`data` 및 목록 항목)

| 필드 | 설명 |
|---|---|
| `id` | 키 행 ID |
| `provider` | `OPENAI` / `GEMINI` / `CLAUDE` |
| `alias` | 별칭 |
| `monthlyBudgetUsd` | 월 예산(USD), 숫자 |
| `createdAt` | 생성 시각(ISO-8601) |
| `deletionRequestedAt` | 삭제 예정 요청 시각. 삭제 예정이 아니면 보통 응답에 포함되지 않음(null 생략). |
| `permanentDeletionAt` | 유예 종료·영구 삭제 예정 시각. 삭제 예정이 아니면 생략. |
| `deletionGraceDays` | 삭제 요청 시 선택한 유예 기간(일). 생략 시 서버 기본은 7일. 삭제 예정이 아니면 생략. |

- `POST /api/team/v1/teams/{id}/api-keys`
  - 요청 본문: `provider` (`OPENAI`/`GEMINI`/`CLAUDE`), `alias`, `externalKey`, `monthlyBudgetUsd` (0 이상, USD 월 예산 한도 — identity-service 외부 키와 동일 개념)
  - 성공: `201`, `data`에 등록된 키 요약(위 표의 활성 키에 해당하는 필드)
  - 실패:
    - `400` (`success=false`): 필수값 누락, alias 중복, 동일 provider+키 값(해시)이 **이미 활성**인 경우 `이미 등록된 API Key입니다`, 동일 해시가 **삭제 예정** 행에 있으면 `삭제 예정키와 중복입니다`
    - `403` (`success=false`): 팀장이 아닌 경우(팀 멤버만인 경우 등)
    - `404` (`success=false`): 대상 팀이 존재하지 않는 경우

- `PUT /api/team/v1/teams/{teamId}/api-keys/{keyId}`
  - 요청 본문: `alias`, `monthlyBudgetUsd` (필수). Identity `/teams`·team `web` UI는 별칭·예산만 보낸다. 서버는 `externalKey`가 비어 있지 않을 때에만 키 값·provider 갱신을 허용한다(내부·다른 클라이언트용).
  - 권한: **팀 멤버**(팀장 아님 포함).
  - 성공: `200`, 수정된 키 요약
  - 실패: `400` (검증/중복/대상 없음, **삭제 예정인 키는 수정 불가**), `403`, `404`

- `DELETE /api/team/v1/teams/{teamId}/api-keys/{keyId}`
  - 선택 쿼리: `gracePeriodDays` (정수, 생략 시 **기본 7일**). 허용 범위는 **0~365일**.
    - **`0`**: 삭제 예정 없이 **즉시 DB에서 행을 삭제**(물리 삭제).
    - **`1` 이상**: `deletionRequestedAt`을 현재 시각으로, `permanentDeletionAt`을 그 시각 + `gracePeriodDays`일 후로 두는 **삭제 예정(소프트)**.
  - 권한: **팀장만** 호출 가능.
  - 유예 종료 후 배치로 행을 지우는 **스케줄러는 별도 구현**일 수 있다(계약만으로 보장하지 않음).
  - 성공: `200`, `message` 예: `팀 API 키 삭제 요청이 처리되었습니다`, `data`에 해당 키 요약(즉시 삭제 직전 스냅샷 또는 삭제 예정 필드 포함)
  - 실패: `400` (대상 없음·유예 일수 범위 밖·이미 삭제 예정 등), `403`, `404`

- `POST /api/team/v1/teams/{teamId}/api-keys/{keyId}/deletion/cancel`
  - 본문 없음. 삭제 예정이었던 키를 다시 활성 상태로 되돌린다(`deletionRequestedAt` / `permanentDeletionAt` / `deletionGraceDays` 해제).
  - 권한: **팀 멤버**(팀장 아님 포함).
  - 성공: `200`, `message` 예: `삭제 예정이 해제되었습니다`, `data`에 활성 키 요약
  - 실패: `400` (삭제 예정이 아님 등), `403`, `404`

- `GET /api/team/v1/teams/{id}/api-keys`
  - 성공: `200`, 팀 API Key 목록(요약만, `monthlyBudgetUsd` 포함). 삭제 예정 행은 `deletionRequestedAt`, `permanentDeletionAt`, `deletionGraceDays`가 채워진다.
  - 실패:
    - `403` (`success=false`): 팀 멤버가 아닌 사용자의 조회 시도
    - `404` (`success=false`): 대상 팀이 존재하지 않는 경우

---

## 6. 내부 API 및 이벤트 계약

### 6.1 내부 API (notification 등 서비스 간 조회)

- Team Service는 내부 호출용으로 `GET /internal/teams/{id}`를 제공한다.
- 응답 `data`는 `teamId`, `teamName`, `createdBy`, `createdAt`를 포함한다.
- Billing 연동용으로 `GET /internal/teams/users/{userId}/billing-summaries`를 제공한다.
  - 응답 목록의 각 팀 항목은 `teamId`, `teamAlias`, `monthlyBudgetUsd`, `monthlyBudgetsByKey`, `apiKeys`를 포함한다.
  - `monthlyBudgetUsd`: 팀 API 키 월 예산 합계(USD)
  - `monthlyBudgetsByKey`: 키별 월 예산 목록(`apiKeyId`, `apiKeySource`, `provider`, `alias`, `monthlyBudgetUsd`)
    - `apiKeyId`: Team API Key PK를 문자열로 직렬화한 값 (`String(teamApiKeyId)`)
    - `apiKeySource`: 항상 `"team"` (UsageRecordedEvent `apiKeySource + apiKeyId` 매칭 기준)
  - `apiKeys`: 기존 하위 호환용 키 목록(현재 `monthlyBudgetsByKey`와 동일 스냅샷)
- Notification 액션 처리용으로 `POST /internal/v1/team-invitations/{invitationId}/decision`를 제공한다.
  - 요청 본문: `inviteeUserId`(string), `decision`(`ACCEPT` | `REJECT`)
  - `decision=ACCEPT`: 초대 수락 처리(`ACCEPTED`) + 멤버 추가
  - `decision=REJECT`: 초대 거절 처리(`REJECTED`) + 멤버 미추가

### 6.2 RabbitMQ 이벤트 (`team.events`)

- 설정 정본: `services/team-service/src/main/resources/application.properties` — `team.member-added-event.exchange`, `team.member-added-event.routing-key`(기본값 `team-member-added`), `spring.rabbitmq.*`.
- **동일 TopicExchange·라우팅 키**로 팀 도메인 이벤트 JSON이 발행된다. 각 메시지 본문에 **`eventType`**(필수)과 공통 필드(`teamId`, `teamName`, `actorUserId`, `occurredAt`, `recipientUserIds` 등)가 포함되며, AMQP **헤더 `eventType`**에도 동일 문자열이 실린다. 구독자는 헤더 또는 본문 `eventType`으로 분기한다.
- 주요 `eventType` 값: `TEAM_CREATED`, `TEAM_INVITE_CREATED`, `TEAM_INVITATION_ACCEPTED`, `TEAM_INVITATION_REJECTED`, `TEAM_MEMBER_JOINED`, `TEAM_MEMBER_REMOVED`, `TEAM_DELETED`, `TEAM_API_KEY_REGISTERED`, `TEAM_API_KEY_UPDATED`, `TEAM_API_KEY_DELETED`, `TEAM_API_KEY_DELETION_SCHEDULED`, `TEAM_API_KEY_DELETION_CANCELLED`.
- 하위 호환: `TEAM_INVITE_CREATED` 페이로드에 기존 `invitationId`, `receiverId`, `inviterId`, `createdAt` 필드가 유지된다. `TEAM_MEMBER_JOINED`에 `receiverId`, `inviterId`, `createdAt`(레거시)가 유지된다.
- 목적: notification 등이 알림·감사 로그를 비동기로 처리할 수 있도록 전달
- **소비(인앱):** **notification-service**(`services/notification-service/src/team-events/`)가 RabbitMQ 큐를 구독해 위 이벤트별로 `InAppNotification`을 생성하고, `NotificationDelivery.dedupeKey`로 멱등을 보장한다. `TEAM_MEMBER_JOINED`는 제품 규칙상 **참여 사용자(`receiverId`)에게만** 인앱을 생성한다(초대자는 `TEAM_INVITATION_ACCEPTED`로 별도 통지). 상세·환경 변수는 [`services/notification-service/README.md`](../../services/notification-service/README.md), 아키텍처 요약은 [`architecture.md`](../architecture.md) §4.9·§6.

---

## 7. Identity `web` `/teams` 화면·데모 순서 (현행 구현)

구현 정본: `services/identity-service/web/src/components/account/teams-view.tsx`, `services/identity-service/web/src/app/teams/[[...path]]/page.tsx`(브라우저 경로 **`/teams`**). 팀 백엔드: `services/team-service/`.

### 7.1 내비게이션

- 좌측 대시보드 셸에서 **`팀`** → 브라우저 경로 **`/teams`** (Identity `web`이 페이지를 소유한다).
- **`조직`** (`/organizations`)과는 **별도** 상위 메뉴이며, **「조직 설정 > 팀 관리」** 같은 중첩 메뉴는 없다.
- 조직 맥락 뱃지·우측 상단 **「+ 새 팀 추가」** 전용 버튼·중앙 **데이터 테이블** 형태의 팀 그리드는 **본 저장소 UI에 없다** (데모 시나리오 작성 시 혼동하지 말 것).

### 7.2 팀 만들기

1. **`팀 만들기`**를 누르면 같은 카드 영역에 **인라인 폼**이 열린다(별도 중앙 모달 전용 컴포넌트가 아니다).
2. **팀 이름 (필수)** 입력.
3. **팀원 초대 (선택)** — 이메일(아이디) 입력란을 여러 개 둘 수 있다. 비우면 생성자만 멤버가 된다.
4. **`생성`** — `POST /api/team/v1/teams` 본문은 **`{ "name": "…" }` 만** 보낸다. 생성이 성공한 뒤, 초대 이메일이 있으면 **팀별로** `POST /api/team/v1/teams/{id}/members` 를 연속 호출한다.
5. **`취소`** — 폼을 닫는다.

**팀 리더 지정 UI는 없다.** Team Service는 팀 생성 요청을 보낸 사용자를 멤버로 넣고 **OWNER** 역할을 부여한다.

### 7.3 팀 목록 표시

- 목록은 **데이터 테이블(컬럼: 리더·인원·생성일 등)** 이 아니라, **접이식(아코디언) 목록**이다.
- 접힌 행에는 **팀 이름**만 보인다.
- 행을 눌러 펼치면 팀 `id`, 멤버 수·이메일 목록, 초대 입력, 팀 API Key 등록·목록·수정·삭제 영역이 나온다. 펼친 팀에 대해서만 멤버/API Key 목록 API를 호출한다.
- `GET /api/team/v1/me/teams` 응답의 팀 요약은 **`id`, `name`** 수준이며, **리더·인원 수·생성일**을 한 줄로 주지 않는다. 검증은 **이름이 목록에 나타나는지**, 펼쳤을 때 **멤버·초대·키** 동작이 보이는지로 맞춘다.
- 팀원 초대 입력 행은 여러 개 추가할 수 있고, 2개 이상일 때 각 행 우측 `-` 버튼으로 빈 행을 즉시 제거할 수 있다.

#### 7.3.1 현재 MFE/호스트 UI 배치(2026-04)

- `services/team-service/web/src/components/team/team-management-view.tsx` 는 **좌측 팀 목록 아코디언 내부에서 CRUD**(멤버 초대·삭제, 팀 API Key 등록·수정·삭제/삭제취소, 팀 삭제)를 수행한다.
- 같은 화면의 오른쪽 패널은 **Usage 소유 슬롯 예약 영역**으로 두며, Team UI는 해당 영역을 비워 둔다(Team이 Usage 화면을 직접 수정하지 않음).
- `apps/web` 호스트는 `/teams/[id]/[section]` 경로(`dashboard|members|api-keys`)를 해석해 Team remote를 렌더한다. 현재 Team remote는 내부 탭 없이 아코디언 중심 동작으로 맞춘다.

### 7.4 팀 API Key 삭제 예정·유예·취소 (Identity `/teams`)

구현: `teams-view.tsx`. 팀장만 **`삭제`/`삭제 취소`/등록 폼**이 보인다(`GET /api/team/v1/teams/{id}/owner`). 서버 기본 유예 **7일**, `gracePeriodDays` 허용 범위 **0~365일**(`0` = 즉시 삭제).

1. **삭제** — 활성 키 행에서 **`삭제`** → 확인 대화상자 → 브라우저 **`prompt`로 유예 기간(일)** 입력(비우면 7일, **0이면 즉시 삭제**). `DELETE .../api-keys/{keyId}?gracePeriodDays=...` 호출.
2. **삭제 예정 표시** — 해당 행에 `(삭제 예정)` 안내, **영구 삭제 예정 시각**·유예 일수 표시, **`수정` 비활성**.
3. **삭제 취소** — **`삭제 취소`** → 확인 후 `POST .../api-keys/{keyId}/deletion/cancel`. 성공 시 다시 활성 키로 표시.
4. **동일 키 재등록** — 삭제 예정 중인 키와 같은 provider+키 값으로 등록 시 서버 메시지 `삭제 예정키와 중복입니다`.

### 7.5 Team `web` (`services/team-service/web/`)

- 동일 도메인 로직을 **Team 전용 Next**에서도 볼 수 있다. UI 패턴(팀 만들기·접이식 목록 등)은 `team-management-view.tsx`가 대응한다. 브라우저 진입점으로는 보통 Identity의 **`/teams`** 를 쓴다([web-split-boundary.md](./web-split-boundary.md) §2.3).
- `team-management-view`는 팀 API Key **목록·삭제 예정 안내(영구 삭제 예정 시각 등)** 를 볼 수 있으나, **삭제 예정 등록·삭제 취소** 전용 버튼은 Identity `/teams`와 다를 수 있다(필요 시 동일 API로 확장 가능).
- `team-management-view`의 초대 알림 영역은 **만료(`EXPIRED`) 초대만 표시**한다.
  - 수락/거절 버튼은 Team `web`에서 제공하지 않는다(해당 액션은 Notification 흐름 사용).
  - 문구는 `viewerRole` 기준으로 분기한다: `INVITER`는 재초대 안내, `INVITEE`는 만료 안내.
  - 항목 우측 `X` 버튼으로 현재 화면에서 개별 알림을 dismiss 할 수 있다.
