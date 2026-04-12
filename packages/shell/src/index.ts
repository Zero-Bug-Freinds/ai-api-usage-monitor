export type {
  ConsoleNavId,
  ConsoleNavMeta,
  ConsoleProfile,
  RouteOwner,
} from "./console-nav-model"
export { CONSOLE_MAIN_NAV_ORDER, CONSOLE_NAV } from "./console-nav-model"
export {
  anchorHrefForPublicPath,
  identityWebOrigin,
  isConsoleNavActive,
  resolveConsoleNavLink,
  usageDashboardHref,
  usageEntryPublicPath,
  type ConsoleNavLinkSpec,
} from "./console-nav-resolve"
export { ConsoleSidebar, type ConsoleSidebarProps } from "./console-sidebar"
export { ConsoleShell, type ConsoleShellProps } from "./console-shell"

/** Alias for billing layouts (same layout as `ConsoleShell`). */
export { ConsoleShell as BillingConsoleShell } from "./console-shell"
