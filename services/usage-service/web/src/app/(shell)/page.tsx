import type { Metadata } from "next"
import { Suspense } from "react"

import { UsageDashboard } from "@/components/usage/usage-dashboard"
import { COMMON_MESSAGES } from "@/lib/usage/messaging/common-messages"

export const metadata: Metadata = {
  title: "사용량",
}

export default function UsageDashboardPage() {
  return (
    <Suspense fallback={<p className="p-4 text-sm text-muted-foreground">{COMMON_MESSAGES.shell.loading}</p>}>
      <UsageDashboard />
    </Suspense>
  )
}
