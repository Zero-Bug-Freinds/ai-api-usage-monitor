# Web(Next.js) ↔ Team Service BFF 계약

버전: 0.5  
관련: [web-split-boundary.md](./web-split-boundary.md), [web-identity-bff.md](./web-identity-bff.md) — `/teams` UI 소유·경로: §2.3

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
| `DELETE /api/team/v1/teams/{teamId}/members/{userId}` | Team BFF `DELETE ...` → Team Service `DELETE /api/v1/teams/{teamId}/members/{userId}` |
| `DELETE /api/team/v1/teams/{teamId}` | Team BFF `DELETE ...` → Team Service `DELETE /api/v1/teams/{teamId}` |
| `GET /api/team/v1/me/team-invitations` | Team BFF `GET ...` → Team Service `GET /api/v1/me/team-invitations` |
| `POST /api/team/v1/me/team-invitations/{invitationId}/accept` | Team BFF `POST ...` → Team Service `POST /api/v1/me/team-invitations/{invitationId}/accept` |
| `POST /api/team/v1/me/team-invitations/{invitationId}/reject` | Team BFF `POST ...` → Team Service `POST /api/v1/me/team-invitations/{invitationId}/reject` |
| `GET /api/team/v1/teams/{id}/api-keys` | Team BFF `GET /api/team/v1/teams/{id}/api-keys` → Team Service `GET /api/v1/teams/{id}/api-keys` |
| `POST /api/team/v1/teams/{id}/api-keys` | Team BFF `POST /api/team/v1/teams/{id}/api-keys` → Team Service `POST /api/v1/teams/{id}/api-keys` |
| `PUT /api/team/v1/teams/{teamId}/api-keys/{keyId}` | Team BFF `PUT ...` → Team Service `PUT /api/v1/teams/{teamId}/api-keys/{keyId}` |
| `DELETE /api/team/v1/teams/{teamId}/api-keys/{keyId}` | Team BFF `DELETE ...` → Team Service `DELETE /api/v1/teams/{teamId}/api-keys/{keyId}` (선택 쿼리 `gracePeriodDays`) |
| `POST /api/team/v1/teams/{teamId}/api-keys/{keyId}/deletion/cancel` | Team BFF `POST ...` → Team Service `POST /api/v1/teams/{teamId}/api-keys/{keyId}/deletion/cancel` |

- Identity `web`는 `/teams` UI(팀·멤버·팀 API Key·예산)를 렌더링하고, Next rewrite로 `GET/POST/PUT/DELETE /api/team/v1/*`를 Team BFF(`team-web`)로 전달한다(삭제 예정 해제용 `POST .../deletion/cancel` 포함).
- Team BFF는 `TEAM_SERVICE_URL` 환경 변수로 Team Service를 프록시한다.
- Team BFF는 `IDENTITY_SERVICE_URL`로 세션 확인(`GET /api/auth/session`)을 프록시한다.

---

## 3. 요청/응답 공통 규칙

- `access_token` 쿠키가 없으면 BFF는 업스트림 호출 없이 `401`을 반환한다.
- 응답에는 `Cache-Control: no-store`를 적용한다.
- 성공/실패 응답은 Team Service의 `ApiResponse` 포맷을 그대로 전달한다.

---

## 4. 보안/권한

- 팀 생성/조회/초대/초대 수락·거절/팀원 삭제/팀 삭제/팀 API Key 등록·조회·수정·삭제 예정 등록·삭제 예정 해제는 모두 인증 필요다.
- 권한 검증(팀 멤버만 초대 가능, 팀장만 팀원 삭제/팀 삭제 가능 등)은 Team Service가 최종 책임을 가진다.
- 팀원 초대 시 Team Service는 Identity 내부 API(`GET /internal/users/exists?email=...`)로
  사용자 존재 여부를 확인한 뒤, **실제로 존재하는 아이디(이메일)만** 초대를 허용한다.
- 팀 API Key는 Team Service DB에 **평문 저장하지 않고 암호화 저장**한다.
- 팀 API Key 조회·등록·수정 응답에는 **원문 키를 포함하지 않는다**(미리보기 필드 없음). 저장은 DB에 암호화된다.
- 팀 삭제(`DELETE /teams/{teamId}`)는 팀 API Key가 하나라도 남아 있으면 `409`으로 거절된다.

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

- `DELETE /api/team/v1/teams/{teamId}/members/{userId}`
  - `403` (`success=false`): 요청자가 팀장이 아닌 경우(팀장 전용)
  - `400` (`success=false`): 삭제 대상 멤버가 없거나, 팀장(OWNER) 삭제 시도
  - `404` (`success=false`): 대상 팀이 존재하지 않는 경우

- `DELETE /api/team/v1/teams/{teamId}`
  - `403` (`success=false`): 요청자가 팀장이 아닌 경우(팀장 전용)
  - `409` (`success=false`): 팀 API Key가 남아 있어 삭제 선행조건 미충족
  - `404` (`success=false`): 대상 팀이 존재하지 않는 경우

### 5.2 팀 초대(PENDING)·수락/거절

