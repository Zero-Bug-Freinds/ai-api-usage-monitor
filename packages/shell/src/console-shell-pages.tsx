"use client"

import type { ReactNode } from "react"
import { useRouter } from "next/router"

import type { ConsoleProfile } from "./console-nav"
import { buildTeamSubmenuPublicHref, resolveWebEdgeLogoutPathsFromEnv } from "./console-nav"
import { ConsoleSidebarInner } from "./console-sidebar"
import { ConsoleShellInAppToastClient } from "./console-shell-in-app-toast-client"

export type ConsoleShellPagesProps = {
  profile: ConsoleProfile
  children: ReactNode
  logoutApiPath?: string
  logoutRedirectPath?: string
}

/**
 * Pages Router(team-web 등) 전용 `ConsoleShell`. `next/navigation` 대신 `next/router` pathname 을 쓴다.
 */
export function ConsoleShellPages({
  profile,
  children,
  logoutApiPath,
  logoutRedirectPath,
}: ConsoleShellPagesProps) {
  const router = useRouter()
  const pathname = router.asPath.split("?")[0] ?? ""
  const defaultLogoutPaths = resolveWebEdgeLogoutPathsFromEnv()

  return (
    <div className="flex min-h-screen w-full min-w-0 bg-background">
      <ConsoleSidebarInner
        pathname={pathname}
        profile={profile}
        logoutApiPath={logoutApiPath ?? defaultLogoutPaths.logoutApiPath}
        logoutRedirectPath={logoutRedirectPath ?? defaultLogoutPaths.logoutRedirectPath}
        buildTeamSubmenuHref={profile === "team" ? buildTeamSubmenuPublicHref : undefined}
        navigationReady={router.isReady}
      />
      <main className="flex min-h-screen min-w-0 flex-1 flex-col overflow-x-auto overflow-y-auto">
        <div className="mx-auto min-h-full w-full max-w-6xl flex-1 px-4 py-6 sm:px-6 lg:px-8">
          {profile === "notification" ? children : <ConsoleShellInAppToastClient>{children}</ConsoleShellInAppToastClient>}
        </div>
      </main>
    </div>
  )
}
