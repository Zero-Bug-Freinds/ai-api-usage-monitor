"use client"

import type { ReactNode } from "react"

import { ConsoleShell, ConsoleSidebar } from "@ai-usage/shell"
import { Button } from "@ai-usage/ui"

import { ToastProvider } from "@/components/toast/toast-provider"
import { InAppNotificationToastListener } from "@/components/toast/in-app-notification-toast-listener"

export function NotificationShell({ children }: { children: ReactNode }) {
  return (
    <ToastProvider>
      <ConsoleShell
        sidebar={
          <ConsoleSidebar
            profile="usage"
            footer={
              <form action="/api/auth/logout" method="post">
                <Button type="submit" variant="secondary" className="w-full">
                  로그아웃
                </Button>
              </form>
            }
          />
        }
      >
        <InAppNotificationToastListener />
        {children}
      </ConsoleShell>
    </ToastProvider>
  )
}

