"use client";

import dynamic from "next/dynamic";
import { RemoteErrorBoundary } from "@/components/remote-error-boundary";

const TeamManagement = dynamic(() => import("team/TeamManagement"), {
  ssr: false,
  loading: () => <p className="text-sm text-muted-foreground">Team remote loading...</p>,
});
const TeamUsageDashboard = dynamic(() => import("usage/TeamUsageDashboard"), {
  ssr: false,
  loading: () => <p className="text-sm text-muted-foreground">Usage remote loading...</p>,
});

export function TeamPageContent() {
  return (
    <div className="flex h-full w-full gap-4 p-4">
      <div className="flex w-1/4 min-w-[240px] flex-col gap-4">
        <section className="flex-1 overflow-y-auto rounded-lg border border-border bg-card p-4">
          <RemoteErrorBoundary
            fallback={<p className="text-sm text-muted-foreground">Team remote를 불러오지 못했습니다.</p>}
          >
            <TeamManagement />
          </RemoteErrorBoundary>
        </section>
      </div>

      <div className="flex w-3/4 min-w-0 flex-col gap-4">
        <section className="h-fit rounded-lg border border-border bg-card p-4">
          <p className="text-sm text-muted-foreground">
            TeamInfo 영역은 현재 team-service 단일 컴포넌트(TeamManagement)에 포함되어 있습니다.
          </p>
        </section>
        <section className="flex-1 rounded-lg border border-border bg-card p-4">
          <RemoteErrorBoundary
            fallback={<p className="text-sm text-muted-foreground">Usage remote를 불러오지 못했습니다.</p>}
          >
            <TeamUsageDashboard />
          </RemoteErrorBoundary>
        </section>
      </div>
    </div>
  );
}
