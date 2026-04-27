export type ConsoleProfile = "identity" | "usage" | "billing" | "notification" | "team"

export type ConsoleNavId =
  | "usageHome"
  | "usageLog"
  | "billingHome"
  | "notifications"
  | "settings"
  | "organizations"
  | "teams"
  | "identityLanding"

export type RouteOwner = "usage" | "billing" | "identity" | "notification" | "team"

export type ConsoleNavMeta = {
  label: string
  owner: RouteOwner
  /** Browser path at the identity edge (single host, no basePath on identity app). */
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
]

function normalizePath(path: string): string {
  if (!path.startsWith("/")) return `/${path}`
  return path
}

/**
 * Resolves a root-absolute path for `<a href>` so it is not prefixed by Next `basePath`
 * (usage `/dashboard`, billing `/billing`).
 */
export function anchorHrefForPublicPath(publicPath: string): string {
  const path = normalizePath(publicPath)
  return path
}

export type ConsoleNavLinkSpec =
  | { kind: "next"; href: string }
  | { kind: "anchor"; href: string }

/**
 * Builds the correct navigation target for the current app profile.
 * - `next`: safe for `next/link` inside the owning app (including basePath).
 * - `anchor`: cross-app or identity routes; avoids basePath double-prefix on usage/billing.
 */
export function resolveConsoleNavLink(profile: ConsoleProfile, id: ConsoleNavId): ConsoleNavLinkSpec {
  const meta = CONSOLE_NAV[id]
  const { owner, publicPath } = meta

  if (profile === "identity") {
    if (owner === "identity") {
      return { kind: "next", href: publicPath }
    }
    return { kind: "anchor", href: anchorHrefForPublicPath(publicPath) }
  }

  if (profile === "usage") {
    if (id === "usageHome") {
      return { kind: "next", href: "/" }
    }
    if (id === "usageLog") {
      return { kind: "next", href: "/usagelog" }
    }
    return { kind: "anchor", href: anchorHrefForPublicPath(publicPath) }
  }

  if (profile === "billing") {
    if (owner === "billing") {
      return { kind: "next", href: "/" }
    }
    return { kind: "anchor", href: anchorHrefForPublicPath(publicPath) }
  }

  if (profile === "notification") {
    if (owner === "notification") {
      return { kind: "next", href: "/" }
    }
    return { kind: "anchor", href: anchorHrefForPublicPath(publicPath) }
  }

  if (profile === "team") {
    if (owner === "team") {
      return { kind: "next", href: "/" }
    }
    return { kind: "anchor", href: anchorHrefForPublicPath(publicPath) }
  }

  const _never: never = profile
  throw new Error(`Unexpected profile: ${_never}`)
}

/** Public dashboard URL for links from billing or CTAs (same-origin relative path). */
export function usageDashboardHref(): string {
  const basePath = (process.env.NEXT_PUBLIC_USAGE_BASE_PATH ?? "/dashboard").replace(/\/$/, "")
  return basePath || "/"
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
    if (id === "teams") return true
    return false
  }

  return false
}
