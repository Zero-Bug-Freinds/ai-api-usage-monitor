"use client";

import { useMemo, useState } from "react";
import TeamDashboard from "./TeamDashboard";
import TeamMemberUsageLog from "./TeamMemberUsageLog";

export default function TeamUsageDashboard() {
  const [selectedUserId, setSelectedUserId] = useState<string>("");
  const teamId = useMemo(() => {
    if (typeof window === "undefined") {
      return "";
    }
    const m = window.location.pathname.match(/\/teams\/([^/]+)/);
    return m?.[1] ?? "";
  }, []);

  return (
    <div className="space-y-4">
      <TeamDashboard teamId={teamId} onSelectUser={setSelectedUserId} />
      <TeamMemberUsageLog teamId={teamId} userId={selectedUserId} />
    </div>
  );
}
