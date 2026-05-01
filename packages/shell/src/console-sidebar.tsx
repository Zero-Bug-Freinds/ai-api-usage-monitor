"use client"

import * as React from "react"
import type { ReactNode } from "react"
import Link from "next/link"
import { usePathname } from "next/navigation"
import {
  Bell,
  Building2,
  ChevronLeft,
  ChevronRight,
  LayoutDashboard,
  ScrollText,
  Settings,
  UsersRound,
  Wallet,
} from "lucide-react"

import { dispatchLogoutEvent } from "@ai-usage/team-workspace-cache"
import { Button, cn } from "@ai-usage/ui"
import type { ConsoleNavId, ConsoleProfile } from "./console-nav"
import { CONSOLE_MAIN_NAV_ORDER, CONSOLE_NAV } from "./console-nav"
import { isConsoleNavActive, resolveConsoleNavLink } from "./console-nav"

type TeamSidebarItem = {
  id: string
  name: string
}

const TEAM_SUB_MENU = [
  { key: "dashboard", label: "대시보드", suffix: "dashboard" },
  { key: "members", label: "멤버 관리", suffix: "members" },
  { key: "apiKeys", label: "API 및 설정", suffix: "api-keys" },
] as const

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
  unreadCount,
}: {
  profile: ConsoleProfile
  id: ConsoleNavId
  pathname: string
  unreadCount?: number | null
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
        {id === "notifications" && typeof unreadCount === "number" && unreadCount > 0 ? (
          <span
            className="ml-2 inline-flex min-w-5 items-center justify-center rounded-full bg-destructive px-1.5 text-[11px] font-semibold leading-5 text-destructive-foreground"
            aria-label={`미확인 알림 ${unreadCount}개`}
          >
            {unreadCount}
          </span>
        ) : null}
      </Link>
    )
  }

  return (
    <a href={spec.href} className={className}>
      {icon}
      {label}
      {id === "notifications" && typeof unreadCount === "number" && unreadCount > 0 ? (
        <span
          className="ml-2 inline-flex min-w-5 items-center justify-center rounded-full bg-destructive px-1.5 text-[11px] font-semibold leading-5 text-destructive-foreground"
          aria-label={`미확인 알림 ${unreadCount}개`}
        >
          {unreadCount}
        </span>
      ) : null}
    </a>
  )
}

export type ConsoleSidebarProps = {
  profile: ConsoleProfile
  teams?: TeamSidebarItem[]
  /** Per-service BFF logout endpoint path. */
  logoutApiPath?: string
  /** Redirect path after logout request. */
  logoutRedirectPath?: string
  /**
   * When set, team submenu links use this URL shape (e.g. `/teams?viewTeamId=…&tab=…`).
   * If omitted, legacy `/teams/{id}/{suffix}` links are used.
   */
  buildTeamSubmenuHref?: (teamId: string, suffix: (typeof TEAM_SUB_MENU)[number]["suffix"]) => string
  /** When set, expands the matching team row (e.g. `viewTeamId` query). */
  teamExpandedTeamId?: string | null
  /** Highlights the active team submenu entry. */
  teamSubmenuActive?: { teamId: string; suffix: string } | null
}

