import type { ReactNode } from "react"

import { ConsoleShell } from "@ai-usage/shell"

export default function SettingsLayout({ children }: { children: ReactNode }) {
  return <ConsoleShell profile="identity">{children}</ConsoleShell>
}
