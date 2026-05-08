"use client"

import type { ReactNode } from "react"
import { ConsoleShell } from "@ai-usage/shell"

export function AgentShell({ children }: { children: ReactNode }) {
  return <ConsoleShell profile="agent">{children}</ConsoleShell>
}
