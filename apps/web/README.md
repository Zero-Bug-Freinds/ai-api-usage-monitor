# `apps/web` — Module Federation Host (Main Shell)

Identity·Usage·Team 등 분리된 Next 앱과 병행하는 **통합 쉘(Host)** 입니다. `team`·`usage` Remotes를 불러 `/team` 등에서 조립합니다.

## 로컬 실행 순서

저장소 루트에서 **`pnpm install`** 후:

1. **team-web** (Remote): `pnpm --filter team-web dev` → 기본 `http://localhost:3002/teams`
2. **usage-web** (Remote): `pnpm --filter usage-web dev` → 기본 `http://localhost:3001/dashboard`
3. **web-host** (Host): `pnpm dev:web-host` 또는 `pnpm --filter web-host dev` → `http://localhost:3100`

`web-host`는 pnpm이 로컬 `node_modules/.bin`을 만들지 않는 경우에도 동작하도록 [`scripts/run-next.cjs`](./scripts/run-next.cjs)로 Next CLI를 해석합니다.

브라우저에서 `http://localhost:3100/team` 을 연다.

## 설정

- [`docs/contracts/mfe-host-and-team-remote.md`](../../docs/contracts/mfe-host-and-team-remote.md) — Remote URL, team 담당자용 exposes 가이드
- 환경 변수 예시: [`.env.example`](./.env.example)

## 경계

- 단일 도메인·BFF·rewrite 정본은 기존 [`docs/contracts/web-split-boundary.md`](../../docs/contracts/web-split-boundary.md) 를 따른다. Host는 **별도 포트**에서 Remotes를 직접 로드하는 로컬 개발용이다.
