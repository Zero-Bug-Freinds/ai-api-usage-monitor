# Web(Next.js) ↔ Team Service BFF 계약

버전: 0.1  
관련: [web-split-boundary.md](./web-split-boundary.md), [web-identity-bff.md](./web-identity-bff.md)

---

## 1. 목적

- `services/team-service/web/`에서 팀 생성/조회/아이디 초대를 BFF로 처리한다.
- 브라우저는 `access_token` httpOnly 쿠키를 유지하고, BFF가 Bearer로 Team Service를 호출한다.

---

## 2. 엔드포인트 계약

| 브라우저 경로 (Identity `web` 기준) | Upstream |
|---|---|
| `GET /api/team/v1/me/teams` | Team BFF `GET /api/team/v1/me/teams` → Team Service `GET /api/v1/me/teams` |
| `POST /api/team/v1/teams` | Team BFF `POST /api/team/v1/teams` → Team Service `POST /api/v1/teams` |
| `POST /api/team/v1/teams/{id}/members` | Team BFF `POST /api/team/v1/teams/{id}/members` → Team Service `POST /api/v1/teams/{id}/members` |

- Identity `web`는 `/teams` UI를 직접 렌더링하고, Next rewrite로 `GET/POST /api/team/v1/*`를 Team BFF(`team-web`)로 전달한다.
- Team BFF는 `TEAM_SERVICE_URL` 환경 변수로 Team Service를 프록시한다.
- Team BFF는 `IDENTITY_SERVICE_URL`로 세션 확인(`GET /api/auth/session`)을 프록시한다.

---

## 3. 요청/응답 공통 규칙

- `access_token` 쿠키가 없으면 BFF는 업스트림 호출 없이 `401`을 반환한다.
- 응답에는 `Cache-Control: no-store`를 적용한다.
- 성공/실패 응답은 Team Service의 `ApiResponse` 포맷을 그대로 전달한다.

---

## 4. 보안/권한

- 팀 생성/조회/초대는 모두 인증 필요다.
- 권한 검증(팀 멤버만 초대 가능 등)은 Team Service가 최종 책임을 가진다.
