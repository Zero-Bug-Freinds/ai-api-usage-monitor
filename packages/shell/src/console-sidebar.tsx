"use client"

import * as React from "react"
import type { ReactNode } from "react"
import Link from "next/link"
import type { NextRouter } from "next/router"
import { useRouter as usePagesRouter } from "next/router"
import {
  Bell,
  Building2,
  ChevronLeft,
  ChevronRight,
  LayoutDashboard,
  MessageCircle,
  ScrollText,
  Settings,
  UsersRound,
  Wallet,
} from "lucide-react"

import { Button, cn } from "@ai-usage/ui"
import { ConsoleInternalNavLink } from "./console-internal-nav-link"
import type { ConsoleNavId, ConsoleProfile } from "./console-nav"
import { CONSOLE_MAIN_NAV_ORDER, CONSOLE_NAV } from "./console-nav"
import { isConsoleNavActive, notificationUnreadCountFetchUrl, resolveConsoleNavLink } from "./console-nav"

type TeamSidebarItem = {
  id: string
  name: string
}

const TEAM_SUB_MENU = [
  { key: "dashboard", label: "대시보드", suffix: "dashboard" },
  { key: "memberDetail", label: "멤버 상세", suffix: "memberDetail" },
] as const
const AI_USAGE_LOGOUT_EVENT = "ai-usage:logout"
const LOCAL_STORAGE_KEYS_TO_PRESERVE_ON_LOGOUT = ["team.dismissedExpiredInvitationNoticeIds"] as const

const ICONS: Record<ConsoleNavId, ReactNode> = {
  usageHome: <LayoutDashboard className="size-[1.125rem] shrink-0" aria-hidden />,
  usageLog: <ScrollText className="size-[1.125rem] shrink-0" aria-hidden />,
  billingHome: <Wallet className="size-[1.125rem] shrink-0" aria-hidden />,
  notifications: <Bell className="size-[1.125rem] shrink-0" aria-hidden />,
  settings: <Settings className="size-[1.125rem] shrink-0" aria-hidden />,
  organizations: <Building2 className="size-[1.125rem] shrink-0" aria-hidden />,
  teams: <UsersRound className="size-[1.125rem] shrink-0" aria-hidden />,
  assistant: <MessageCircle className="size-[1.125rem] shrink-0" aria-hidden />,
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
  /** 팀 메뉴는 항상 풀 페이지 이동(anchor); Next Link 클라이언트 전환 방지 */
  const forceTeamsAnchor = id === "teams"
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

  const notificationBadge =
    id === "notifications" && typeof unreadCount === "number" && unreadCount > 0 ? (
      <span
        className="ml-2 inline-flex min-w-5 items-center justify-center rounded-full bg-destructive px-1.5 text-[11px] font-semibold leading-5 text-destructive-foreground"
        aria-label={`미확인 알림 ${unreadCount}개`}
      >
        {unreadCount}
      </span>
    ) : null

  return (
    <ConsoleInternalNavLink spec={spec} forceAnchor={forceTeamsAnchor} className={className}>
      {icon}
      {label}
      {notificationBadge}
    </ConsoleInternalNavLink>
  )
}

export type ConsoleSidebarProps = {
  /**
   * Pages 호스트가 `_app`의 `router`를 넘기면 MF `loadShare` 직후에도 `useRouter` 컨텍스트 없이 동작한다.
   * 생략 시 내부에서 `usePagesRouter()`를 쓴다(별도 컴포넌트로 훅 규칙 유지).
   */
  pagesRouter?: NextRouter
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
  /**
   * Pages Router에서 `router.isReady` 전에는 false로 두어 팀 서브메뉴 클릭을 막는다.
   * App Router는 생략(기본 true).
   */
  navigationReady?: boolean
  /**
   * false면 사이드바 내 per-team 확장·서브메뉴 블록을 렌더하지 않는다(호스트 팀 페이지에서 목록을 본문에 둘 때).
   */
  showTeamSidebarSection?: boolean
}

export function ConsoleSidebarInner({
  pathname,
  profile,
  teams = [],
  logoutApiPath = "/api/auth/logout",
  logoutRedirectPath = "/",
  buildTeamSubmenuHref,
  teamExpandedTeamId,
  teamSubmenuActive,
  navigationReady = true,
  showTeamSidebarSection = true,
}: ConsoleSidebarProps & { pathname: string }) {
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
        const res = await fetch(notificationUnreadCountFetchUrl(profile), {
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
  }, [profile])

  function clearLocalStoragePreservingKeys() {
    if (typeof localStorage === "undefined") return
    const preservedEntries = LOCAL_STORAGE_KEYS_TO_PRESERVE_ON_LOGOUT
      .map((key) => [key, localStorage.getItem(key)] as const)
      .filter((entry) => entry[1] !== null)
    localStorage.clear()
    for (const [key, value] of preservedEntries) {
      if (value !== null) {
        localStorage.setItem(key, value)
      }
    }
  }

  async function handleLogout() {
    setLogoutPending(true)
    try {
      if (typeof window !== "undefined") {
        window.dispatchEvent(new CustomEvent(AI_USAGE_LOGOUT_EVENT, { bubbles: true }))
      }
    } catch {
      // 이벤트 발행 실패 시에도 로그아웃 흐름은 계속 진행한다.
    }
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
      clearLocalStoragePreservingKeys()
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
        {showTeamSidebarSection && teams.length > 0 ? (
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
                              prefetch={navigationReady}
                              tabIndex={navigationReady ? 0 : -1}
                              aria-disabled={!navigationReady}
                              onClick={(e) => {
                                if (!navigationReady) e.preventDefault()
                              }}
                              className={cn(
                                "block rounded-md px-3 py-2 text-xs transition-colors",
                                active
                                  ? "bg-sidebar-accent font-semibold text-sidebar-accent-foreground"
                                  : "text-sidebar-foreground/80 hover:bg-sidebar-accent/60 hover:text-sidebar-foreground",
                                !navigationReady && "pointer-events-none opacity-50"
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

function ConsoleSidebarPagesCore(
  props: Omit<ConsoleSidebarProps, "pagesRouter"> & { pagesRouter: NextRouter },
) {
  const { pagesRouter: router, ...innerProps } = props
  if (!router.isReady) {
    return (
      <aside
        className="flex h-full min-h-0 w-64 min-w-[240px] max-w-[280px] shrink-0 flex-col border-r border-sidebar-border bg-sidebar animate-pulse"
        aria-hidden
      />
    )
  }
  const pathname = `${(router.basePath ?? "").replace(/\/$/, "")}${router.asPath ?? ""}` || "/"
  return <ConsoleSidebarInner pathname={pathname} {...innerProps} navigationReady />
}

function ConsoleSidebarPagesWithHook(props: ConsoleSidebarProps) {
  const router = usePagesRouter()
  return <ConsoleSidebarPagesCore {...props} pagesRouter={router} />
}

/**
 * Pages Router 호스트(예: web-host `basePath=/teams`)용 — `next/navigation` 훅 없이 경로를 맞춘다.
 * `asPath`에는 basePath가 포함되지 않으므로, 엣지 단일 도메인 기준으로 `/teams`와 동일한 활성 판별을 위해 합친다.
 */
export function ConsoleSidebarPages(props: ConsoleSidebarProps) {
  if (props.pagesRouter) {
    return <ConsoleSidebarPagesCore {...props} pagesRouter={props.pagesRouter} />
  }
  return <ConsoleSidebarPagesWithHook {...props} />
}
