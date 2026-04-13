import type { ReactNode } from "react"

import { NotificationShell } from "@/components/shell/notification-shell"

export default function ShellLayout({ children }: { children: ReactNode }) {
  return <NotificationShell>{children}</NotificationShell>
}

