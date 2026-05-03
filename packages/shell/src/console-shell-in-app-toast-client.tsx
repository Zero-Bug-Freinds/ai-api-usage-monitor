"use client"

import type { ReactNode } from "react"

import { InAppNotificationToastListener } from "./in-app-notification-toast-listener"
import { InAppToastProvider } from "./in-app-toast-provider"

/**
 * Client subtree: in-app notification toasts + page content.
 * Mounted only from {@link ConsoleShell} when `profile !== "notification"`.
 */
export function ConsoleShellInAppToastClient({ children }: { children: ReactNode }) {
  return (
    <InAppToastProvider>
      <InAppNotificationToastListener />
      {children}
    </InAppToastProvider>
  )
}
