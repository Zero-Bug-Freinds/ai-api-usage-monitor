"use client";

import type { ReactNode } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { ConsoleShell, ConsoleSidebar } from "@ai-usage/shell";

export function HostShellLayout({ children }: { children: ReactNode }) {
  const pathname = usePathname() ?? "";

  return (
    <ConsoleShell
      sidebar={
        <div className="flex h-full min-h-0 w-64 min-w-[240px] max-w-[280px] shrink-0 flex-col">
          <ConsoleSidebar
            profile="identity"
            footer={<p className="text-xs text-muted-foreground">web-host · module federation</p>}
          />
          <div className="border-t border-sidebar-border px-3 py-3">
            <Link
              href="/team"
              className={`block rounded-md px-3 py-2.5 text-sm font-medium ${
                pathname === "/team" ? "bg-sidebar-accent text-sidebar-accent-foreground" : "hover:bg-sidebar-accent/60"
              }`}
            >
              팀(/team)
            </Link>
          </div>
        </div>
      }
    >
      {children}
    </ConsoleShell>
  );
}
