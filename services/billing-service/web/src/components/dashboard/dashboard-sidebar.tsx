"use client"

import { ConsoleSidebar } from "@ai-usage/shell"

import { LogoutButton } from "@/components/auth/logout-button"

export function DashboardSidebar() {
  return (
    <ConsoleSidebar
      profile="billing"
      footer={<LogoutButton variant="outline" className="h-9 w-full justify-center text-sm" />}
    />
  )
}
