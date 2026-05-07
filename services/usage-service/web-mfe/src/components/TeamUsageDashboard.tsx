"use client";

import * as React from "react";
import { useLogoutCleanup, type CachedTeamItem } from "@ai-usage/team-workspace-cache";
import TeamDashboard from "./TeamDashboard";
import TeamMemberUsageLog from "./TeamMemberUsageLog";

type TeamUsageDashboardProps = {
  /** URL `viewTeamId` — 새로고침 시 조회 팀 힌트(관리 UI와 자동 동기화 없음). */
  viewTeamIdFromQuery?: string;
  /** Main Shell 동기화 팀 목록(캐시와 병합). */
  shellTeamList?: CachedTeamItem[];
};

export default function TeamUsageDashboard({ viewTeamIdFromQuery, shellTeamList }: TeamUsageDashboardProps) {
  useLogoutCleanup();
  const [selectedUserId, setSelectedUserId] = React.useState<string>("");
  const [bffTeamId, setBffTeamId] = React.useState<string>("");
  const [activeTab, setActiveTab] = React.useState<"team" | "member">("team");

  return (
    <section className="w-full min-w-0 rounded-xl border border-border bg-background p-4 shadow-sm">
      <div className="mb-4 border-b border-border">
        <div className="flex flex-wrap items-center gap-5">
          <button
            type="button"
            className={`pb-2 text-sm font-semibold transition ${
              activeTab === "team"
                ? "border-b-2 border-blue-500 text-blue-600"
                : "border-b-2 border-transparent text-muted-foreground hover:text-foreground"
            }`}
            onClick={() => setActiveTab("team")}
          >
            대시보드
          </button>
          <button
            type="button"
            className={`pb-2 text-sm font-semibold transition ${
              activeTab === "member"
                ? "border-b-2 border-blue-500 text-blue-600"
                : "border-b-2 border-transparent text-muted-foreground hover:text-foreground"
            }`}
            onClick={() => setActiveTab("member")}
          >
            멤버 상세
          </button>
        </div>
      </div>

      <div className="rounded-lg border border-border bg-card p-4">
        {activeTab === "team" ? (
          <TeamDashboard
            viewTeamIdFromQuery={viewTeamIdFromQuery}
            shellTeamList={shellTeamList}
            onSelectUser={setSelectedUserId}
            onEffectiveTeamChange={setBffTeamId}
          />
        ) : (
          <TeamMemberUsageLog teamId={bffTeamId} userId={selectedUserId} isActive={activeTab === "member"} />
        )}
      </div>
    </section>
  );
}
