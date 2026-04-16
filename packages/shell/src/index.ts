export type {
  ConsoleNavId,
  ConsoleNavMeta,
  ConsoleProfile,
  RouteOwner,
} from "./console-nav"
export { CONSOLE_MAIN_NAV_ORDER, CONSOLE_NAV } from "./console-nav"
export {
  anchorHrefForPublicPath,
  identityWebOrigin,
  isConsoleNavActive,
  resolveConsoleNavLink,
  usageDashboardHref,
  usageEntryPublicPath,
  type ConsoleNavLinkSpec,
} from "./console-nav"
export { ConsoleSidebar, type ConsoleSidebarProps } from "./console-sidebar"
export {
  ConsoleShell,
  type ConsoleShellProps,
  resolveIdentityLogoutPathsFromEnv,
} from "./console-shell"
export { ConsoleLayoutOverride } from "./console-layout-override"

/** Alias for billing layouts (same layout as `ConsoleShell`). */
export { ConsoleShell as BillingConsoleShell } from "./console-shell"
