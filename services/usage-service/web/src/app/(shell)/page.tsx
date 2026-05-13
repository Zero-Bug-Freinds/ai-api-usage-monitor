import type { Metadata } from "next"
import { Suspense } from "react"

import { UsageDashboard } from "@/components/usage/usage-dashboard"

export const metadata: Metadata = {
  title: "사용량",
}

export default function UsageDashboardPage() {
  return (
    <Suspense fallback={<p className="p-4 text-sm text-muted-foreground">불러오는 중…</p>}>
      <UsageDashboard />
    </Suspense>
  )
}
