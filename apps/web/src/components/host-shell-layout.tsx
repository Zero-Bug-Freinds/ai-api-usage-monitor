"use client";

import type { ReactNode } from "react";
import Link from "next/link";

export function HostShellLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen w-full min-w-0 bg-background">
      <div className="flex h-full min-h-0 w-64 min-w-[240px] max-w-[280px] shrink-0 flex-col">
        <aside className="flex h-full min-h-0 w-full flex-col border-r border-sidebar-border bg-sidebar text-sidebar-foreground">
          <div className="border-b border-sidebar-border px-4 py-4">
            <p className="text-xs font-medium uppercase tracking-wide text-sidebar-foreground/60">콘솔</p>
            <p className="mt-1 text-sm font-semibold leading-tight tracking-tight text-sidebar-foreground">
              사용량 모니터
            </p>
          </div>
          <nav className="flex flex-1 flex-col gap-1 px-3 py-3" aria-label="앱 메뉴">
            <Link href="/" className="block rounded-md px-3 py-2.5 text-sm font-medium hover:bg-sidebar-accent/60">
              홈(/)
            </Link>
            <Link href="/team" className="block rounded-md px-3 py-2.5 text-sm font-medium hover:bg-sidebar-accent/60">
              팀(/team)
            </Link>
          </nav>
          <div className="mt-auto border-t border-sidebar-border px-3 py-4">
            <p className="text-xs text-muted-foreground">web-host · module federation</p>
          </div>
        </aside>
      </div>
      <main className="flex min-h-screen min-w-0 flex-1 flex-col overflow-x-auto overflow-y-auto">
        <div className="mx-auto min-h-full w-full max-w-6xl flex-1 px-4 py-6 sm:px-6 lg:px-8">{children}</div>
      </main>
    </div>
  );
}
