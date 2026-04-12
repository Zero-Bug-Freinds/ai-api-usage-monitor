"use client"

import type { ReactNode } from "react"

import { DashboardSidebar } from "@/components/dashboard/dashboard-sidebar"

type DashboardShellProps = {
  children: ReactNode
}

/**
 * usage-web 과 동일한 좌측 내비 + 본문 뼈대(Identity :3000 에서 /billing 프록시 시에도 메뉴 일관).
 */
export function DashboardShell({ children }: DashboardShellProps) {
  return (
    <div className="flex min-h-screen w-full bg-background">
      <DashboardSidebar />
      <main className="min-h-screen min-w-0 flex-1 overflow-x-auto">
        <div className="mx-auto min-h-full max-w-6xl px-4 py-6 sm:px-6 lg:px-8">{children}</div>
      </main>
    </div>
  )
}
