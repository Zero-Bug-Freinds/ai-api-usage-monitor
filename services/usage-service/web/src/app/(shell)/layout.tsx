import type { ReactNode } from "react"

import { ConsoleShell } from "@ai-usage/shell"
import { LogoutCleanupMount } from "@/components/shell/logout-cleanup-mount"

export default function ShellLayout({ children }: { children: ReactNode }) {
  return (
    <ConsoleShell profile="usage">
      <LogoutCleanupMount />
      {children}
    </ConsoleShell>
  )
}
