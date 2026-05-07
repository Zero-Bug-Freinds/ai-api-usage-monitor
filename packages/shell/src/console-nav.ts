export type ConsoleProfile = "identity" | "usage" | "billing" | "notification" | "team" | "agent"

export type ConsoleNavId =
  | "usageHome"
  | "usageLog"
  | "billingHome"
  | "notifications"
  | "settings"
  | "organizations"
  | "teams"
  | "assistant"
  | "identityLanding"

export type RouteOwner = "usage" | "billing" | "identity" | "notification" | "team"

export type ConsoleNavMeta = {
  label: string
  owner: RouteOwner
  /** Browser path at web-edge (single host, no app-specific basePath prefixing here). */
  publicPath: string
}

export const CONSOLE_NAV: Record<ConsoleNavId, ConsoleNavMeta> = {
  usageHome: { label: "사용량", owner: "usage", publicPath: "/dashboard" },
  usageLog: { label: "상세 로그", owner: "usage", publicPath: "/dashboard/usagelog" },
  billingHome: { label: "지출", owner: "billing", publicPath: "/billing" },
  notifications: { label: "알림", owner: "notification", publicPath: "/notifications" },
  settings: { label: "설정", owner: "identity", publicPath: "/settings" },
  organizations: { label: "조직", owner: "identity", publicPath: "/organizations" },
  teams: { label: "팀", owner: "team", publicPath: "/teams" },
  assistant: { label: "비서", owner: "identity", publicPath: "/agent" },
  identityLanding: { label: "홈으로", owner: "identity", publicPath: "/" },
}

/** Main sidebar order (excludes identity landing / back link). */
export const CONSOLE_MAIN_NAV_ORDER: ConsoleNavId[] = [
  "usageHome",
  "usageLog",
  "billingHome",
  "notifications",
  "settings",
  "organizations",
  "teams",
  "assistant",
]

function normalizePath(path: string): string {
  if (!path.startsWith("/")) return `/${path}`
  return path
}

const DEFAULT_TEAM_SHELL_ENTRY = "http://localhost:8888/teams"

/**
 * 사이드바「팀」→ Main Shell 팀 콘솔 절대 URL.
 * `NEXT_PUBLIC_TEAM_SHELL_HREF`가 http(s)면 그대로, `/…` 상대면 엣지 오리진(`8888`)에 붙인다.
 */
export function resolveTeamShellEntryHref(): string {
  const raw =
    typeof process !== "undefined" ? process.env.NEXT_PUBLIC_TEAM_SHELL_HREF?.trim() ?? "" : ""
  if (!raw) {
    return DEFAULT_TEAM_SHELL_ENTRY
  }
  const normalized = raw.replace(/\/+$/, "")
  if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
    return normalized
  }
  const path = normalizePath(normalized)
  const edgeOrigin =
    typeof process !== "undefined"
      ? (process.env.NEXT_PUBLIC_WEB_EDGE_ORIGIN ?? "http://localhost:8888").replace(/\/+$/, "")
      : "http://localhost:8888"
  return `${edgeOrigin}${path}`
}

/**
 * Resolves a root-absolute path for `<a href>` so it is not prefixed by Next `basePath`
 * (usage `/dashboard`, billing `/billing`).
 */
export function anchorHrefForPublicPath(publicPath: string): string {
  const path = normalizePath(publicPath)
  return path
}

/**
 * Cross-app sidebar links: paths like `/dashboard` are routed by web-edge to other apps.
 * When this app is served on its own port (e.g. agent-web :3005), relative paths would 404 — prefix
 * {@link identityWebOrigin} when it is set and the shell is not already the identity app.
 */
function consoleCrossAppHref(profile: ConsoleProfile, publicPath: string): string {
  const path = anchorHrefForPublicPath(publicPath)
  if (profile === "identity") {
    return path
  }
  const origin = identityWebOrigin()
  if (!origin) {
    return path
  }
  return `${origin}${path}`
}

export type ConsoleNavLinkSpec =
  | { kind: "next"; href: string }
  | { kind: "anchor"; href: string }

/**
 * 현재 앱(프로필) 안에서만 `next/link`로 안전한 SPA 이동이 가능한 항목인지.
 * false이면 타 서비스(App/Pages 혼합)로 나가는 것이므로 항상 `<a href>`(풀 페이지)로 처리한다.
 *
 * | profile      | SPA(next) 대상 |
 * |--------------|----------------|
 * | identity     | owner === identity |
 * | usage        | usageHome, usageLog |
 * | billing      | billingHome |
 * | notification | notifications |
 * | team         | 없음(web-host 메인 넷은 모두 타 앱) |
 * | agent        | assistant |
 */
export function ownsNavItemForSpaLink(profile: ConsoleProfile, id: ConsoleNavId): boolean {
  const { owner } = CONSOLE_NAV[id]
  switch (profile) {
    case "identity":
      return owner === "identity"
    case "usage":
      return id === "usageHome" || id === "usageLog"
    case "billing":
      return id === "billingHome"
    case "notification":
      return id === "notifications"
    case "team":
      return false
    case "agent":
      return id === "assistant"
    default: {
      const _exhaustive: never = profile
      throw new Error(`Unexpected profile: ${_exhaustive}`)
    }
  }
}

