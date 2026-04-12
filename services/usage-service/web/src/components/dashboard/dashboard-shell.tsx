"use client"

import type { ReactNode } from "react"

import { ConsoleShell } from "@ai-usage/shell"

import { DashboardSidebar } from "@/components/dashboard/dashboard-sidebar"

type DashboardShellProps = {
  children: ReactNode
}

/**
 * Google AI Studio 스타일의 좌측 내비 + 본문 뼈대. 로그인·쿠키·BFF 동작은 변경하지 않는다.
 */
export function DashboardShell({ children }: DashboardShellProps) {
  return (
    <ConsoleShell sidebar={<DashboardSidebar />}>{children}</ConsoleShell>
  )
}
