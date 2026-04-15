# web-host (`apps/web`)

**Module Federation 호스트** — `usage-service`·`team-service`의 **`web-mfe/`** remote(`remoteEntry.js`)를 붙여 통합 UI를 실험·제공할 때 사용한다.

- **도메인별 BFF·운영 UI**는 여전히 `services/<svc>/web/`(App Router)가 담당한다.
- **경계·rewrite·엣지:** [`docs/contracts/web-split-boundary.md`](../../docs/contracts/web-split-boundary.md), [`docs/architecture.md`](../../docs/architecture.md) §13.3
- **MF 분리 정본:** [`docs/mfe-pages-only-remote-split-guidance-20260414.md`](../../docs/mfe-pages-only-remote-split-guidance-20260414.md)
- 로컬: 루트에서 `pnpm --filter web-host dev` — remote URL은 `NEXT_PUBLIC_MFE_*` 환경 변수(`next.config.mjs` 기본값 참고).

인증·Identity BFF·Usage 대시보드 본편은 **`services/identity-service/web/`**, **`services/usage-service/web/`** 에 있다.
