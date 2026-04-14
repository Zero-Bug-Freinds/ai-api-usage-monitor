"use client";

import dynamic from "next/dynamic";

const TeamManagement = dynamic(() => import("team/TeamManagement"), { ssr: false });
const TeamUsageDashboard = dynamic(() => import("usage/TeamUsageDashboard"), { ssr: false });

export function TeamPageContent() {
  return (
    <div className="flex h-full w-full gap-4 p-4">
      <div className="flex w-1/4 min-w-[240px] flex-col gap-4">
        <section className="flex-1 overflow-y-auto rounded-lg border border-border bg-card p-4">
          <TeamManagement />
        </section>
      </div>

      <div className="flex w-3/4 min-w-0 flex-col gap-4">
        <section className="h-fit rounded-lg border border-border bg-card p-4">
          <p className="text-sm text-muted-foreground">
            TeamInfo 영역은 현재 team-service 단일 컴포넌트(TeamManagement)에 포함되어 있습니다.
          </p>
        </section>
        <section className="flex-1 rounded-lg border border-border bg-card p-4">
          <TeamUsageDashboard />
        </section>
      </div>
    </div>
  );
}
