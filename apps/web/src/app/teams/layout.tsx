import type { ReactNode } from "react"

import { DashboardShell } from "@/components/dashboard/dashboard-shell"

export default function TeamsLayout({ children }: { children: ReactNode }) {
  return <DashboardShell>{children}</DashboardShell>
}
