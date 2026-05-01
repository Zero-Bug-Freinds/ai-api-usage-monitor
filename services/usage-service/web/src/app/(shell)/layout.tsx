import type { ReactNode } from "react"

import { ConsoleShell } from "@ai-usage/shell"
import { LogoutCleanupMount } from "@/components/shell/logout-cleanup-mount"

export default function ShellLayout({ children }: { children: ReactNode }) {
  const idOrigin = (process.env.NEXT_PUBLIC_IDENTITY_WEB_ORIGIN ?? "").replace(/\/$/, "")
  const logoutApiPath = `${idOrigin}/api/auth/logout`
  const logoutRedirectPath = `${idOrigin}/login`

  return (
    <ConsoleShell
      profile="usage"
      logoutApiPath={logoutApiPath}
      logoutRedirectPath={logoutRedirectPath}
    >
      <LogoutCleanupMount />
      {children}
    </ConsoleShell>
  )
}
