# Web(Next.js) ↔ Notification Service — Notification BFF 계약

버전: 1.0  
관련: [web-split-boundary.md](./web-split-boundary.md), [web-identity-bff.md](./web-identity-bff.md)(세션), [`docker/web-edge/nginx.conf`](../../docker/web-edge/nginx.conf), [architecture.md](../architecture.md) §4.9·§10.2·§13

**소스 트리:** Notification `web`(UI+BFF)의 **정본**은 `services/notification-service/web/` 이다. Notification 백엔드(Nest+Prisma)는 `services/notification-service/` 이다.

---

## 1. 목적

- 브라우저는 **동일 오리진**의 Next.js Route Handler(BFF)만 호출한다.
- BFF는 Identity의 세션 확인으로 호출자 식별자(`X-User-Id`)를 확보한 뒤, notification-service(Nest)로 프록시한다.
- 플랫폼 JWT(`access_token`)는 브라우저 JavaScript에 노출하지 않고 **httpOnly 쿠키**로만 관리한다.

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
| `IDENTITY_SERVICE_URL` | BFF가 Identity `GET /api/auth/session`을 호출할 때 쓰는 베이스 URL |
| `NOTIFICATION_SERVICE_URL` | BFF가 notification-service(Nest)로 프록시할 때 쓰는 베이스 URL(트레일링 슬래시 없음) |
| `NOTIFICATION_INTERNAL_SECRET` | (선택) 내부 호출 보호용. 설정 시 BFF가 `X-Notification-Internal-Secret`로 전달 |
| `NEXT_PUBLIC_NOTIFICATION_POLL_MS` | (선택) UI 폴링 주기(ms). 기본값은 앱 설정에 따름 |

---

## 4. BFF 프록시 규칙

구현 정본: `services/notification-service/web/src/app/api/notification/[[...path]]/route.ts`

### 4.1 인증(세션 확인) 규칙

1. 브라우저 요청에 `access_token` httpOnly 쿠키가 없으면 BFF는 `401`로 응답한다.
2. 쿠키가 있으면 BFF가 `GET {IDENTITY_SERVICE_URL}/api/auth/session`을 호출한다.
   - 헤더: `Authorization: Bearer {access_token}`
   - `200` 응답의 `data.email`을 **사용자 식별자**로 사용한다.
3. 세션 확인 실패(Identity 401/네트워크 실패/응답 파싱 실패 포함) 시 BFF는 `401`로 응답한다.

### 4.2 업스트림(notification-service) 전달 헤더

- 필수: `X-User-Id: {email}`
- 선택: `X-Notification-Internal-Secret: {NOTIFICATION_INTERNAL_SECRET}` (환경 변수가 비어 있지 않을 때만)
- 선택: 인바운드 `X-Correlation-Id`가 있으면 동일 값 전달

### 4.3 경로 매핑

브라우저의 catch-all 세그먼트를 동일하게 notification-service로 전달한다.

- 브라우저: `/{basePath}/api/notification/{segments...}{?query}`
- 업스트림: `{NOTIFICATION_SERVICE_URL}/{segments...}{?query}`

예:

- 브라우저 `GET /notifications/api/notification/in-app-notifications?limit=20`
- 업스트림 `GET {NOTIFICATION_SERVICE_URL}/in-app-notifications?limit=20`

### 4.4 캐시 정책

- BFF 응답에는 `Cache-Control: no-store`를 강제한다.

---

## 5. 유지보수 규칙

- Notification 백엔드(Nest) 엔드포인트/헤더 계약을 바꾸면 **본 문서**와 `web-split-boundary.md`를 함께 갱신한다.
- 단일 도메인 라우팅 규칙 변경(접두 추가/변경)은 `docker/web-edge/nginx.conf`와 `docs/architecture.md` §10.2를 함께 갱신한다.

