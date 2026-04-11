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

import { LogoutButton } from "@/components/auth/logout-button"
import { cn } from "@/lib/utils"

type NavItem = {
  href: string
  label: string
  icon: ReactNode
}

const NAV_ITEMS: NavItem[] = [
  { href: "/dashboard", label: "사용량", icon: <LayoutDashboard className="size-4" aria-hidden /> },
  { href: "/billing", label: "지출", icon: <Wallet className="size-4" aria-hidden /> },
  { href: "/settings", label: "설정", icon: <Settings className="size-4" aria-hidden /> },
  { href: "/organizations", label: "조직", icon: <Building2 className="size-4" aria-hidden /> },
  { href: "/teams", label: "팀", icon: <UsersRound className="size-4" aria-hidden /> },
]

function navActive(pathname: string, href: string): boolean {
  if (pathname === href) return true
  if (href !== "/" && pathname.startsWith(`${href}/`)) return true
  return false
}

export function DashboardSidebar() {
  const pathname = usePathname() ?? ""

  return (
    <aside className="flex h-full min-h-0 w-56 shrink-0 flex-col border-r border-border bg-muted/25">
      <div className="border-b border-border px-3 py-4">
        <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">콘솔</p>
        <p className="mt-0.5 text-sm font-semibold tracking-tight">사용량 모니터</p>
      </div>

      <div className="px-2 py-2">
        <Link
          href="/"
          className="flex items-center gap-2 rounded-lg px-2.5 py-2 text-sm text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
        >
          <ChevronLeft className="size-4 shrink-0" aria-hidden />
          홈으로
        </Link>
      </div>

      <nav className="flex flex-1 flex-col gap-0.5 px-2 pb-2" aria-label="앱 메뉴">
        {NAV_ITEMS.map((item) => {
          const active = navActive(pathname, item.href)
          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                "flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-sm font-medium transition-colors",
                active
                  ? "bg-muted text-foreground"
                  : "text-muted-foreground hover:bg-muted/80 hover:text-foreground"
              )}
            >
              {item.icon}
              {item.label}
            </Link>
          )
        })}
      </nav>

      <div className="mt-auto border-t border-border px-2 py-3">
        <LogoutButton variant="outline" className="h-9 w-full justify-center text-sm" />
      </div>
    </aside>
  )
}
