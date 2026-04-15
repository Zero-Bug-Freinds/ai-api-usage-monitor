# Team Service 개발자 가이드 (web ↔ web-mfe ↔ host)

관련 문서: `docs/architecture.md`, `docs/contracts/web-split-boundary.md`, `docs/mfe-pages-only-remote-split-guidance-20260414.md`

---

## 1) 기본 원칙: 구현 소스 단일화 (Single Source of Truth)

- Team 도메인의 **UI 로직/상태/기능 구현은 `services/team-service/web/src/components`를 정본**으로 둔다.
- `web-mfe`는 remote 노출용 얇은 엔트리 계층으로 유지한다.
- 같은 기능을 `web`와 `web-mfe`에 중복 구현하지 않는다.

권장 구조:

- `services/team-service/web/src/components/...`  
  - 실제 화면 컴포넌트, 비즈니스 UI 로직
- `services/team-service/web-mfe/src/components/mf/...`  
  - host에 노출할 MF 엔트리 래퍼
- `services/team-service/web-mfe/src/pages/...`  
  - Pages Router 기반 개발/테스트용 진입점

---

## 2) MFE 노출 절차 (Team Remote)

### Step 1. web에서 기능 구현

먼저 `services/team-service/web/src/components`에 기능을 완성한다.

예시:

- `services/team-service/web/src/components/team/team-management-view.tsx`

### Step 2. web-mfe 엔트리 파일 생성

`web-mfe/src/components/mf/` 또는 `web-mfe/src/pages/` 아래에 엔트리를 만든다.

예시 (`web-mfe/src/components/mf/team-management-entry.tsx`):

```tsx
"use client";

import { TeamManagementView } from "@web/components/team/team-management-view";

export default function TeamManagementEntry() {
  return <TeamManagementView />;
}
```

### Step 3. `exposes` 등록

`services/team-service/web-mfe/next.config.ts`의 `NextFederationPlugin`에 노출 경로를 추가한다.

예시:

```ts
new NextFederationPlugin({
  name: "team",
  filename: "static/chunks/remoteEntry.js",
  exposes: {
    "./TeamManagement": "./src/components/mf/team-management-entry.tsx",
    "./TeamMembers": "./src/components/mf/team-members-entry.tsx",
  },
});
```

---

## 3) 설정 점검 체크리스트 (현재 저장소 기준)

### 3.1 `tsconfig` 별칭

`services/team-service/web-mfe/tsconfig.json`에 아래가 있어야 한다.

```json
{
  "compilerOptions": {
    "baseUrl": ".",
    "paths": {
      "@web/*": ["../web/src/*"]
    }
  }
}
```

- 이 별칭으로 `web` 정본 컴포넌트를 참조한다.
- 상대경로(`../../..`) 남용을 피하고 유지보수성을 높인다.

### 3.2 Federation 기본 틀

`services/team-service/web-mfe/next.config.ts`에 아래 항목이 있어야 한다.

- `NextFederationPlugin` 사용
- `name: "team"`
- `filename: "static/chunks/remoteEntry.js"`
- `exposes`에 엔트리 등록

현재 저장소는 위 기본 틀이 이미 적용되어 있다.

---

## 4) 데이터 통신 원칙

### 4.1 상태 변경(팀 생성/수정/삭제)

- 팀 생성/수정 등 상태 변경 시에는 **Identity가 정의한 도메인 계약/이벤트 규격**을 따른다.
- API 필드/검증/이벤트 payload를 임의로 확장하지 말고, 계약 문서와 백엔드 스키마를 동시에 갱신한다.

### 4.2 실시간 검증이 필요한 기능

- 중복 이름 확인, 권한 검증 등 즉시성 검증은 **`team-service` API 직접 호출** 기준으로 설계한다.
- 클라이언트 단 캐시는 UX 최적화 용도로만 사용하고, 최종 검증은 서버 응답을 기준으로 한다.

---

## 5) Host(`apps/web`) 연동 방법

`apps/web`은 이미 `team` remote를 참조한다. 새로 노출한 컴포넌트는 다음처럼 `dynamic import`로 불러온다.

예시:

```tsx
import dynamic from "next/dynamic";

const TeamManagement = dynamic(() => import("team/TeamManagement"), {
  ssr: false,
  loading: () => <div>팀 화면 로딩 중...</div>,
});

export default function TeamPage() {
  return <TeamManagement />;
}
```

주의:

- remote 식별자(`team/TeamManagement`)는 `web-mfe`의 `exposes` 키와 정확히 일치해야 한다.
- SSR 호환이 어렵거나 브라우저 의존 코드가 있으면 `ssr: false`를 유지한다.

---

## 6) 로컬 개발 실행 플로우 (권장)

신규 기능 개발 시 보통 아래 3개를 함께 띄운다.

1. Team 운영 앱 (`team-web`)
2. Team MF remote (`team-web-mfe`)
3. Host (`web-host`)

현재 스크립트 기준:

- `pnpm --filter team-web dev` (3002)
- `pnpm --filter team-web-mfe dev` (3012)
- `pnpm --filter web-host dev` (3100)

---

## 7) 실행 스크립트 제언 (선택 적용)

루트 `package.json`에 아래 스크립트를 추가하면 onboarding이 쉬워진다.

```json
{
  "scripts": {
    "dev:team-stack": "pnpm --parallel --filter team-web --filter team-web-mfe --filter web-host dev",
    "dev:team-mfe": "pnpm --parallel --filter team-web-mfe --filter web-host dev"
  }
}
```

`turbo`를 쓰는 경우 예시:

```bash
turbo run dev --filter=team-web --filter=team-web-mfe --filter=web-host --parallel
```

팀 규칙상 스크립트를 실제 추가할 때는 CI 영향 여부를 확인하고 PR에서 함께 검증한다.

---

## 8) 신규 기능 반영 체크리스트

1. `web/src/components`에 기능 구현 완료
2. `web-mfe` 엔트리 생성 (`@web/*` import)
3. `next.config.ts` `exposes` 등록
4. host에서 `dynamic(() => import("team/..."))` 연결
5. 로컬에서 `team-web + team-web-mfe + web-host` 동시 검증
6. 경로/BFF 영향이 있으면 `docs/contracts/web-split-boundary.md` 갱신
