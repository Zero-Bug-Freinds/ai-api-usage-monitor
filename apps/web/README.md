# web-host (`apps/web`)

선택적 **Next.js App Router 셸**이다. **운영 단일 도메인 진입은 `services/team-service/web`(team-web, `/teams`)** 이며, usage/team **Module Federation·`/mfe/usage` 경로는 제거**되었다.

- **도메인별 BFF·운영 UI**는 여전히 `services/<svc>/web/`(App Router)가 담당한다.
- **경계·rewrite·엣지:** [`docs/contracts/web-split-boundary.md`](../../docs/contracts/web-split-boundary.md), [`docs/architecture.md`](../../docs/architecture.md) §13.3
- 로컬: 루트에서 `pnpm --filter web-host dev`.

인증·Identity BFF·Usage 대시보드 본편은 **`services/identity-service/web/`**, **`services/usage-service/web/`** 에 있다.
