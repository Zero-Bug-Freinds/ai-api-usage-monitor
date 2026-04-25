"use client"

import * as React from "react"
import type { ReactNode } from "react"
import Link from "next/link"
import { usePathname } from "next/navigation"
import {
  Bell,
  Building2,
  ChevronLeft,
  LayoutDashboard,
  ScrollText,
  Settings,
  UsersRound,
  Wallet,
} from "lucide-react"

import { Button, cn } from "@ai-usage/ui"
import type { ConsoleNavId, ConsoleProfile } from "./console-nav"
import { CONSOLE_MAIN_NAV_ORDER, CONSOLE_NAV } from "./console-nav"
import { isConsoleNavActive, resolveConsoleNavLink } from "./console-nav"

const ICONS: Record<ConsoleNavId, ReactNode> = {
  usageHome: <LayoutDashboard className="size-[1.125rem] shrink-0" aria-hidden />,
  usageLog: <ScrollText className="size-[1.125rem] shrink-0" aria-hidden />,
  billingHome: <Wallet className="size-[1.125rem] shrink-0" aria-hidden />,
  notifications: <Bell className="size-[1.125rem] shrink-0" aria-hidden />,
  settings: <Settings className="size-[1.125rem] shrink-0" aria-hidden />,
  organizations: <Building2 className="size-[1.125rem] shrink-0" aria-hidden />,
  teams: <UsersRound className="size-[1.125rem] shrink-0" aria-hidden />,
  identityLanding: <ChevronLeft className="size-[1.125rem] shrink-0" aria-hidden />,
}

function NavRow({
  profile,
  id,
  pathname,
}: {
  profile: ConsoleProfile
  id: ConsoleNavId
  pathname: string
}) {
  const spec = resolveConsoleNavLink(profile, id)
  const active = isConsoleNavActive(profile, pathname, id)
  const label = CONSOLE_NAV[id].label
  const icon = ICONS[id]
  const isBack = id === "identityLanding"
  const className = isBack
    ? cn(
        "flex items-center gap-3 rounded-md px-3 py-2.5 text-sm text-sidebar-foreground/80 transition-colors hover:bg-sidebar-accent/80 hover:text-sidebar-foreground",
        active && "bg-sidebar-accent font-medium text-sidebar-accent-foreground"
      )
    : cn(
        "flex min-h-10 items-center gap-3 rounded-md px-3 py-2.5 text-sm font-medium leading-snug transition-colors",
        active
          ? "bg-sidebar-accent font-semibold text-sidebar-accent-foreground shadow-sm"
          : "text-sidebar-foreground/85 hover:bg-sidebar-accent/60 hover:text-sidebar-foreground"
      )

  if (spec.kind === "next") {
    return (
      <Link href={spec.href} className={className}>
        {icon}
        {label}
      </Link>
    )
  }

  return (
    <a href={spec.href} className={className}>
      {icon}
      {label}
    </a>
  )
}

export type ConsoleSidebarProps = {
  profile: ConsoleProfile
  /** Per-service BFF logout endpoint path. */
  logoutApiPath?: string
  /** Redirect path after logout request. */
  logoutRedirectPath?: string
}

export function ConsoleSidebar({
  profile,
  logoutApiPath = "/api/auth/logout",
  logoutRedirectPath = "/",
}: ConsoleSidebarProps) {
  const pathname = usePathname() ?? ""
  const [logoutPending, setLogoutPending] = React.useState(false)

  async function handleLogout() {
    setLogoutPending(true)
    try {
      await fetch(logoutApiPath, {
        method: "POST",
        credentials: "include",
      })
    } catch {
      // Network failure should not block redirect to sign-in entry.
    } finally {
      setLogoutPending(false)
    }
    try {
      if (typeof sessionStorage !== "undefined") {
        sessionStorage.clear()
      }
      if (typeof localStorage !== "undefined") {
        localStorage.clear()
      }
    } catch {
      // Storage 접근 오류가 있어도 로그아웃 리다이렉트는 진행한다.
    }
    window.location.assign(logoutRedirectPath)
  }

  return (
    <aside className="flex h-full min-h-0 w-64 min-w-[240px] max-w-[280px] shrink-0 flex-col border-r border-sidebar-border bg-sidebar text-sidebar-foreground">
      <div className="border-b border-sidebar-border px-4 py-4">
        <p className="text-xs font-medium uppercase tracking-wide text-sidebar-foreground/60">콘솔</p>
        <p className="mt-1 text-sm font-semibold leading-tight tracking-tight text-sidebar-foreground">
          사용량 모니터
        </p>
      </div>

      <div className="px-3 pt-3 pb-1">
        <NavRow profile={profile} id="identityLanding" pathname={pathname} />
      </div>

      <nav className="flex flex-1 flex-col gap-1 px-3 pb-3" aria-label="앱 메뉴">
        {CONSOLE_MAIN_NAV_ORDER.map((id) => (
          <NavRow key={id} profile={profile} id={id} pathname={pathname} />
        ))}
      </nav>

      <div className="mt-auto border-t border-sidebar-border px-3 py-4">
        <div className="flex flex-col gap-2">
          <Button
            type="button"
            variant="outline"
            className="h-9 w-full justify-center text-sm"
            disabled={logoutPending}
            onClick={handleLogout}
          >
            {logoutPending ? "로그아웃 중..." : "로그아웃"}
          </Button>
        </div>
      </div>
    </aside>
  )
}
