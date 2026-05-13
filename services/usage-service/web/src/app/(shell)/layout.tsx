import type { ReactNode } from "react"

import { ConsoleShell } from "@ai-usage/shell"
import { UsageShellLayoutClient } from "@/components/shell/usage-shell-layout-client"

export default function ShellLayout({ children }: { children: ReactNode }) {
  return (
    <ConsoleShell profile="usage">
      <UsageShellLayoutClient>{children}</UsageShellLayoutClient>
    </ConsoleShell>
  )
}
