"use client";

import type { ReactNode } from "react";
import Link from "next/link";
import { ChevronLeft, Wallet } from "lucide-react";

type BillingShellProps = {
  children: ReactNode;
};

function usageDashboardHref(): string {
  const base = (process.env.NEXT_PUBLIC_USAGE_WEB_ORIGIN ?? "").replace(/\/$/, "");
  const basePath = (process.env.NEXT_PUBLIC_USAGE_BASE_PATH ?? "/dashboard").replace(/\/$/, "");
  if (!base) return basePath || "/";
  return `${base}${basePath}` || base;
}

export function BillingShell({ children }: BillingShellProps) {
  return (
    <div className="flex min-h-screen w-full bg-background">
      <aside className="flex h-full min-h-0 w-56 shrink-0 flex-col border-r border-border bg-muted/25">
        <div className="border-b border-border px-3 py-4">
          <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">콘솔</p>
          <p className="mt-0.5 text-sm font-semibold tracking-tight">지출</p>
        </div>
        <div className="px-2 py-2">
          <Link
            href={usageDashboardHref()}
            className="flex items-center gap-2 rounded-lg px-2.5 py-2 text-sm text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
          >
            <ChevronLeft className="size-4 shrink-0" aria-hidden />
            사용량으로
          </Link>
        </div>
        <div className="flex flex-1 items-start px-2 pt-2">
          <div className="flex w-full items-center gap-2 rounded-lg bg-muted px-2.5 py-2 text-sm font-medium text-foreground">
            <Wallet className="size-4 shrink-0" aria-hidden />
            지출 대시보드
          </div>
        </div>
      </aside>
      <main className="min-h-screen min-w-0 flex-1 overflow-x-auto">
        <div className="mx-auto min-h-full max-w-6xl px-4 py-6 sm:px-6 lg:px-8">{children}</div>
      </main>
    </div>
  );
}
