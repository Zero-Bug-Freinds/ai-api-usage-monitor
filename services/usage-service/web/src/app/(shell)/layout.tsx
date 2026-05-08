import { Suspense, type ReactNode } from "react"

import { ConsoleShell } from "@ai-usage/shell"
import { UsageSecondSidebar } from "@/components/shell/usage-second-sidebar"

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
      <div className="flex min-h-full w-full min-w-0 gap-4">
        <Suspense fallback={<aside className="w-64 min-w-[240px] shrink-0 rounded-lg border border-border bg-zinc-100/70 p-3" />}>
          <UsageSecondSidebar />
        </Suspense>
        <div className="min-w-0 flex-1">{children}</div>
      </div>
    </ConsoleShell>
  )
}
