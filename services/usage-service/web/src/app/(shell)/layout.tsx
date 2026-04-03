import type { ReactNode } from "react"

import { DashboardShell } from "@/components/dashboard/dashboard-shell"

export default function ShellLayout({ children }: { children: ReactNode }) {
  return <DashboardShell>{children}</DashboardShell>
}
