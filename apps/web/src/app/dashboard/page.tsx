import type { ReactNode } from "react"
import Link from "next/link"

import { LogoutButton } from "@/components/auth/logout-button"

export default function DashboardPage() {
  return (
    <div className="mx-auto flex min-h-full max-w-lg flex-col gap-6 px-4 py-12">
      <div className="space-y-2">
        <h1 className="text-2xl font-semibold tracking-tight">대시보드</h1>
        <p className="text-sm text-muted-foreground">로그인된 사용자 영역입니다.</p>
      </div>
      <div className="flex flex-wrap items-center gap-3">
        <LogoutButton />
        <ButtonAsLink href="/">홈으로</ButtonAsLink>
      </div>
    </div>
  )
}

function ButtonAsLink({ href, children }: { href: string; children: ReactNode }) {
  return (
    <Link
      href={href}
      className="inline-flex h-9 items-center justify-center rounded-md border border-input bg-background px-3 text-sm font-medium shadow-sm transition-colors hover:bg-accent hover:text-accent-foreground"
    >
      {children}
    </Link>
  )
}
