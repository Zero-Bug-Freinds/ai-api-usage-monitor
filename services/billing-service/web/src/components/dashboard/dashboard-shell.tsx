"use client"

import type { ReactNode } from "react"

import { ConsoleShell } from "@ai-usage/shell"

import { DashboardSidebar } from "@/components/dashboard/dashboard-sidebar"

type DashboardShellProps = {
  children: ReactNode
}

/**
 * usage-web 과 동일한 좌측 내비 + 본문 뼈대(Identity :3000 에서 /billing 프록시 시에도 메뉴 일관).
 */
export function DashboardShell({ children }: DashboardShellProps) {
  return (
    <ConsoleShell sidebar={<DashboardSidebar />}>{children}</ConsoleShell>
  )
}
