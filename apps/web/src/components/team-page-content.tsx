"use client";

import dynamic from "next/dynamic";
import * as React from "react";
import { Button, cn } from "@ai-usage/ui";
import { usePagesHostRouter } from "@/context/pages-host-router-context";
import { useTeamWorkspace } from "@/components/team-workspace-context";
import { normalizeTab, type TeamRouteSection } from "@/components/team-route-types";

export type { TeamRouteSection } from "@/components/team-route-types";

/**
 * `team/`·`usage/` federation dynamic은 `./team-mf-remotes` 청크에만 두고,
 * 클라이언트 마운트 후에만 그 청크를 로드해 모듈 평가 시점 경합을 줄인다(Task37-8).
 */
const TeamMfRemotesLazy = dynamic(
  () => import("./team-mf-remotes").then((m) => ({ default: m.TeamMfRemotes })),
  {
    ssr: false,
    loading: () => <p className="text-sm text-muted-foreground">원격 모듈을 불러오는 중…</p>,
  }
);

/**
 * 팀 콘솔: `TeamManagement` 리모트가 이미 좌측 팀 목록·우측 상세를 포함하므로(Task37-10),
 * 호스트는 탭·리모트 슬롯만 두고 가로 전체(`max-w-none`은 셸 `ConsoleLayoutOverride`)로 둔다.
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
  const usageResetKey = `${router.asPath}|${teams.length}`;

  function goTab(next: TeamRouteSection) {
    const q: Record<string, string> = { tab: next };
    if (viewTeamId !== "") q.viewTeamId = viewTeamId;
    void router.replace({ pathname: "/", query: q }, undefined, { shallow: true });
  }

  return (
    <div className="host-remote-slot mx-auto flex w-full max-w-none flex-1 flex-col gap-4">
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
          variant={tab === "members" ? "default" : "outline"}
          className={cn(tab === "members" && "font-semibold")}
          onClick={() => goTab("members")}
        >
          멤버 관리
        </Button>
        <Button
          type="button"
          size="sm"
          variant={tab === "api-keys" ? "default" : "outline"}
          className={cn(tab === "api-keys" && "font-semibold")}
          onClick={() => goTab("api-keys")}
        >
          API 및 설정
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

      {mfChunkAllowed ? (
        <TeamMfRemotesLazy tab={tab} viewTeamId={viewTeamId} teams={teams} usageResetKey={usageResetKey} />
      ) : (
        <p className="text-sm text-muted-foreground">원격 모듈을 준비하는 중…</p>
      )}
    </div>
  );
}
