# team-web

팀 콘솔(Next Pages Router, `basePath=/teams`). BFF·계약: `docs/contracts/web-team-bff.md`. **usage/team `web-mfe`·`/mfe/usage` 는 사용하지 않는다** — UI·BFF는 이 디렉터리만 본다.

## `@ai-usage/shell` 동기화

`packages/shell` 을 수정한 뒤 team-web 에 반영이 안 되면(옛 사이드바 링크·캐시):

```bash
# 저장소 루트
pnpm refresh:team-web-shell
pnpm --filter team-web dev
```

또는 team-web 디렉터리에서:

```bash
pnpm refresh:shell && pnpm dev
```

- **로컬:** `next.config.ts` 가 `@ai-usage/shell` 을 `packages/shell/src` 로 직접 alias 해 node_modules 복사본을 우회한다.
- **Docker:** `docker compose --profile web build --no-cache team-web` 후 `up` (이미지에 이전 `.next` 번들이 남을 때).

## 배포(staging/production)

- 사이드바 링크는 **Release 빌드 시** GitHub Environment `NEXT_PUBLIC_WEB_EDGE_ORIGIN` / `NEXT_PUBLIC_IDENTITY_WEB_ORIGIN` 이 이미지에 박힌다. 로컬 `.env` 의 `localhost:8888` 로 ECR 이미지를 만들면 안 된다.
- 이미지에 localhost가 박혀 있어도 `@ai-usage/shell` 은 **브라우저가 열린 배포 origin**(ALB DNS 등)으로 교차 링크를 맞춘다.
- 배포 후: `terraform output alb_dns_name` → GitHub vars 갱신 → **team-web Release 재빌드** → compose roll.

로컬 통합 진입만 **web-edge `:8888`** (`http://localhost:8888/teams`). `:3012` 단독 포트는 upstream 확인용이다.
