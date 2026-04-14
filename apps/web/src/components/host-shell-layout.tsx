"use client"

import type { ReactNode } from "react"

import { ConsoleShell, ConsoleSidebar } from "@ai-usage/shell"

export function HostShellLayout({ children }: { children: ReactNode }) {
  return (
    <ConsoleShell
      contentClassName="min-h-full w-full max-w-none flex-1 px-4 py-6 sm:px-6 lg:px-8"
      sidebar={
        <ConsoleSidebar
          profile="identity"
          publicPathOverrides={{ teams: "/team" }}
          footer={
            <p className="text-xs text-muted-foreground">
              Host: web-host · Remotes: team-web, usage-web
            </p>
          }
        />
      }
    >
      {children}
    </ConsoleShell>
  )
}
