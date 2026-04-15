"use client"

import type { ReactNode } from "react"

import { ConsoleShell } from "@ai-usage/shell"

import { ToastProvider } from "@/components/toast/toast-provider"
import { InAppNotificationToastListener } from "@/components/toast/in-app-notification-toast-listener"

export function NotificationShell({ children }: { children: ReactNode }) {
  return (
    <ToastProvider>
      <ConsoleShell profile="notification" logoutApiPath="/api/auth/logout" logoutRedirectPath="/">
        <InAppNotificationToastListener />
        {children}
      </ConsoleShell>
    </ToastProvider>
  )
}

