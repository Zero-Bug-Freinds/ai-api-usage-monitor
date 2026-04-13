# 콘솔 사이드바에 탭 추가·라우팅 연결 가이드

새 화면을 만든 뒤 **공통 사이드바(메뉴바)** 에 탭을 추가하고, 클릭 시 **브라우저 주소가 소유 서비스와 일치하는 경로**로 이동하도록 설정하는 절차입니다.

**관련 정본:** [`docs/repository-structure.md`](repository-structure.md) §6, [`docs/contracts/web-split-boundary.md`](contracts/web-split-boundary.md), 구현 소스 [`packages/shell/src/`](../packages/shell/src/).

---

## 1. 먼저 결정할 것 (라우팅 소유권)

| 질문 | 이유 |
|------|------|
| 이 화면의 **URL을 어느 서비스가 소유**하는가? | Identity / Usage / Billing / Team 등 **Next 앱이 `app/` 아래에서 실제로 그 경로를 렌더**해야 한다. |
| **브라우저에 보일 공개 경로**는 무엇인가? | 단일 도메인(예: `localhost:3000`) 기준으로 `/settings`, `/dashboard/...`, `/billing/...` 처럼 **한 줄로 고정**해 팀과 공유한다. |
| Usage·Billing 앱인가? | 해당 `web`은 `next.config`에 **`basePath`**(` /dashboard`, `/billing`)가 있을 수 있다. **탭 `href`는 `@ai-usage/shell`에서만 조립**하는 것을 권장한다(잘못 쓰면 `/dashboard/settings` 같은 이중 접두가 난다). |

**요약:** “어느 `services/<svc>/web`에 `page.tsx`를 둘지”와 “주소창에 무엇이 찍힐지”를 먼저 합의한 뒤 구현합니다.

---

## 2. 해당 서비스에 화면(라우트) 추가

1. 소유가 정해지면 **`services/<서비스명>/web/src/app/`** 아래에 App Router 규칙에 맞게 디렉터리·`page.tsx`(·`layout.tsx`)를 추가합니다.
2. **Usage** 대시보드 하위라면 브라우저 경로는 보통 **`/dashboard/...`** 이고, 소스 트리에서는 `basePath`를 제외한 경로(예: `app/(shell)/foo/page.tsx` → 브라우저 `/dashboard/foo`)로 맞춥니다.
3. **Identity** 전용(설정·조직 등)이면 **`/settings/...`**, **`/organizations/...`** 등 [`web-split-boundary.md`](contracts/web-split-boundary.md) §2와 미들웨어 보호 규칙을 따릅니다.
4. **엣지(Nginx)** 나 **Identity `next.config` rewrites**로 경로가 넘어가는 구조라면, **새 최상위 접두**를 도입할 때 [`docker/web-edge/nginx.conf`](../docker/web-edge/nginx.conf) / identity 설정을 검토합니다(팀 합의 후).

---

## 3. `@ai-usage/shell`에 네비 항목 등록

콘솔 메뉴의 정본은 **`packages/shell`** 에 있습니다. 새 탭을 넣으려면 보통 아래를 순서대로 수정합니다.

### 3.1 식별자·메타데이터 (`console-nav-model.ts`)

- `ConsoleNavId` 유니온에 **새 키**를 추가합니다 (예: `"usageLog"`).
- `CONSOLE_NAV`에 항목을 추가합니다.
  - **`label`**: 사이드바에 보일 이름.
  - **`owner`**: `'identity' | 'usage' | 'billing' | 'notification'` — **어느 앱이 그 URL을 “담당”하는지**.
  - **`publicPath`**: **단일 오리진·엣지 기준 브라우저 절대 경로** (항상 `/` 로 시작).
- **메인 메뉴 순서**에 넣으려면 `CONSOLE_MAIN_NAV_ORDER` 배열에 같은 키를 끼워 넣습니다.  
  (`identityLanding`(「홈으로」)은 별도 영역이라 이 배열에 넣지 않습니다.)

### 3.2 아이콘 (`console-sidebar.tsx`)

- `ICONS` 맵에 **같은 `ConsoleNavId` 키**로 `lucide-react` 아이콘을 추가합니다.

### 3.3 링크·활성 상태 (`console-nav-resolve.ts`)

여기서 **basePath 함정**을 피합니다.

- **`resolveConsoleNavLink`**
  - **Identity 프로필**: 이제 “Identity가 소유한 라우트”만 `{ kind: 'next', href: publicPath }` 로 두고, 그 외(예: `/notifications`)는 `{ kind: 'anchor', href: anchorHrefForPublicPath(publicPath) }` 로 처리하는 패턴을 권장합니다.
  - **Usage / Billing 프로필**  
    - **그 앱이 소유한 경로**: `next/link`용 **`href`는 basePath 기준 “앱 내부 경로”** 여야 합니다.  
      현재 구현은 **각 앱당 “홈” 한 칸**만 `owner === usage|billing` 일 때 `href: '/'` 로 두는 패턴이 있습니다. **홈이 아닌 같은 앱 내 다른 탭**(예: `/dashboard/usagelog`)을 추가하려면, `publicPath`에서 `basePath`를 뺀 상대 경로(예: `/usagelog`)를 `next` 링크에 넣도록 **분기**를 추가해야 합니다.
    - **다른 앱 소유 경로**(identity·다른 MFE): **`{ kind: 'anchor', href: anchorHrefForPublicPath(publicPath) }`** — 루트 절대 경로 또는 분리 오리진 시 전체 URL.
