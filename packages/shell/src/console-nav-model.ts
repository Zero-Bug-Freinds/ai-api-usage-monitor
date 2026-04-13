export type ConsoleProfile = "identity" | "usage" | "billing" | "notification"

export type ConsoleNavId =
  | "usageHome"
  | "usageLog"
  | "billingHome"
  | "notifications"
  | "settings"
  | "organizations"
  | "teams"
  | "identityLanding"

export type RouteOwner = "usage" | "billing" | "identity" | "notification"

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
  teams: { label: "팀", owner: "identity", publicPath: "/teams" },
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
