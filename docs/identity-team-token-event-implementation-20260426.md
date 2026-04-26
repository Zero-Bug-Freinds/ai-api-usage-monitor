# Identity-Team Token/Event Implementation Notes (2026-04-26)

## 1. 목적

- Identity-Team 경계에서 팀 컨텍스트 전환(`active_team_id`)과 Team API Key 상태 동기화 이벤트의 신뢰성(Outbox)을 동시에 정비한 작업 이력을 기록한다.
- 운영/테스트 시 확인할 핵심 포인트(토큰 갱신, 내부 검증, Rabbit 릴레이, 회귀 테스트)를 한 문서에 모은다.

---

## 2. 핵심 변경 요약

### 2.1 Identity: JWT/토큰 전환

- `JwtTokenProvider`에 `active_team_id` 클레임 지원 추가.
- 로그인/팀 전환 응답을 `TokenResponse(access + refresh)` 구조로 통일.
- `/api/auth/switch-team` + `/api/auth/token/switch-team`에서 팀 전환 후 토큰 pair 재발급.
- 팀 전환 시 기존 refresh 토큰 무효화 후 신규 refresh 토큰 저장(DB).
- (추가 정리) Identity 인증 경계는 Gateway로 이관하여 `JwtAuthenticationFilter`를 제거했고, 서비스 내부 요청 인증은 `X-User-Id` 헤더 기반(`GatewayHeaderInterceptor`)으로 전환.

### 2.2 Team: 멤버십 내부 검증 API

- `GET /internal/v1/teams/{teamId}/members/{userId}/verify` 추가.
- 응답 DTO: `InternalTeamMembershipVerifyResponse` (`isValid` 포함).

### 2.3 Identity: TEAM_MEMBER_REMOVED 대응

- Rabbit 리스너 추가로 `TEAM_MEMBER_REMOVED` 이벤트 수신.
- 해당 사용자 refresh 토큰 삭제(세션 재발급 차단).
- 보안 감사 로그(`warn`) 기록.

### 2.4 Team: Team API Key 상태 이벤트 + Outbox

- `TeamApiKeyStatusChangedEvent` 계약 DTO 반영(`schemaVersion`, `eventId`, `status`, `retainLogs` 포함).
- `TeamApiKeyService`의 등록/수정/삭제 성공 직후 `ApplicationEventPublisher`로 상태 이벤트 발행.
- `TeamEventOutbox`/`TeamEventOutboxRepository` 추가.
- `@TransactionalEventListener(AFTER_COMMIT)` 기반 릴레이:
  - Outbox 선저장
  - Rabbit 발송
  - 성공 시 `published=true`
  - 실패 시 `published=false` 유지(재시도 대상)

### 2.5 Frontend(team-web): 팀 전환 후 토큰 즉시 갱신

- `POST /api/auth/token/switch-team` BFF 라우트 추가.
- Identity switch-team 성공 시 새 `access_token` 쿠키 즉시 갱신.
- 팀 선택 UI(`team-management-view.tsx`)에서 팀 전환 API 호출 연결.
- Team BFF의 `/api/team/v1/*` 업스트림을 Team Service 직결이 아닌 Gateway 경유로 전환(`GATEWAY_URL`).

---

## 3. 상세 변경 파일

### 3.1 Identity 서비스

- `services/identity-service/src/main/java/com/zerobugfreinds/identity_service/security/JwtTokenProvider.java`
- `services/identity-service/src/main/java/com/zerobugfreinds/identity_service/controller/AuthController.java`
- `services/identity-service/src/main/java/com/zerobugfreinds/identity_service/service/UserService.java`
- `services/identity-service/src/main/java/com/zerobugfreinds/identity_service/service/TeamMembershipVerificationClient.java`
- `services/identity-service/src/main/java/com/zerobugfreinds/identity_service/service/RefreshTokenRevocationService.java`
- `services/identity-service/src/main/java/com/zerobugfreinds/identity_service/mq/TeamMemberRemovedEventListener.java`
- `services/identity-service/src/main/java/com/zerobugfreinds/identity_service/config/TeamMemberRemovedRabbitConfig.java`
- `services/identity-service/src/main/java/com/zerobugfreinds/identity_service/entity/RefreshTokenEntity.java`
- `services/identity-service/src/main/java/com/zerobugfreinds/identity_service/repository/RefreshTokenRepository.java`
- `services/identity-service/src/main/java/com/zerobugfreinds/identity_service/dto/TokenResponse.java`
- `services/identity-service/src/main/java/com/zerobugfreinds/identity_service/dto/SwitchTeamRequest.java`
- `services/identity-service/src/main/resources/application.properties`
- `services/identity-service/src/test/java/com/zerobugfreinds/identity_service/controller/AuthSwitchTeamE2ETest.java`
- `services/identity-service/build.gradle` (test 의존성 추가)