- **`isConsoleNavActive`**
  - **Identity 프로필**: `publicPath`와 `usePathname()`(또는 동일 규칙)으로 접두 일치·하위 경로 포함 여부를 정합니다.
  - **Usage / Billing**: 해당 탭이 “이 앱에서 보이는 pathname”과 맞을 때만 `true`가 되도록 합니다.  
    (Next는 `basePath`를 제외한 pathname을 돌려주는 경우가 많으므로, 기존 `usageHome` / `billingHome`과 같은 방식으로 맞춥니다.)

구현 세부는 [`packages/shell/src/console-nav-resolve.ts`](../packages/shell/src/console-nav-resolve.ts)를 기준으로 합니다.

---

## 4. 웹 앱·빌드 설정 (이미 되어 있으면 생략)

- 각 `services/*/web`의 `package.json`에 **`"@ai-usage/shell": "workspace:*"`** 가 있어야 합니다.
- `next.config`의 **`transpilePackages`** 에 `"@ai-usage/shell"` 이 포함되어야 합니다.
- **Tailwind v4:** `src/app/globals.css` 에 **`@source`** 로 `packages/ui/src`·`packages/shell/src` 를 등록해 두면, 공유 패키지에 적은 유틸 클래스가 빌드 산출물에 포함된다(스타일이 비어 보이면 여기부터 확인).
- Docker 빌드 시 **`packages/shell`** 소스가 context에 포함되는지 Dockerfile을 확인합니다.

---

## 5. 분리 오리진(로컬 포트 분리)으로 개발할 때

- Identity와 Usage를 **서로 다른 포트**에서 띄우는 경우, **`NEXT_PUBLIC_IDENTITY_WEB_ORIGIN`** 등을 `.env`에 맞게 설정합니다.
- `anchorHrefForPublicPath`가 **전체 URL**을 만들어 크로스 오리진 이동이 되도록 되어 있습니다.  
  ([`packages/shell/src/console-nav-resolve.ts`](../packages/shell/src/console-nav-resolve.ts) 참고.)

---

## 6. 검증 체크리스트

- [ ] 주소창에 **의도한 공개 경로**만 나온다(특히 usage basePath 아래서 **`/dashboard/타앱경로`** 로 가지 않는다).
- [ ] 사이드바에서 **현재 페이지와 맞는 탭만 활성(강조)** 된다.
- [ ] `pnpm --filter <해당-web> build` 및 필요 시 단일 도메인·엣지 시나리오에서 한 번 더 확인한다.

---

## 7. Cursor(또는 AI)에 요청할 때 쓸 수 있는 문장 템플릿

아래를 복사해 빈칸만 채워 요청하면, 저장소 규칙과 맞추기 쉽습니다.

**템플릿 A — 새 화면 + 사이드바 탭(소유 서비스가 이미 정해진 경우)**

> `services/<서비스>/web`에 브라우저 경로 **`<공개 경로>`** 인 새 페이지를 추가하고, 콘솔 사이드바에 **`<탭 라벨>`** 탭을 넣어 줘.  
> 네비 정본은 `@ai-usage/shell`만 수정하고: `console-nav-model.ts`에 `ConsoleNavId`·`CONSOLE_NAV`·`CONSOLE_MAIN_NAV_ORDER`, `console-sidebar.tsx`에 아이콘, `console-nav-resolve.ts`에 `resolveConsoleNavLink` / `isConsoleNavActive`를 **`<identity|usage|billing>` 소유**에 맞게 확장해 줘.  
> `basePath`가 있는 앱이면 `next/link`와 `<a>` 중 어떤 걸 써야 하는지 기존 shell 규칙을 따르고, 다른 파일은 요청 범위 밖이면 건드리지 마.

**템플릿 B — 엣지·rewrite가 필요한 새 최상위 접두**

> 새 공개 경로 **`<접두>`** 를 추가할 거야. `docs/contracts/web-split-boundary.md`와 `docker/web-edge/nginx.conf`(또는 identity `next.config` rewrites) 중 어디를 고쳐야 하는지 검토하고, 그다음 `packages/shell` 네비와 `services/<svc>/web` 라우트를 맞춰 줘.

**템플릿 C — Usage 대시보드 **홈이 아닌** 하위 경로 탭만 추가**

> Usage `web`(basePath `/dashboard`)에 **`<앱 내부 경로>`** 페이지가 있고, 사이드바에 **`<라벨>`** 탭을 추가해 줘.  
> `resolveConsoleNavLink`에서 `profile === 'usage'` 이고 owner가 usage일 때, 지금은 `href: '/'`만 있는데 **`<경로>`** 로 가는 `next` 링크 분기와 `isConsoleNavActive`를 추가해 줘.

---

## 8. 자주 하는 실수

- **잘못된 예:** Usage 앱 컴포넌트에서 `next/link`로 `href="/settings"` 만 넘김 → 브라우저가 **`/dashboard/settings`** 이 될 수 있음.  
  **→** shell의 **`anchor`** 규칙 또는 공개 경로 헬퍼 사용.
- **문자열로만 경로를 여기저기 박기 →** 소유권이 흐려짐.  
  **→** `CONSOLE_NAV`와 타입 `ConsoleNavId`에 한 번만 정의하고 재사용.

---

## 문서 이력

| 날짜 | 내용 |
|------|------|
| 2026-04-12 | 최초 작성 (`@ai-usage/shell` 기준). |
| 2026-04-13 | Notification(인앱 알림함) 라우트 추가를 반영(새 `owner`, identity 프로필 anchor 처리). |
