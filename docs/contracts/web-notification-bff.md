# Web(Next.js) ↔ Notification Service — Notification BFF 계약

버전: 1.3  
관련: [web-split-boundary.md](./web-split-boundary.md), [web-identity-bff.md](./web-identity-bff.md)(세션), [`docker/web-edge/nginx.conf`](../../docker/web-edge/nginx.conf), [architecture.md](../architecture.md) §4.9·§6·§10.2·§13, [web-team-bff.md](./web-team-bff.md) §6.2(팀 도메인 이벤트 스키마), [notification-service-gateway-integration-guide.md](../notification-service-gateway-integration-guide.md) §2(게이트웨이 신뢰 헤더)

**소스 트리:** Notification `web`(UI+BFF)의 **정본**은 `services/notification-service/web/` 이다. Notification 백엔드(Nest+Prisma)는 `services/notification-service/` 이다.

---

## 1. 목적

- 브라우저는 **동일 오리진**의 Next.js Route Handler(BFF)만 호출한다.
- BFF는 `access_token` **httpOnly 쿠키**를 읽어 `Authorization: Bearer` 로 업스트림에 전달한다.
- **프로덕션 경로:** BFF는 **API Gateway**의 `/api/notification/**` 로 프록시한다. Gateway가 JWT를 검증한 뒤 Nest에 `X-User-Id` 등 신뢰 헤더를 주입한다(정본: 가이드 §2).
- **로컬 direct 경로:** Gateway 없이 Nest만 둘 때는 `NOTIFICATION_HTTP_UPSTREAM=direct` 로 두고, BFF가 동일 쿠키의 액세스 JWT 페이로드에서 **JWT `sub`(이메일)** 을 추출해 `X-User-Id`로 전달한다(서명 검증은 Gateway 책임과 동일 키 전제 하에 로컬 한정).

---

## 2. 브라우저 경로(단일 도메인)

Notification `web`은 Next `basePath=/notifications`를 사용한다.

| 구분 | 브라우저 경로 |
|------|--------------|
| UI | `/notifications/*` |
| Notification BFF | `/notifications/api/notification/*` |

엣지(Nginx)는 `/notifications` → `308 /notifications/`, `/notifications/` 접두만 notification-web으로 프록시한다(정본: `docker/web-edge/nginx.conf`).

---

## 3. 환경 변수 (Notification `web/`)

| 변수 | 용도 |
|------|------|
| `NOTIFICATION_HTTP_UPSTREAM` | `gateway` \| `direct`. 기본(미설정)은 `direct`. `gateway`일 때는 `API_GATEWAY_URL` 필수. |
| `API_GATEWAY_URL` | Gateway 베이스 URL(트레일링 슬래시 없음). `gateway` 모드에서 BFF가 `{base}/api/notification/{segments...}` 로 프록시할 때 사용. |
| `NOTIFICATION_SERVICE_URL` | `direct` 모드에서 Nest 베이스 URL(예: `http://localhost:8096/api`). |
| `NOTIFICATION_INTERNAL_SECRET` | (선택) 과거에는 BFF가 Nest로 넘겼으나, **Gateway 경유 사용자 호출**에서는 전달하지 않는다. 서버 간 내부 호출에서만 사용. |
| `NEXT_PUBLIC_NOTIFICATION_POLL_MS` | (선택) UI 폴링 주기(ms). 기본값은 앱 설정에 따름 |

구현 정본: `services/notification-service/web/src/app/api/notification/[[...path]]/route.ts` 및 `notification-bff-proxy.ts`.

---

## 4. BFF 프록시 규칙

### 4.1 인증 규칙

1. 브라우저 요청에 `access_token` httpOnly 쿠키가 없으면 BFF는 `401`로 응답한다.
2. 쿠키가 있으면 업스트림 요청에 `Authorization: Bearer {access_token}` 을 설정한다.
3. **`direct` 모드:** JWT 페이로드에 `sub` 클레임이 없으면 `401`로 응답한다.
4. **`gateway` 모드:** Identity `GET /api/auth/session` 을 호출하지 않는다. `X-User-Id`는 클라이언트가 보낸 값을 신뢰·전달하지 않는다(오염 방지).

### 4.2 업스트림 전달 헤더

- 필수: `Authorization: Bearer {access_token}`
- **`direct` 모드:** `X-User-Id: {JWT sub(이메일)}` (문자열)
- **`gateway` 모드:** `X-User-Id` / 팀·스코프 헤더는 **Gateway가** Nest로 주입한다. BFF는 Bearer만 붙인다.
- 선택: 인바운드 `X-Correlation-Id`가 있으면 동일 값 전달

