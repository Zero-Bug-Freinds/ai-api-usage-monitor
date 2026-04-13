import type { ConsoleNavId, ConsoleProfile } from "./console-nav-model"
import { CONSOLE_NAV } from "./console-nav-model"

/**
 * Identity web origin for split ports (e.g. usage on :3001 → identity on :3000).
 * Empty when apps are served on the same browser origin (e.g. :3000 + rewrite).
 */
export function identityWebOrigin(): string {
  return (process.env.NEXT_PUBLIC_IDENTITY_WEB_ORIGIN ?? "").replace(/\/$/, "")
}

function normalizePath(path: string): string {
  if (!path.startsWith("/")) return `/${path}`
  return path
}

/**
 * Resolves a root-absolute path for `<a href>` so it is not prefixed by Next `basePath`
 * (usage `/dashboard`, billing `/billing`). When `NEXT_PUBLIC_IDENTITY_WEB_ORIGIN` is set,
 * returns a full URL to that origin.
 */
export function anchorHrefForPublicPath(publicPath: string): string {
  const path = normalizePath(publicPath)
  const origin = identityWebOrigin()
  if (origin) return `${origin}${path}`
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

  const _never: never = profile
  throw new Error(`Unexpected profile: ${_never}`)
}

/** Public dashboard URL for links from billing or CTAs (split-origin aware). */
export function usageDashboardHref(): string {
  const usageBase = (process.env.NEXT_PUBLIC_USAGE_WEB_ORIGIN ?? "").replace(/\/$/, "")
  const basePath = (process.env.NEXT_PUBLIC_USAGE_BASE_PATH ?? "/dashboard").replace(/\/$/, "")
  if (!usageBase) return basePath || "/"
  return `${usageBase}${basePath}` || usageBase
}

/**
 * Identity landing / 대시보드 entry: same semantics as former `usageAppHref("/dashboard")` on identity.
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

  return false
}
