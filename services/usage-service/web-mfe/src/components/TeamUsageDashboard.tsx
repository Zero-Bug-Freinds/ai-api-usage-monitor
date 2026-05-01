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

  return (
    <div className="space-y-4">
      <TeamDashboard
        viewTeamIdFromQuery={viewTeamIdFromQuery}
        shellTeamList={shellTeamList}
        onSelectUser={setSelectedUserId}
        onEffectiveTeamChange={setBffTeamId}
      />
      <TeamMemberUsageLog teamId={bffTeamId} userId={selectedUserId} />
    </div>
  );
}
