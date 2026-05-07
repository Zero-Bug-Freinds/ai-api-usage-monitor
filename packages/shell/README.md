# `@ai-usage/shell`

Shared console layout: sidebar, navigation UI, logout wiring, `ConsoleShell`, `ConsoleLayoutOverride`.

## Navigation UI

`packages/shell/src/console-sidebar.tsx` owns the console navigation UI. Service apps should render
`<ConsoleShell profile="...">` or `ConsoleSidebarPages` and avoid rebuilding nav labels, icons,
active-state rules, or logout URLs locally.

Cross-app navigation uses `NEXT_PUBLIC_WEB_EDGE_ORIGIN` first, with the legacy
`NEXT_PUBLIC_IDENTITY_WEB_ORIGIN` read only as a fallback for older env files.

## In-app notification toasts (`ConsoleShell`)

`ConsoleShell` wraps non-notification profiles with a client subtree that polls the notification BFF and shows up to five toasts (bottom-right). **`profile === "notification"`** skips that subtree so notification-web’s own toast stack is not duplicated.

### Coverage (what is in / out of scope)

- **In scope:** Any app that renders **`ConsoleShell`** (usage, billing, team, identity, agent shell layouts, etc.).
- **Out of this change:** Layouts that use **`ConsoleLayoutOverride`** only and never mount `ConsoleShell`, for example:
  - Module Federation host: `apps/web/src/components/host-shell-layout.tsx`
- **Follow-up for “global” parity:** Export a small client root (e.g. provider + listener bundle) from this package and mount it once in those shells; that is a separate wiring task.

### Poll URL (same-origin)

The listener calls a fixed browser path at web-edge:

`/notifications/api/notification/in-app-notifications?limit=10`

It does **not** use `NEXT_PUBLIC_BASE_PATH` from the embedding app, so embedded bundles under `/billing`, `/teams`, etc. still hit the notification app on the **current origin**.

Optional: `NEXT_PUBLIC_NOTIFICATION_POLL_MS` (default 10s, minimum 2s).

### Manual verification

Use the **integrated** web-edge origin, not a single-service dev port without `/notifications`.

1. Open a `ConsoleShell` page (e.g. `/dashboard`, `/billing`, `/teams`) and stay logged in.
2. Create an in-app notification (team flow or API) so a new unread item appears.
3. Confirm toasts appear bottom-right, dismiss works, and polling respects `NEXT_PUBLIC_NOTIFICATION_POLL_MS` if set.
4. Open `/notifications` and confirm **no duplicate** toasts vs other console pages (notification profile skips the shell’s listener).

### Tests

This package does not ship a dedicated Vitest/Jest job. Validation for this feature is **manual** as above unless the repo adds a shell-scoped test runner later.
