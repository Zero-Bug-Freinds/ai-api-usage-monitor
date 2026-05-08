"use client"

import Link from "next/link"
import { usePathname, useSearchParams } from "next/navigation"

type ViewMode = "personal" | "team"

function navClass(active: boolean): string {
  return active
    ? "block rounded-md bg-muted px-3 py-2 text-sm font-semibold text-foreground"
    : "block rounded-md px-3 py-2 text-sm text-muted-foreground hover:bg-muted/60 hover:text-foreground"
}

export function UsageSecondSidebar() {
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const isTeam = pathname.startsWith("/team")
  const viewMode: ViewMode = isTeam ? "team" : "personal"
  const teamTab = searchParams.get("tab") === "member" ? "member" : "team"

  return (
    <aside className="w-64 min-w-[240px] shrink-0 rounded-lg border border-border bg-zinc-100/70 p-3">
      <div className="mb-4">
        <p className="mb-2 text-xs font-medium text-muted-foreground">메뉴</p>
        <div className="inline-flex w-full rounded-md border border-border bg-background p-0.5">
          <Link href="/" className={`flex-1 rounded px-3 py-1.5 text-center text-sm ${viewMode === "personal" ? "bg-muted font-medium text-foreground" : "text-muted-foreground"}`}>
            개인
          </Link>
          <Link href="/team" className={`flex-1 rounded px-3 py-1.5 text-center text-sm ${viewMode === "team" ? "bg-muted font-medium text-foreground" : "text-muted-foreground"}`}>
            팀
          </Link>
        </div>
      </div>

      <nav className="space-y-1">
        {viewMode === "personal" ? (
          <>
            <Link href="/" className={navClass(pathname === "/")}>개인 대시보드</Link>
          </>
        ) : (
          <>
            <Link href="/team?tab=team" className={navClass(pathname === "/team" && teamTab === "team")}>팀 대시보드</Link>
            <Link href="/team?tab=member" className={navClass(pathname === "/team" && teamTab === "member")}>멤버별 분석</Link>
          </>
        )}
      </nav>
    </aside>
  )
}
