"use client";

import { useEffect, useState } from "react";
import TeamDashboard from "./TeamDashboard";
import TeamMemberUsageLog from "./TeamMemberUsageLog";

type TeamUsageDashboardProps = {
  /** When embedded in apps/web `/teams/[id]`, pass router query id so team changes without full reload. */
  teamId?: string;
};

export default function TeamUsageDashboard({ teamId: teamIdProp }: TeamUsageDashboardProps) {
  const [selectedUserId, setSelectedUserId] = useState<string>("");
  const [pathTeamId, setPathTeamId] = useState("");

  useEffect(() => {
    const readPath = () => {
      const m = window.location.pathname.match(/\/teams\/([^/]+)/);
      setPathTeamId(m?.[1] ?? "");
    };
    readPath();
    window.addEventListener("popstate", readPath);
    return () => window.removeEventListener("popstate", readPath);
  }, []);

  const teamId = teamIdProp ?? pathTeamId;

  return (
    <div className="space-y-4">
      <TeamDashboard teamId={teamId} onSelectUser={setSelectedUserId} />
      <TeamMemberUsageLog teamId={teamId} userId={selectedUserId} />
    </div>
  );
}
