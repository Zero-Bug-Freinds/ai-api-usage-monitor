export type ConsoleProfile = "identity" | "usage" | "billing"

export type ConsoleNavId =
  | "usageHome"
  | "billingHome"
  | "settings"
  | "organizations"
  | "teams"
  | "identityLanding"

export type RouteOwner = "usage" | "billing" | "identity"

export type ConsoleNavMeta = {
  label: string
  owner: RouteOwner
  /** Browser path at the identity edge (single host, no basePath on identity app). */
  publicPath: string
}

export const CONSOLE_NAV: Record<ConsoleNavId, ConsoleNavMeta> = {
  usageHome: { label: "사용량", owner: "usage", publicPath: "/dashboard" },
  billingHome: { label: "지출", owner: "billing", publicPath: "/billing" },
  settings: { label: "설정", owner: "identity", publicPath: "/settings" },
  organizations: { label: "조직", owner: "identity", publicPath: "/organizations" },
  teams: { label: "팀", owner: "identity", publicPath: "/teams" },
  identityLanding: { label: "홈으로", owner: "identity", publicPath: "/" },
}

/** Main sidebar order (excludes identity landing / back link). */
export const CONSOLE_MAIN_NAV_ORDER: ConsoleNavId[] = [
  "usageHome",
  "billingHome",
  "settings",
  "organizations",
  "teams",
]