export function ConsoleSidebar({
  profile,
  teams = [],
  logoutApiPath = "/api/auth/logout",
  logoutRedirectPath = "/",
  buildTeamSubmenuHref,
  teamExpandedTeamId,
  teamSubmenuActive,
}: ConsoleSidebarProps) {
  const pathname = usePathname() ?? ""
  const [logoutPending, setLogoutPending] = React.useState(false)
  const [expandedTeamId, setExpandedTeamId] = React.useState<string | null>(null)
  const [unreadCount, setUnreadCount] = React.useState<number | null>(null)

  React.useEffect(() => {
    if (teamExpandedTeamId != null && teamExpandedTeamId !== "") {
      setExpandedTeamId(teamExpandedTeamId)
      return
    }
    const match = pathname.match(/^\/teams\/([^/]+)/)
    if (match?.[1]) {
      setExpandedTeamId((prev) => prev ?? decodeURIComponent(match[1]))
    }
  }, [pathname, teamExpandedTeamId])

  React.useEffect(() => {
    let cancelled = false
    let timer: ReturnType<typeof setInterval> | null = null

    const rawPollMs = process.env.NEXT_PUBLIC_NOTIFICATION_POLL_MS
    const pollMs = Math.max(1_000, Number.parseInt(rawPollMs ?? "", 10) || 20_000)

    async function fetchUnreadCount() {
      try {
        const res = await fetch("/notifications/api/notification/in-app-notifications/unread-count", {
          method: "GET",
          credentials: "include",
          cache: "no-store",
        })

        if (!res.ok) {
          if (!cancelled) setUnreadCount(null)
          return
        }

        const json: unknown = await res.json()
        const value = (json as { unreadCount?: unknown } | null)?.unreadCount
        if (typeof value !== "number" || !Number.isFinite(value) || value < 0) {
          if (!cancelled) setUnreadCount(null)
          return
        }

        if (!cancelled) setUnreadCount(value)
      } catch {
        if (!cancelled) setUnreadCount(null)
      }
    }

    void fetchUnreadCount()
    timer = setInterval(fetchUnreadCount, pollMs)

    return () => {
      cancelled = true
      if (timer) clearInterval(timer)
    }
  }, [])

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
        <NavRow profile={profile} id="identityLanding" pathname={pathname} unreadCount={unreadCount} />
      </div>

      <nav className="flex flex-1 flex-col gap-1 px-3 pb-3" aria-label="앱 메뉴">
        {CONSOLE_MAIN_NAV_ORDER.map((id) => (
          <NavRow key={id} profile={profile} id={id} pathname={pathname} unreadCount={unreadCount} />
        ))}
        {teams.length > 0 ? (
          <div className="mt-2 space-y-1 border-t border-sidebar-border pt-2">
            {teams.map((team) => {
              const isExpanded = expandedTeamId === team.id
              return (
                <div key={team.id} className="rounded-md">
                  <button
                    type="button"
                    onClick={() => setExpandedTeamId((prev) => (prev === team.id ? null : team.id))}
                    className={cn(
                      "flex min-h-10 w-full items-center justify-between gap-2 rounded-md px-3 py-2.5 text-left text-sm font-medium transition-colors",
                      isExpanded
                        ? "bg-sidebar-accent font-semibold text-sidebar-accent-foreground shadow-sm"
                        : "text-sidebar-foreground/85 hover:bg-sidebar-accent/60 hover:text-sidebar-foreground"
                    )}
                    aria-expanded={isExpanded}
                    aria-controls={`team-submenu-${team.id}`}
                  >
                    <span className="truncate">{team.name}</span>
                    <ChevronRight
                      className={cn("size-4 shrink-0 transition-transform duration-200", isExpanded && "rotate-90")}
                      aria-hidden
                    />
                  </button>

                  <div
                    id={`team-submenu-${team.id}`}
                    className={cn(
                      "grid overflow-hidden transition-all duration-200",
                      isExpanded ? "grid-rows-[1fr] opacity-100" : "grid-rows-[0fr] opacity-0"
                    )}
                  >
                    <ul className="min-h-0 space-y-1 py-1 pl-3">
                      {TEAM_SUB_MENU.map((item) => {
                        const href = buildTeamSubmenuHref
                          ? buildTeamSubmenuHref(team.id, item.suffix)
                          : `/teams/${encodeURIComponent(team.id)}/${item.suffix}`
                        const active = teamSubmenuActive
                          ? teamSubmenuActive.teamId === team.id && teamSubmenuActive.suffix === item.suffix
                          : pathname === href || pathname.startsWith(`${href}/`)
                        return (
                          <li key={item.key}>
                            <Link
                              href={href}
                              className={cn(
                                "block rounded-md px-3 py-2 text-xs transition-colors",
                                active
                                  ? "bg-sidebar-accent font-semibold text-sidebar-accent-foreground"
                                  : "text-sidebar-foreground/80 hover:bg-sidebar-accent/60 hover:text-sidebar-foreground"
                              )}
                            >
                              {item.label}
                            </Link>
                          </li>
                        )
                      })}
                    </ul>
                  </div>
                </div>
              )
            })}
          </div>
        ) : null}
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