- `POST /api/team/v1/teams/{id}/members` 는 멤버를 즉시 추가하지 않고 **PENDING 초대**를 생성한다.
- `GET /api/team/v1/me/team-invitations` 로 내 대기 초대 목록을 조회한다.
- `POST /api/team/v1/me/team-invitations/{invitationId}/accept` 성공 시 멤버로 추가되고 초대 상태가 `ACCEPTED`가 된다.
- `POST /api/team/v1/me/team-invitations/{invitationId}/reject` 성공 시 초대 상태가 `REJECTED`가 된다.
- 이미 처리된 초대를 다시 수락/거절하면 `400`을 반환한다.

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
    - `403` (`success=false`): 팀 멤버가 아닌 사용자의 등록 시도
    - `404` (`success=false`): 대상 팀이 존재하지 않는 경우

- `PUT /api/team/v1/teams/{teamId}/api-keys/{keyId}`
  - 요청 본문: `alias`, `monthlyBudgetUsd` (필수). Identity `/teams`·team `web` UI는 별칭·예산만 보낸다. 서버는 `externalKey`가 비어 있지 않을 때에만 키 값·provider 갱신을 허용한다(내부·다른 클라이언트용).
  - 성공: `200`, 수정된 키 요약
  - 실패: `400` (검증/중복/대상 없음, **삭제 예정인 키는 수정 불가**), `403`, `404`

- `DELETE /api/team/v1/teams/{teamId}/api-keys/{keyId}`
  - 선택 쿼리: `gracePeriodDays` (정수, 생략 시 **기본 7일**). `deletionRequestedAt`을 현재 시각으로, `permanentDeletionAt`을 그 시각 + `gracePeriodDays`일 후로 둔다. 허용 범위는 Team Service 구현상 **1~365일**.
  - 동작: **소프트 삭제(삭제 예정)**. 유예 기간이 끝난 뒤 DB에서 행을 제거하는 **배치/스케줄러는 별도 구현**일 수 있다(계약만으로 보장하지 않음).
  - 성공: `200`, `message` 예: `팀 API 키가 삭제 예정으로 등록되었습니다`, `data`에 해당 키 요약(삭제 예정 필드 포함)
  - 실패: `400` (대상 없음·유예 일수 범위 밖·이미 삭제 예정 등), `403`, `404`

- `POST /api/team/v1/teams/{teamId}/api-keys/{keyId}/deletion/cancel`
  - 본문 없음. 삭제 예정이었던 키를 다시 활성 상태로 되돌린다(`deletionRequestedAt` / `permanentDeletionAt` / `deletionGraceDays` 해제).
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

### 6.2 팀 초대 이벤트 (RabbitMQ)

- 팀 초대 생성 시 Team Service는 `team.events` 토픽 익스체인지로 초대 이벤트를 발행한다.
- 이벤트 payload 필드: `invitationId`, `receiverId`, `inviterId`, `teamId`, `teamName`, `createdAt`
- 목적: notification 서비스가 초대 알림/후속 UX를 비동기로 처리할 수 있도록 전달

---

## 7. Identity `web` `/teams` 화면·데모 순서 (현행 구현)

구현 정본: `services/identity-service/web/src/components/account/teams-view.tsx`, `services/identity-service/web/src/app/teams/[[...path]]/page.tsx`. 팀 백엔드: `services/team-service/` (`TeamService.createTeam` 등).

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

### 7.4 팀 API Key 삭제 예정·유예·취소 (Identity `/teams`)

구현: `teams-view.tsx`. 서버 기본 유예 **7일**, 요청 시 **1~365일**(`gracePeriodDays`).

1. **삭제 예정 등록** — 활성 키 행에서 **`삭제`** → 확인 대화상자 → 브라우저 **`prompt`로 유예 기간(일)** 입력(비우면 7일). `DELETE .../api-keys/{keyId}?gracePeriodDays=...` 호출.
2. **삭제 예정 표시** — 해당 행에 `(삭제 예정)` 안내, **영구 삭제 예정 시각**·유예 일수 표시, **`수정` 비활성**.
3. **삭제 취소** — **`삭제 취소`** → 확인 후 `POST .../api-keys/{keyId}/deletion/cancel`. 성공 시 다시 활성 키로 표시.
4. **동일 키 재등록** — 삭제 예정 중인 키와 같은 provider+키 값으로 등록 시 서버 메시지 `삭제 예정키와 중복입니다`.

### 7.5 Team `web` (`services/team-service/web/`)

- 동일 도메인 로직을 **Team 전용 Next**에서도 볼 수 있다. UI 패턴(팀 만들기·접이식 목록 등)은 `team-management-view.tsx`가 대응한다. 브라우저 진입점으로는 보통 Identity의 **`/teams`** 를 쓴다([web-split-boundary.md](./web-split-boundary.md) §2.3).
- `team-management-view`는 팀 API Key **목록·삭제 예정 안내(영구 삭제 예정 시각 등)** 를 볼 수 있으나, **삭제 예정 등록·삭제 취소** 전용 버튼은 Identity `/teams`와 다를 수 있다(필요 시 동일 API로 확장 가능).
