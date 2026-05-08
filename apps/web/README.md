# web-host (`apps/web`)

팀 콘솔 진입점으로 사용되는 **독립형 Next.js App Router 셸**이다.

- **도메인별 BFF·운영 UI**는 여전히 `services/<svc>/web/`(App Router)가 담당한다.
- **경계·rewrite·엣지:** [`docs/contracts/web-split-boundary.md`](../../docs/contracts/web-split-boundary.md), [`docs/architecture.md`](../../docs/architecture.md) §13.3
- 로컬: 루트에서 `pnpm --filter web-host dev`.

인증·Identity BFF·Usage 대시보드 본편은 **`services/identity-service/web/`**, **`services/usage-service/web/`** 에 있다.
