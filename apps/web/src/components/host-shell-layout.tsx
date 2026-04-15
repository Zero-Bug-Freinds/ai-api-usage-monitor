"use client";

import type { ReactNode } from "react";
import Link from "next/link";
import { useRouter } from "next/router";
import { ConsoleShell } from "@ai-usage/shell";

export function HostShellLayout({ children }: { children: ReactNode }) {
  const { pathname } = useRouter();

  return (
    <ConsoleShell
      sidebar={
        <div className="flex h-full min-h-0 w-64 min-w-[240px] max-w-[280px] shrink-0 flex-col">
          <aside className="flex h-full min-h-0 w-full flex-col border-r border-sidebar-border bg-sidebar text-sidebar-foreground">
            <div className="border-b border-sidebar-border px-4 py-4">
              <p className="text-xs font-medium uppercase tracking-wide text-sidebar-foreground/60">콘솔</p>
              <p className="mt-1 text-sm font-semibold leading-tight tracking-tight text-sidebar-foreground">
                사용량 모니터
              </p>
            </div>
            <nav className="flex flex-1 flex-col gap-1 px-3 py-3" aria-label="앱 메뉴">
              <Link
                href="/"
                className={`block rounded-md px-3 py-2.5 text-sm font-medium ${
                  pathname === "/" ? "bg-sidebar-accent text-sidebar-accent-foreground" : "hover:bg-sidebar-accent/60"
                }`}
              >
                홈(/)
              </Link>
              <Link
                href="/team"
                className={`block rounded-md px-3 py-2.5 text-sm font-medium ${
                  pathname === "/team" ? "bg-sidebar-accent text-sidebar-accent-foreground" : "hover:bg-sidebar-accent/60"
                }`}
              >
                팀(/team)
              </Link>
            </nav>
            <div className="mt-auto border-t border-sidebar-border px-3 py-4">
              <p className="text-xs text-muted-foreground">web-host · module federation</p>
            </div>
          </aside>
        </div>
      }
    >
      {children}
    </ConsoleShell>
  );
}