### 3.2 Team 서비스

- `services/team-service/src/main/java/com/zerobugfreinds/team_service/controller/InternalTeamController.java`
- `services/team-service/src/main/java/com/zerobugfreinds/team_service/service/TeamService.java`
- `services/team-service/src/main/java/com/zerobugfreinds/team_service/dto/InternalTeamMembershipVerifyResponse.java`
- `services/team-service/src/main/java/com/zerobugfreinds/team_service/event/TeamDomainOutboundEvent.java`
- `services/team-service/src/main/java/com/zerobugfreinds/team_service/event/TeamApiKeyStatus.java`
- `services/team-service/src/main/java/com/zerobugfreinds/team_service/event/TeamEventTypes.java`
- `services/team-service/src/main/java/com/zerobugfreinds/team_service/service/TeamApiKeyService.java`
- `services/team-service/src/main/java/com/zerobugfreinds/team_service/entity/TeamEventOutbox.java`
- `services/team-service/src/main/java/com/zerobugfreinds/team_service/repository/TeamEventOutboxRepository.java`
- `services/team-service/src/main/java/com/zerobugfreinds/team_service/mq/TeamApiKeyStatusChangedEventRelay.java`
- `services/team-service/src/main/java/com/zerobugfreinds/team_service/config/TeamApiKeyStatusEventRabbitConstants.java`
- `services/team-service/src/main/resources/application.properties`

### 3.3 Team Web(BFF/화면)

- `services/team-service/web/src/app/api/auth/token/switch-team/route.ts`
- `services/team-service/web/src/components/team/team-management-view.tsx`

### 3.4 계약 문서

- `docs/contracts/team-api-key-event-contract.md`

---

## 4. API/이벤트 계약 포인트

### 4.1 Switch Team API

- 엔드포인트:
  - `POST /api/auth/switch-team`
  - `POST /api/auth/token/switch-team` (호환 경로)
- 요청: `targetTeamId`
- 처리:
  - Gateway가 주입한 `X-User-Id`를 인증 사용자로 신뢰
  - team-service 내부 API로 멤버십 검증
  - 성공 시 새 access/refresh 재발급

### 4.2 Team API Key 상태 동기화 이벤트

- 이벤트 타입: `TEAM_API_KEY_STATUS_CHANGED`
- 핵심 필드:
  - `schemaVersion`, `eventId`, `eventType`, `occurredAt`
  - `teamId`, `teamApiKeyId`, `alias`, `provider`, `status`, `retainLogs`
- 라우팅:
  - exchange: `team.api-key.exchange`
  - routingKey: `team.api-key.status.changed`

---

## 5. 테스트/검증

### 5.1 수행된 검증

- `identity-service` 컴파일 성공
- `team-service` 컴파일 성공
- `AuthSwitchTeamE2ETest` 실행 성공
  - 로그인 -> 팀 전환 -> JWT `active_team_id` 단언
  - 개인 external key 등록/조회 회귀 확인
- `team-service` 전체 테스트 실행 성공(`./gradlew test`)

### 5.2 참고

- `team-service/web` lint는 로컬 환경의 ESLint plugin dependency(`semver`) 누락으로 실행 실패 가능성이 확인되었다.
- 이는 기능 코드 자체와 별개로 개발 환경 의존성 상태 이슈다.

---

## 6. 운영 관점 체크리스트

- `identity.team-service.internal-base-url`가 환경에 맞게 설정되어 있는지 확인.
- team 이벤트 구독 큐/라우팅 설정(`identity.team-member-removed.*`)이 배포 환경과 일치하는지 확인.
- outbox 재시도 스케줄러 도입 전에는 `published=false` 레코드 모니터링이 필요.
- 게이트웨이에서 `active_team_id -> X-Team-Id` 헤더 주입 로직이 배포 브랜치에 반영되어야 최종 전파가 완성된다.
- Team Service는 JWT 서명 검증을 수행하지 않으므로, 게이트웨이 구간에서 `X-User-Id`/`X-Team-Id` 주입·검증 정책이 강제되어야 한다.

