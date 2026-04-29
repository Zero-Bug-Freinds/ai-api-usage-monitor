import type { ReactNode } from "react"
import { AgentShell } from "@/components/agent/agent-shell"

export default function ShellLayout({ children }: { children: ReactNode }) {
  return <AgentShell>{children}</AgentShell>
}
