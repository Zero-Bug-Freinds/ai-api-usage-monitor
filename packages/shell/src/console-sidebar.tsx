"use client"

import type { ReactNode } from "react"
import Link from "next/link"
import { usePathname } from "next/navigation"
import {
  Building2,
  ChevronLeft,
  LayoutDashboard,
  Settings,
  UsersRound,
  Wallet,
} from "lucide-react"

import { cn } from "@ai-usage/ui"

import type { ConsoleNavId, ConsoleProfile } from "./console-nav-model"
import { CONSOLE_MAIN_NAV_ORDER, CONSOLE_NAV } from "./console-nav-model"
import { isConsoleNavActive, resolveConsoleNavLink } from "./console-nav-resolve"

const ICONS: Record<ConsoleNavId, ReactNode> = {
  usageHome: <LayoutDashboard className="size-4" aria-hidden />,
  billingHome: <Wallet className="size-4" aria-hidden />,
  settings: <Settings className="size-4" aria-hidden />,
  organizations: <Building2 className="size-4" aria-hidden />,
  teams: <UsersRound className="size-4" aria-hidden />,
  identityLanding: <ChevronLeft className="size-4 shrink-0" aria-hidden />,
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
        "flex items-center gap-2 rounded-lg px-2.5 py-2 text-sm text-muted-foreground transition-colors hover:bg-muted hover:text-foreground",
        active && "bg-muted text-foreground"
      )
    : cn(
        "flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-sm font-medium transition-colors",
        active ? "bg-muted text-foreground" : "text-muted-foreground hover:bg-muted/80 hover:text-foreground"
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
  /** Logout button or other footer actions from the host app. */
  footer: ReactNode
}

export function ConsoleSidebar({ profile, footer }: ConsoleSidebarProps) {
  const pathname = usePathname() ?? ""

  return (
    <aside className="flex h-full min-h-0 w-56 shrink-0 flex-col border-r border-border bg-muted/25">
      <div className="border-b border-border px-3 py-4">
        <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">콘솔</p>
        <p className="mt-0.5 text-sm font-semibold tracking-tight">사용량 모니터</p>
      </div>

      <div className="px-2 py-2">
        <NavRow profile={profile} id="identityLanding" pathname={pathname} />
      </div>

      <nav className="flex flex-1 flex-col gap-0.5 px-2 pb-2" aria-label="앱 메뉴">
        {CONSOLE_MAIN_NAV_ORDER.map((id) => (
          <NavRow key={id} profile={profile} id={id} pathname={pathname} />
        ))}
      </nav>

      <div className="mt-auto border-t border-border px-2 py-3">{footer}</div>
    </aside>
  )
}
