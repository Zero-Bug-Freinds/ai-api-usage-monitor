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
  const [activeTab, setActiveTab] = React.useState<"team" | "member">(initialTab)

  React.useEffect(() => {
    setActiveTab(initialTab)
  }, [initialTab])

  return (
    <section className="w-full min-w-0 rounded-xl border border-border bg-background p-4 shadow-sm">
      <div className="mb-4 border-b border-border">
        <div className="flex flex-wrap items-center gap-5">
          <button
            type="button"
            className={`pb-2 text-sm font-semibold transition ${activeTab === "team" ? "border-b-2 border-blue-500 text-blue-600" : "border-b-2 border-transparent text-muted-foreground hover:text-foreground"}`}
            onClick={() => setActiveTab("team")}
          >
            팀 요약
          </button>
          <button
            type="button"
            className={`pb-2 text-sm font-semibold transition ${activeTab === "member" ? "border-b-2 border-blue-500 text-blue-600" : "border-b-2 border-transparent text-muted-foreground hover:text-foreground"}`}
            onClick={() => setActiveTab("member")}
          >
            멤버별 분석
          </button>
        </div>
      </div>
      <div className="rounded-lg border border-border bg-card p-4">
        {activeTab === "team" ? (
          <TeamDashboard
            viewTeamIdFromQuery={viewTeamIdFromQuery}
            onSelectUser={setSelectedUserId}
            onEffectiveTeamChange={setBffTeamId}
          />
        ) : (
          <TeamMemberDashboard teamId={bffTeamId} userId={selectedUserId} isActive={activeTab === "member"} />
        )}
      </div>
    </section>
  )
}
