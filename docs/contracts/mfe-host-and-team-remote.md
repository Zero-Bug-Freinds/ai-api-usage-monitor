# Module Federation Host (`apps/web`) · Team Remote 가이드

## 역할

- **`apps/web` (web-host, 포트 3100):** `NextFederationPlugin` **Host**. Remotes: `team`, `usage`.
- **`services/team-service/web` (team-web, 포트 3002, `basePath` `/teams`):** **Remote** `team`. 기존 도메인 UI는 수정하지 않고, MF용 **thin entry**만 추가한다.
- **`services/usage-service/web` (usage-web, 포트 3001, `basePath` `/dashboard`):** **Remote** `usage`, `./TeamUsageDashboard` expose.

로컬에서 Host가 Remotes를 불러오려면 **team-web·usage-web dev 서버를 각각 기동**한 뒤 Host를 띄운다.

## Remote URL (개발 기본값)

| Remote | `name` | 기본 origin (끝 슬래시 없음) | `remoteEntry` 경로 |
|--------|--------|------------------------------|-------------------|
| team | `team` | `http://localhost:3002/teams` | `/_next/static/chunks/remoteEntry.js` |
| usage | `usage` | `http://localhost:3001/dashboard` | `/_next/static/chunks/remoteEntry.js` |

전체 URL 예: `http://localhost:3002/teams/_next/static/chunks/remoteEntry.js`

Host는 `apps/web/.env.local` 등으로 다음을 바꿀 수 있다.

- `NEXT_PUBLIC_MFE_TEAM_REMOTE_URL` — team Remote 베이스 (기본 `http://localhost:3002/teams`)
- `NEXT_PUBLIC_MFE_USAGE_REMOTE_URL` — usage Remote 베이스 (기본 `http://localhost:3001/dashboard`)

## team-service (web) 담당자 작업 — `TeamRegister` / `TeamList` / `TeamInfo` 분리 expose

현재는 `./TeamManagement` 하나만 노출한다 (`team-management-entry.tsx`가 `TeamManagementView`를 re-export).

와이어프레임처럼 **컴포넌트를 쪼개 노출**하려면:

1. **도메인 로직이 들어 있는 파일(`team-management-view.tsx` 등)은 가능하면 건드리지 않는다.**
2. `src/components/mf/` 아래에 **래퍼만** 추가한다. 예:
   - `team-register-entry.tsx` → 기존 내부 컴포넌트를 import해 default export (또는 팀에서 분리한 프레젠테이션 컴포넌트).
3. `services/team-service/web/next.config.ts` 의 `NextFederationPlugin` `exposes`에 경로를 추가한다. 예:
   - `"./TeamRegister": "./src/components/mf/team-register-entry.tsx"`
4. Host `apps/web/next.config.mjs`에는 변경이 필요 없다(같은 Remote 이름 `team` 안에 모듈만 늘어남).
5. Host 소비 측 `dynamic(() => import('team/TeamRegister'))` 등으로 불러오면 된다.

`remoteEntry` 캐시 이슈가 있으면 team-web을 재시작하거나 브라우저 강력 새로고침을 한다.

## 빌드·실행 참고

- Next.js 16에서는 MF 사용 시 **`next dev` / `next build`에 `--webpack`** 과 루트/패키지의 **`NEXT_PRIVATE_LOCAL_WEBPACK=true`** 를 맞춘다(각 `web` 패키지 `package.json` 스크립트 참고).
- `@module-federation/nextjs-mf`의 peer는 Next 15까지로 표기되어 있을 수 있으나, 본 저장소는 Next 16에서 동작을 검증한다. 업스트림 지원 변화 시 이 문서를 갱신한다.
