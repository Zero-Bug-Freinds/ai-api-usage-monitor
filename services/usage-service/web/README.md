# Usage `web` (Next.js App Router, `basePath=/dashboard`)

## 브라우저 진입 (정본)

**`http://localhost:8888/dashboard`** (루트 Compose **`web-edge`**). 사이드바에서 알림·지출 등으로 이동할 때도 **8888 오리진**을 전제한다 (`packages/shell`, `NEXT_PUBLIC_WEB_EDGE_ORIGIN=http://localhost:8888`).

```bash
# 저장소 루트
docker compose --profile web up -d
```

`http://localhost:3001` 은 Compose upstream 포트이며, 통합 진입점이 아니다.

## 호스트 단독 dev (선택)

```bash
pnpm install
pnpm --filter usage-web dev
```

단독 dev 시 포트·`basePath` 는 `package.json` / `.env` 를 본다. 통합 스택 검증은 **8888** 을 사용한다.

환경 변수: `.env.example`. 계약: `docs/contracts/web-gateway-bff.md`, `docs/contracts/web-split-boundary.md` §4.
