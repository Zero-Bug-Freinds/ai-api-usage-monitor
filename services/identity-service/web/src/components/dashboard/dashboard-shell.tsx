"use client"

import type { ReactNode } from "react"

import { DashboardSidebar } from "@/components/dashboard/dashboard-sidebar"

type DashboardShellProps = {
  children: ReactNode
}

/**
 * Google AI Studio 스타일의 좌측 내비 + 본문 뼈대. 로그인·쿠키·BFF 동작은 변경하지 않는다.
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
