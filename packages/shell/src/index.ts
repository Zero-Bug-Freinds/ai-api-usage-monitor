export type {
  ConsoleNavId,
  ConsoleNavMeta,
  ConsoleProfile,
  RouteOwner,
} from "./console-nav"
export { CONSOLE_MAIN_NAV_ORDER, CONSOLE_NAV } from "./console-nav"
export {
  anchorHrefForPublicPath,
  webEdgeOrigin,
  identityWebOrigin,
  isConsoleNavActive,
  notificationUnreadCountFetchUrl,
  ownsNavItemForSpaLink,
  resolveConsoleNavLink,
  resolveWebEdgeLogoutPathsFromEnv,
  usageDashboardHref,
  usageEntryPublicPath,
  webEdgeHref,
  type ConsoleNavLinkSpec,
} from "./console-nav"
export { ConsoleSidebar } from "./console-sidebar-app"
export { ConsoleSidebarPages, type ConsoleSidebarProps } from "./console-sidebar"
export {
  ConsoleShell,
  type ConsoleShellProps,
  resolveIdentityLogoutPathsFromEnv,
} from "./console-shell"
export { ConsoleShellInAppToastClient } from "./console-shell-in-app-toast-client"
export { ConsoleLayoutOverride } from "./console-layout-override"

/** Alias for billing layouts (same layout as `ConsoleShell`). */
export { ConsoleShell as BillingConsoleShell } from "./console-shell"
