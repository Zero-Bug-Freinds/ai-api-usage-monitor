"use client";

import dynamic from "next/dynamic";
import * as React from "react";
import { Button, cn } from "@ai-usage/ui";
import { usePagesHostRouter } from "@/context/pages-host-router-context";
import { useTeamWorkspace } from "@/components/team-workspace-context";
import { RemoteErrorBoundary } from "@/components/remote-error-boundary";
import { normalizeTab, type TeamRouteSection } from "@/components/team-route-types";

export type { TeamRouteSection } from "@/components/team-route-types";

/**
 * `usage/` federation dynamic은 `./team-mf-remotes` 청크에만 둔다(Task37-8).
 * `team/TeamManagement`는 좌측 전용으로 이 파일에서만 로드해 동일 리모트 이중 등록을 피한다(Task37-12).
 */
const TeamMfRemotesLazy = dynamic(
  () => import("./team-mf-remotes").then((m) => ({ default: m.TeamMfRemotes })),
  {
    ssr: false,
    loading: () => <p className="text-sm text-muted-foreground">원격 모듈을 불러오는 중…</p>,
  }
);

const TeamManagementLazy = dynamic(() => import("team/TeamManagement"), {
  ssr: false,
  loading: () => <p className="text-sm text-muted-foreground">Team remote loading…</p>,
});

/**
 * 팀 콘솔(Task37-12): 좌측 MF `TeamManagement` 통째로, 우측 usage MF만 + 상단 탭.
 */
export function TeamPageContent() {
  const router = usePagesHostRouter();
  const { teams, syncError } = useTeamWorkspace();
  const [mfChunkAllowed, setMfChunkAllowed] = React.useState(false);

  React.useEffect(() => {
    setMfChunkAllowed(true);
  }, []);

  if (!router.isReady) {
    return <p className="text-sm text-muted-foreground">라우터 준비 중…</p>;
  }

  const viewTeamId =
    typeof router.query.viewTeamId === "string"
      ? router.query.viewTeamId
      : Array.isArray(router.query.viewTeamId)
        ? router.query.viewTeamId[0] ?? ""
        : "";

  const tab = normalizeTab(router.query.tab);
  const usageResetKey = `${router.asPath}|${teams.length}|${tab}`;

  function goTab(next: TeamRouteSection) {
    const q: Record<string, string> = { tab: next };
    if (viewTeamId !== "") q.viewTeamId = viewTeamId;
    void router.replace({ pathname: "/", query: q }, undefined, { shallow: true });
  }

  const teamMfResetKey = `${viewTeamId}|${teams.length}`;

  return (
    <div className="flex min-h-0 w-full flex-1 flex-col gap-0 md:flex-row md:gap-6">
      <aside className="team-team-mf-slot shrink-0 border-b border-zinc-200 bg-gray-50 md:sticky md:top-16 md:max-h-[calc(100vh-5rem)] md:self-start md:overflow-y-auto md:border-b-0 md:border-r md:border-zinc-200">
        {mfChunkAllowed ? (
          <RemoteErrorBoundary
            resetKey={teamMfResetKey}
            fallback={<p className="p-3 text-sm text-muted-foreground">Team remote를 불러오지 못했습니다.</p>}
          >
            <TeamManagementLazy />
          </RemoteErrorBoundary>
        ) : (
          <p className="p-3 text-sm text-muted-foreground">원격 모듈을 준비하는 중…</p>
        )}
      </aside>

      <div className="host-remote-slot flex min-h-0 min-w-0 flex-1 flex-col gap-4">
        <div className="flex flex-wrap gap-2 border-b border-border pb-3">
          <Button
            type="button"
            size="sm"
            variant={tab === "dashboard" ? "default" : "outline"}
            className={cn(tab === "dashboard" && "font-semibold")}
            onClick={() => goTab("dashboard")}
          >
            팀 대시보드
          </Button>
          <Button
            type="button"
            size="sm"
            variant={tab === "memberDetail" ? "default" : "outline"}
            className={cn(tab === "memberDetail" && "font-semibold")}
            onClick={() => goTab("memberDetail")}
          >
            멤버 상세
          </Button>
        </div>

        {syncError ? (
          <p className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900">
            팀 목록 동기화: {syncError} (저장된 목록으로 계속합니다)
          </p>
        ) : null}

        {process.env.NODE_ENV === "development" ? (
          <p className="text-xs text-muted-foreground" data-testid="mf-usage-remote-hint">
            [dev] usage MF 베이스:{" "}
            <code className="rounded bg-muted px-1 py-0.5">
              {process.env.NEXT_PUBLIC_MFE_USAGE_REMOTE_URL ?? "http://localhost:8888/mfe/usage (기본값)"}
            </code>
            …/remoteEntry.js 로 로드됩니다.
          </p>
        ) : null}

        <div className="min-h-0 flex-1 overflow-y-auto">
          {mfChunkAllowed ? (
            <TeamMfRemotesLazy viewTeamId={viewTeamId} teams={teams} usageResetKey={usageResetKey} />
          ) : (
            <p className="text-sm text-muted-foreground">원격 모듈을 준비하는 중…</p>
          )}
        </div>
      </div>
    </div>
  );
}
