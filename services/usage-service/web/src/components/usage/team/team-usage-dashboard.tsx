"use client"

import * as React from "react"
import TeamDashboard from "@/components/usage/team/team-dashboard"
import TeamMemberDashboard from "@/components/usage/team/team-member-dashboard"
import {
  readTeamDashboardLastTeamId,
  writeTeamDashboardLastTeamId,
} from "@/lib/usage/team-dashboard-last-team"

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
  const [clientReady, setClientReady] = React.useState(false)

  React.useLayoutEffect(() => {
    setClientReady(true)
  }, [])

  React.useLayoutEffect(() => {
    if (!clientReady) return
    const fromQuery = viewTeamIdFromQuery?.trim()
    if (fromQuery) {
      setBffTeamId(fromQuery)
      writeTeamDashboardLastTeamId(fromQuery)
      return
    }
    const saved = readTeamDashboardLastTeamId()
    if (saved) {
      setBffTeamId((prev) => prev || saved)
    }
  }, [clientReady, viewTeamIdFromQuery])

  const handleEffectiveTeamChange = React.useCallback((teamId: string) => {
    setBffTeamId(teamId)
    if (teamId) {
      writeTeamDashboardLastTeamId(teamId)
    }
  }, [])

  return teamView === "team" ? (
    <TeamDashboard
      viewTeamIdFromQuery={viewTeamIdFromQuery}
      onSelectUser={setSelectedUserId}
      onEffectiveTeamChange={handleEffectiveTeamChange}
    />
  ) : (
    <TeamMemberDashboard teamId={bffTeamId} userId={selectedUserId} isActive />
  )
}
