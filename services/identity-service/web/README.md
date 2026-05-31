# Identity `web` (Next.js App Router)

## 브라우저 진입 (정본)

통합 콘솔·쿠키·`@ai-usage/shell` 교차 링크는 **`http://localhost:8888`** (루트 Compose **`web-edge`**) 한 오리진을 쓴다.

```bash
# 저장소 루트
docker compose --profile web up -d
# 브라우저: http://localhost:8888  (로그인 · /settings · /dashboard 링크 등)
```

Identity `web` 호스트 노출 포트(`IDENTITY_WEB_PORT`, 기본 3000)는 Compose **upstream 포트**일 뿐, 통합 진입점이 아니다.

## 호스트 단독 dev (선택)

```bash
# 저장소 루트
pnpm install
pnpm --filter identity-web dev
```

단독 dev 는 UI/BFF 디버그용이다. Usage·알림 등 다른 앱과 쿠키를 공유하려면 **8888 web-edge** 를 쓴다.

환경 변수: `.env.example`. 계약: `docs/contracts/web-identity-bff.md`, `docs/contracts/web-split-boundary.md` §4.
