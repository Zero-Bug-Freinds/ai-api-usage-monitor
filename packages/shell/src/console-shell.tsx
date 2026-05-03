import type { ReactNode } from "react"

import type { ConsoleProfile } from "./console-nav"
import { ConsoleShellInAppToastClient } from "./console-shell-in-app-toast-client"
import { ConsoleSidebar } from "./console-sidebar"

export type ConsoleShellProps = {
  profile: ConsoleProfile
  children: ReactNode
  logoutApiPath?: string
  logoutRedirectPath?: string
}

export function resolveIdentityLogoutPathsFromEnv(): {
  logoutApiPath: string
  logoutRedirectPath: string
} {
  return {
    logoutApiPath: "/api/auth/logout",
    logoutRedirectPath: "/login",
  }
}

/**
 * Google AI Studio 스타일의 좌측 내비 + 본문 뼈대.
 */
export function ConsoleShell({
  profile,
  children,
  logoutApiPath,
  logoutRedirectPath,
}: ConsoleShellProps) {
  return (
    <div className="flex min-h-screen w-full min-w-0 bg-background">
      <ConsoleSidebar
        profile={profile}
        logoutApiPath={logoutApiPath}
        logoutRedirectPath={logoutRedirectPath}
      />
      <main className="flex min-h-screen min-w-0 flex-1 flex-col overflow-x-auto overflow-y-auto">
        <div className="mx-auto min-h-full w-full max-w-6xl flex-1 px-4 py-6 sm:px-6 lg:px-8">
          {profile === "notification" ? children : <ConsoleShellInAppToastClient>{children}</ConsoleShellInAppToastClient>}
        </div>
      </main>
    </div>
  )
}
