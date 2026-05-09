"use client"

import * as React from "react"
import TeamDashboard from "@/components/usage/team/team-dashboard"
import TeamMemberDashboard from "@/components/usage/team/team-member-dashboard"

type TeamUsageDashboardProps = {
  viewTeamIdFromQuery?: string
  /** Driven by `/team?tab=team|member` — sidebar navigation only (no in-page tabs). */
  teamView?: "team" | "member"
}

export default function TeamUsageDashboard({
  viewTeamIdFromQuery,
  teamView = "team",
}: TeamUsageDashboardProps) {
  const [selectedUserId, setSelectedUserId] = React.useState<string>("")
  const [bffTeamId, setBffTeamId] = React.useState<string>("")

  return teamView === "team" ? (
    <TeamDashboard
      viewTeamIdFromQuery={viewTeamIdFromQuery}
      onSelectUser={setSelectedUserId}
      onEffectiveTeamChange={setBffTeamId}
    />
  ) : (
    <TeamMemberDashboard teamId={bffTeamId} userId={selectedUserId} isActive />
  )
}
