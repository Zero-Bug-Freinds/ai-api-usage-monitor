"use client"

import * as React from "react"
import TeamDashboard from "@/components/usage/team/team-dashboard"
import TeamMemberDashboard from "@/components/usage/team/team-member-dashboard"

type TeamUsageDashboardProps = {
  viewTeamIdFromQuery?: string
  initialTab?: "team" | "member"
}

export default function TeamUsageDashboard({ viewTeamIdFromQuery, initialTab = "team" }: TeamUsageDashboardProps) {
  const [selectedUserId, setSelectedUserId] = React.useState<string>("")
  const [bffTeamId, setBffTeamId] = React.useState<string>("")

  return (
    initialTab === "team" ? (
      <TeamDashboard
        viewTeamIdFromQuery={viewTeamIdFromQuery}
        onSelectUser={setSelectedUserId}
        onEffectiveTeamChange={setBffTeamId}
      />
    ) : (
      <TeamMemberDashboard teamId={bffTeamId} userId={selectedUserId} isActive />
    )
  )
}
