import { Suspense, type ReactNode } from "react"

import { ConsoleShell } from "@ai-usage/shell"
import { UsageSecondSidebar } from "@/components/shell/usage-second-sidebar"

export default function ShellLayout({ children }: { children: ReactNode }) {
  return (
    <ConsoleShell
      profile="usage"
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