### 4.3 경로 매핑

브라우저 catch-all 세그먼트를 업스트림에 그대로 이어 붙인다.

- 브라우저: `/{basePath}/api/notification/{segments...}{?query}`
- **`gateway`:** `{API_GATEWAY_URL}/api/notification/{segments...}{?query}`
- **`direct`:** `{NOTIFICATION_SERVICE_URL}/{segments...}{?query}`

예:

- 브라우저 `GET /notifications/api/notification/in-app-notifications?limit=20`
- Gateway 모드: `GET {API_GATEWAY_URL}/api/notification/in-app-notifications?limit=20`
- Direct 모드: `GET {NOTIFICATION_SERVICE_URL}/in-app-notifications?limit=20`

추가 예(미확인 개수; 알림 배지):

- 브라우저 `GET /notifications/api/notification/in-app-notifications/unread-count`
- Gateway 모드: `GET {API_GATEWAY_URL}/api/notification/in-app-notifications/unread-count`
- Direct 모드: `GET {NOTIFICATION_SERVICE_URL}/in-app-notifications/unread-count`
- 응답: `{ "unreadCount": number }`

### 4.4 인앱 알림 meta(JSON) 계약

- `in-app-notifications` 목록 API의 각 아이템에는 `meta`가 포함될 수 있다(없으면 `null` 또는 누락).
- UI는 `type`별로 `meta`를 해석해 추가 UI/액션을 렌더링할 수 있다.

#### 4.4.1 Team 초대 알림 (`type = team:TEAM_INVITE_CREATED`)

- `meta` 예시(형태; 필드 추가는 하위 호환 유지):

```json
{
  "invitationId": "uuid-or-string",
  "teamId": "string-or-number",
  "teamName": "Team A",
  "actions": {
    "acceptPath": "/team-invitations/<invitationId>/accept",
    "rejectPath": "/team-invitations/<invitationId>/reject"
  }
}
```

- `actions.*Path`는 **notification-service API 베이스(`/api`) 기준의 상대 경로**로 저장된다.
- Notification `web` UI는 BFF 프록시 경로(`/notifications/api/notification`) 뒤에 붙여 호출한다(§4.3 규칙 동일).
  - 예: `POST /notifications/api/notification/team-invitations/<invitationId>/accept`

### 4.5 팀 초대 수락/거절 액션 API (Notification → Team 내부 연동)

팀 초대 알림에서 “수락/거절” 버튼은 Notification Service의 액션 엔드포인트를 호출한다.

- **엔드포인트**
  - `POST /team-invitations/{invitationId}/accept`
  - `POST /team-invitations/{invitationId}/reject`

- **브라우저(BFF) 경로 예**
  - `POST /notifications/api/notification/team-invitations/{invitationId}/accept`
  - `POST /notifications/api/notification/team-invitations/{invitationId}/reject`

- **인증/헤더**
  - 동일하게 `access_token` 쿠키 → BFF가 `Authorization: Bearer`로 업스트림에 전달한다(§4.1).
  - `direct` 모드에서는 `X-User-Id`가 JWT `sub` 기반으로 업스트림에 전달된다(§4.2).
  - `gateway` 모드에서는 `X-User-Id` 등은 Gateway가 주입하는 모델을 따른다(가이드 §2).

### 4.6 캐시 정책

- BFF 응답에는 `Cache-Control: no-store`를 강제한다.

---

## 5. 유지보수 규칙

- Notification 백엔드(Nest) 엔드포인트/헤더 계약을 바꾸면 **본 문서**와 `web-split-boundary.md`를 함께 갱신한다.
- 단일 도메인 라우팅 규칙 변경(접두 추가/변경)은 `docker/web-edge/nginx.conf`와 `docs/architecture.md` §10.2를 함께 갱신한다.

---

## 6. 인앱 데이터의 다른 유입 경로(비동기)

- 본 문서 §1~§4는 **브라우저 → BFF → (Gateway 또는 Nest) HTTP** 경로만 다룬다.
- **팀 도메인 이벤트**는 team-service가 RabbitMQ로 발행하고, notification-service가 **별도 소비자**로 큐에서 받아 `InAppNotification`을 추가한다(페이로드·`eventType` 정본은 [web-team-bff.md](./web-team-bff.md) §6.2). UI는 동일 `in-app-notifications` API로 목록을 조회하면 된다.
- 아키텍처 요약: [`architecture.md`](../architecture.md) §4.9·§6.
