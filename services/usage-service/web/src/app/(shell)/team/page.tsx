import TeamUsageDashboard from "@/components/usage/team/team-usage-dashboard"

type TeamPageProps = {
  searchParams: Promise<{ tab?: string; viewTeamId?: string }>
}

export default async function TeamUsagePage({ searchParams }: TeamPageProps) {
  const params = await searchParams
  const teamView = params.tab === "member" ? "member" : "team"

  return (
    <TeamUsageDashboard
      teamView={teamView}
      viewTeamIdFromQuery={typeof params.viewTeamId === "string" ? params.viewTeamId : undefined}
    />
  )
}
