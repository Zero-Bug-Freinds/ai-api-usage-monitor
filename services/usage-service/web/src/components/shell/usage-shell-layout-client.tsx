"use client"

import { Suspense, useState, type ReactNode } from "react"
import { usePathname } from "next/navigation"
import { PanelLeftClose, PanelLeftOpen } from "lucide-react"

import { Button } from "@ai-usage/ui"
import { UsageSecondSidebar } from "@/components/shell/usage-second-sidebar"

type UsageShellLayoutClientProps = {
  children: ReactNode
}

function isUsageLogRoute(pathname: string | null): boolean {
  if (!pathname) return false
  return pathname.includes("/usagelog")
}

export function UsageShellLayoutClient({ children }: UsageShellLayoutClientProps) {
  const pathname = usePathname()
  const hideSecondSidebar = isUsageLogRoute(pathname)
  const [isSidebarOpen, setIsSidebarOpen] = useState(true)

  if (hideSecondSidebar) {
    return <div className="min-h-0 min-w-0 w-full flex-1">{children}</div>
  }

  return (
    <div className="flex min-h-full w-full min-w-0 gap-4">
      <div className="flex shrink-0 items-start pt-1">
        <Button
          type="button"
          variant="ghost"
          size="icon"
          aria-label={isSidebarOpen ? "사이드바 접기" : "사이드바 펼치기"}
          onClick={() => setIsSidebarOpen((prev) => !prev)}
        >
          {isSidebarOpen ? <PanelLeftClose className="h-4 w-4" /> : <PanelLeftOpen className="h-4 w-4" />}
        </Button>
      </div>

      <div
        className={[
          "transition-all duration-300 ease-out overflow-hidden",
          isSidebarOpen ? "w-64 min-w-[240px]" : "w-0 min-w-0",
        ].join(" ")}
      >
        <Suspense fallback={<aside className="w-64 min-w-[240px] shrink-0 rounded-lg border border-border bg-zinc-100/70 p-3" />}>
          <UsageSecondSidebar />
        </Suspense>
      </div>

      <div className="min-h-0 min-w-0 flex-1">{children}</div>
    </div>
  )
}
