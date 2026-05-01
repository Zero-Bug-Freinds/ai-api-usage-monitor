"use client";

import dynamic from "next/dynamic";
import { useRouter } from "next/router";
import { Button, cn } from "@ai-usage/ui";
import { RemoteErrorBoundary } from "@/components/remote-error-boundary";
import { useTeamWorkspace } from "@/components/team-workspace-context";

export type TeamRouteSection = "dashboard" | "members" | "api-keys";

const TeamManagement = dynamic(() => import("team/TeamManagement"), {
  ssr: false,
  loading: () => <p className="text-sm text-muted-foreground">Team remote loading...</p>,
});
const TeamUsageDashboard = dynamic(() => import("usage/TeamUsageDashboard"), {
  ssr: false,
  loading: () => <p className="text-sm text-muted-foreground">Usage remote loading...</p>,
});

function normalizeTab(q: string | string[] | undefined): TeamRouteSection {
  const raw = Array.isArray(q) ? q[0] : q;
  if (raw === "members" || raw === "api-keys") return raw;
  return "dashboard";
}

export function TeamPageContent() {
  const router = useRouter();
  const { teams, syncError } = useTeamWorkspace();

  const viewTeamId =
    typeof router.query.viewTeamId === "string"
      ? router.query.viewTeamId
      : Array.isArray(router.query.viewTeamId)
        ? router.query.viewTeamId[0] ?? ""
        : "";

  const tab = normalizeTab(router.query.tab);

  function goTab(next: TeamRouteSection) {
    const q: Record<string, string> = { tab: next };
    if (viewTeamId !== "") q.viewTeamId = viewTeamId;
    void router.replace({ pathname: "/teams", query: q }, undefined, { shallow: true });
  }

  return (
    <div className="space-y-4">
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

      {tab === "dashboard" ? (
        <RemoteErrorBoundary
          fallback={<p className="p-4 text-sm text-muted-foreground">Usage remote를 불러오지 못했습니다.</p>}
        >
          <TeamUsageDashboard viewTeamIdFromQuery={viewTeamId} shellTeamList={teams} />
        </RemoteErrorBoundary>
      ) : (
        <RemoteErrorBoundary
          fallback={<p className="p-4 text-sm text-muted-foreground">Team remote를 불러오지 못했습니다.</p>}
        >
          <TeamManagement />
        </RemoteErrorBoundary>
      )}
    </div>
  );
}