function spaInternalHref(profile: ConsoleProfile, id: ConsoleNavId): string {
  const { publicPath } = CONSOLE_NAV[id]
  switch (profile) {
    case "identity":
      return publicPath
    case "usage":
      if (id === "usageHome") return "/"
      if (id === "usageLog") return "/usagelog"
      throw new Error(`resolveConsoleNavLink: unexpected usage SPA id ${id}`)
    case "billing":
      return "/"
    case "notification":
      return "/"
    case "agent":
      return "/"
    case "team":
      throw new Error("resolveConsoleNavLink: team profile has no SPA main-nav targets")
    default: {
      const _exhaustive: never = profile
      throw new Error(`Unexpected profile: ${_exhaustive}`)
    }
  }
}

/**
 * Builds the correct navigation target for the current app profile.
 * - `next`: safe for `next/link` inside the owning app (including basePath).
 * - `anchor`: cross-app routes; 풀 페이지 이동으로 MF·Router 컨텍스트 불일치를 피한다.
 */
export function resolveConsoleNavLink(profile: ConsoleProfile, id: ConsoleNavId): ConsoleNavLinkSpec {
  const meta = CONSOLE_NAV[id]
  const { publicPath } = meta

  /**
   * 팀 콘솔(web-host, basePath /teams)으로의 전환은 항상 풀 페이지 네비게이션(anchor).
   * 기본 진입점은 web-edge Main Shell(`http://localhost:8888/teams` 등 절대 URL)로 고정해
   * identity 등 타 오리진에서 상대 `/teams`로 잘못 이탈하는 것을 막는다(Task37-13).
   */
  if (id === "teams") {
    return { kind: "anchor", href: resolveTeamShellEntryHref() }
  }

  if (!ownsNavItemForSpaLink(profile, id)) {
    return { kind: "anchor", href: consoleCrossAppHref(profile, publicPath) }
  }
  return { kind: "next", href: spaInternalHref(profile, id) }
}

/** Public dashboard URL for links from billing or CTAs (same-origin relative path). */
export function usageDashboardHref(): string {
  const basePath = (process.env.NEXT_PUBLIC_USAGE_BASE_PATH ?? "/dashboard").replace(/\/$/, "")
  return basePath || "/"
}

/**
 * Legacy compatibility helper.
 * Returns identity web origin when explicitly configured; otherwise empty string
 * so callers can safely build same-origin relative paths.
 */
export function identityWebOrigin(): string {
  return (process.env.NEXT_PUBLIC_IDENTITY_WEB_ORIGIN ?? "").replace(/\/$/, "")
}

const NOTIFICATION_UNREAD_COUNT_PATH = "/notifications/api/notification/in-app-notifications/unread-count"

/**
 * Unread-count BFF lives under the notification app (via web-edge). On standalone
 * micro-apps (e.g. agent-web), same-origin `/notifications/...` would 404 — use identity origin when set.
 */
export function notificationUnreadCountFetchUrl(profile: ConsoleProfile): string {
  if (profile === "notification") {
    return NOTIFICATION_UNREAD_COUNT_PATH
  }
  const origin = identityWebOrigin()
  if (!origin) {
    return NOTIFICATION_UNREAD_COUNT_PATH
  }
  return `${origin}${NOTIFICATION_UNREAD_COUNT_PATH}`
}

/**
 * Identity landing / dashboard entry: same semantics as former `usageAppHref("/dashboard")` on identity.
 */
export function usageEntryPublicPath(): string {
  return "/dashboard"
}

export function isConsoleNavActive(profile: ConsoleProfile, pathname: string, id: ConsoleNavId): boolean {
  const p = pathname === "" ? "/" : pathname

  if (id === "identityLanding") {
    return profile === "identity" && p === "/"
  }

  const meta = CONSOLE_NAV[id]
  const { publicPath } = meta

  if (profile === "identity") {
    if (id === "usageLog") {
      return p === "/dashboard/usagelog" || p.startsWith("/dashboard/usagelog/")
    }
    if (publicPath === "/") return p === "/"
    if (publicPath === "/dashboard") {
      const onUsageLog = p === "/dashboard/usagelog" || p.startsWith("/dashboard/usagelog/")
      return (p === "/dashboard" || p.startsWith("/dashboard/")) && !onUsageLog
    }
    if (publicPath === "/billing") return p === "/billing" || p.startsWith("/billing/")
    if (publicPath === "/notifications") return p === "/notifications" || p.startsWith("/notifications/")
    return p === publicPath || p.startsWith(`${publicPath}/`)
  }

  if (profile === "usage") {
    if (id === "usageHome") return p === "/" || p === ""
    if (id === "usageLog") return p === "/usagelog" || p.startsWith("/usagelog/")
    return false
  }

  if (profile === "billing") {
    if (id === "billingHome") return p === "/" || p === ""
    return false
  }

  if (profile === "notification") {
    if (id === "notifications") return p === "/" || p === ""
    return false
  }

  if (profile === "team") {
    if (id === "teams") return p === "/teams" || p.startsWith("/teams?") || p.startsWith("/teams/")
    if (id === "assistant") return p === "/agent" || p.startsWith("/agent/")
    return false
  }

  if (profile === "agent") {
    if (id === "teams") return p === "/teams" || p.startsWith("/teams/")
    if (id === "assistant") return p === "/agent" || p.startsWith("/agent/")
    return false
  }

  return false
}
