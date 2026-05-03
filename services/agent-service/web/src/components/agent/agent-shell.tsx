"use client"

import type { ReactNode } from "react"
import { ConsoleShell } from "@ai-usage/shell"

export function AgentShell({ children }: { children: ReactNode }) {
  const idOrigin = (process.env.NEXT_PUBLIC_IDENTITY_WEB_ORIGIN ?? "").replace(/\/$/, "")
  const logoutApiPath = idOrigin ? `${idOrigin}/api/auth/logout` : "/api/auth/logout"
  const logoutRedirectPath = idOrigin ? `${idOrigin}/login` : "/login"

  return (
    <ConsoleShell profile="agent" logoutApiPath={logoutApiPath} logoutRedirectPath={logoutRedirectPath}>
      {children}
    </ConsoleShell>
  )
}
