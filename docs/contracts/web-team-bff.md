# Web(Next.js) ↔ Team Service BFF 계약

버전: 0.1  
관련: [web-split-boundary.md](./web-split-boundary.md), [web-identity-bff.md](./web-identity-bff.md)

---

## 1. 목적

- `services/team-service/web/`에서 팀 생성/조회/아이디 초대/팀 API Key 등록·조회를 BFF로 처리한다.
- 브라우저는 `access_token` httpOnly 쿠키를 유지하고, BFF가 Bearer로 Team Service를 호출한다.

---

## 2. 엔드포인트 계약

| 브라우저 경로 (Identity `web` 기준) | Upstream |
|---|---|
| `GET /api/team/v1/me/teams` | Team BFF `GET /api/team/v1/me/teams` → Team Service `GET /api/v1/me/teams` |
| `POST /api/team/v1/teams` | Team BFF `POST /api/team/v1/teams` → Team Service `POST /api/v1/teams` |
| `POST /api/team/v1/teams/{id}/members` | Team BFF `POST /api/team/v1/teams/{id}/members` → Team Service `POST /api/v1/teams/{id}/members` |
| `GET /api/team/v1/teams/{id}/api-keys` | Team BFF `GET /api/team/v1/teams/{id}/api-keys` → Team Service `GET /api/v1/teams/{id}/api-keys` |
| `POST /api/team/v1/teams/{id}/api-keys` | Team BFF `POST /api/team/v1/teams/{id}/api-keys` → Team Service `POST /api/v1/teams/{id}/api-keys` |

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

- 팀 생성/조회/초대/팀 API Key 등록·조회는 모두 인증 필요다.
- 권한 검증(팀 멤버만 초대 가능 등)은 Team Service가 최종 책임을 가진다.
- 팀원 초대 시 Team Service는 Identity 내부 API(`GET /internal/users/exists?email=...`)로
  사용자 존재 여부를 확인한 뒤, **실제로 존재하는 아이디(이메일)만** 초대를 허용한다.
- 팀 API Key는 Team Service DB에 **평문 저장하지 않고 암호화 저장**한다.
- 팀 API Key 조회 응답에는 `keyPreview`(마스킹된 미리보기)만 제공하고, 원문 키는 반환하지 않는다.

---

## 5. 팀원 초대 검증 규칙

- 입력값 `inviteeUserId`는 가입/로그인에 사용한 이메일 형식의 아이디를 기준으로 한다.
- 존재하지 않는 아이디(이메일)이면 Team Service는 초대를 거부한다.
- Identity 연동 실패(네트워크/5xx/비정상 응답) 시 안전하게 초대를 거부한다.

### 5.1 실패 응답 예시

- `POST /api/team/v1/teams/{id}/members`
  - `400` (`success=false`): 존재하지 않는 사용자 아이디(이메일)로 초대 요청한 경우
  - `403` (`success=false`): 요청자가 해당 팀의 초대 권한이 없는 경우
  - `404` (`success=false`): 대상 팀이 존재하지 않는 경우

### 5.2 팀 API Key 등록/조회

- `POST /api/team/v1/teams/{id}/api-keys`
  - 요청 본문: `provider` (`OPENAI`/`GEMINI`/`CLAUDE`), `alias`, `externalKey`
  - 성공: `201`, `data`에 등록된 키 요약(`id`, `provider`, `alias`, `keyPreview`, `createdAt`)
  - 실패:
    - `400` (`success=false`): 필수값 누락, alias 중복, 동일 provider+key 중복
    - `403` (`success=false`): 팀 멤버가 아닌 사용자의 등록 시도
    - `404` (`success=false`): 대상 팀이 존재하지 않는 경우

- `GET /api/team/v1/teams/{id}/api-keys`
  - 성공: `200`, 팀 API Key 목록(요약 정보만 반환)
  - 실패:
    - `403` (`success=false`): 팀 멤버가 아닌 사용자의 조회 시도
    - `404` (`success=false`): 대상 팀이 존재하지 않는 경우
