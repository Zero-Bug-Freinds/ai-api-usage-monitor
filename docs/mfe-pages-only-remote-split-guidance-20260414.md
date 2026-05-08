# Module Federation — Pages 전용 remote(`web-mfe`) 분리 가이드

**상태:** 팀 정본(2026-04-14 기준 구조와 동기화). 관련: [`docs/architecture.md`](architecture.md) §13.3, [`docs/contracts/web-split-boundary.md`](contracts/web-split-boundary.md) §2.6, [`docs/repository-structure.md`](repository-structure.md) §6.

---

## 1. 목적

- **Next.js 15**·**React 19** 모노레포에서 **App Router** 기반 `web/`(BFF·운영 UI)과 **Module Federation remote** 요구를 동시에 만족시키기 위해, **Usage·Team** 만 **`web-mfe/`**(Pages Router)를 추가로 둔다.
- **호스트**는 주로 **`apps/web`**(`web-host`)가 `remotes`로 `usage`·`team` remoteEntry를 로드한다.
- **브라우저 단일 오리진**으로 여러 도메인 UI를 붙이는 경로 통합은 Nginx **`web-edge`** 가 담당한다. MF 전용 포트는 개발 시 호스트·remote가 직접 맞춘다.

---

## 2. 디렉터리 구조(정본)

| 역할 | 경로 | 라우터 | 비고 |
|------|------|--------|------|
| Usage 운영·BFF | `services/usage-service/web/` | App Router | `basePath=/dashboard`, Usage BFF |
| Usage MF remote | `services/usage-service/web-mfe/` | Pages Router | `NextFederationPlugin` `exposes` 만 |
| Team 운영·BFF | `services/team-service/web/` | App Router | `basePath=/teams` |
| Team MF remote | `services/team-service/web-mfe/` | Pages Router | 동일 |
| MF 호스트 | `apps/web/` | App Router + webpack MF | `remotes` → 위 remoteEntry URL |

Identity **`web/`** 에는 MF 플러그인을 두지 않고, 랜딩·인증·계정 BFF 역할만 유지하는 것을 권장한다.

---

## 3. 컴포넌트 중복 방지 — `@web/*` 별칭

- **`web-mfe`** 에서 동일 도메인의 **`web/`** 구현을 가져와 쓸 때는 **상대 경로 복제 대신** TypeScript·번들 별칭 **`@web/*` → `../web/src/*`** 를 사용한다(`tsconfig.json` `paths`).
- 공통 **사이드바·헤더·콘솔 네비**는 **`@ai-usage/shell`**, 프리미티브는 **`@ai-usage/ui`** 를 우선한다([`repository-structure.md`](repository-structure.md) §6).

---

## 4. `web-edge`와의 관계

- **페이지·BFF 경로**(`/dashboard`, `/billing`, `/notifications`, `/api/team/v1/...` 등)를 서비스별 `web`으로 넘기는 것은 **`docker/web-edge/nginx.conf.template`** 정본이다. 새 라우트를 “같은 오리진에서 다른 서비스로” 노출할 때는 **반드시 여기에 추가**하고 [`web-split-boundary.md`](contracts/web-split-boundary.md)를 갱신한다.
- **MF remote**(`/_next/static/chunks/remoteEntry.js` 등)는 기본적으로 `web-edge`의 `/mfe/team/*`, `/mfe/usage/*` 경로로 노출한다.

---

## 5. 로컬 개발 힌트

- 호스트: `pnpm --filter web-host dev`(기본 포트 `3100` 등 `package.json` 정본).
- Usage remote: `pnpm --filter usage-web-mfe dev` — 포트는 `usage-service/web-mfe/package.json`의 `dev` 스크립트 정본.
- Team remote: `pnpm --filter team-web-mfe dev`.
- 환경 변수: `NEXT_PUBLIC_MFE_USAGE_REMOTE_URL`, `NEXT_PUBLIC_MFE_TEAM_REMOTE_URL` 등(호스트 `next.config` 기본값과 맞출 것).

---

## 6. Docker·CI

- **`web-mfe`** 용 `Dockerfile`이 있으면 빌드 컨텍스트는 저장소 루트·`pnpm-workspace.yaml` 포함 원칙을 `web/` 과 동일하게 따른다.
- CI(`.github/workflows/ci.yml`)의 **web** 잡이 `web-mfe`를 아직 독립 빌드하지 않으면, 로컬에서만 검증하거나 후속 PR에서 필터·`docker build` 단계를 추가한다.

---

## 7. 변경 시 체크리스트

1. `web-mfe`에 expose 추가 시 호스트 `remotes`·공유 `react`/`react-dom` 버전 정합.
2. 새 브라우저 공개 경로가 다른 서비스 `web`으로 가야 하면 **`web-edge`** 라우팅을 수정.
3. 문서: 본 파일, `architecture.md` §13.3, `web-split-boundary.md`, 필요 시 `c4-architecture-diagrams.md` W 절.
