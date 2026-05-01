"use client"

import type { ReactNode } from "react"
import { ConsoleLayoutOverride, ConsoleSidebar } from "@ai-usage/shell"

export function AgentShell({ children }: { children: ReactNode }) {
  return (
    <ConsoleLayoutOverride primarySidebar={<ConsoleSidebar profile="agent" />}>
      {children}
    </ConsoleLayoutOverride>
  )
}
