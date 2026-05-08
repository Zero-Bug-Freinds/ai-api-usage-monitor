import TeamUsageDashboard from "@/components/usage/team/team-usage-dashboard"

type TeamPageProps = {
  searchParams: Promise<{ tab?: string; viewTeamId?: string }>
}

export default async function TeamUsagePage({ searchParams }: TeamPageProps) {
  const params = await searchParams
  const tab = params.tab === "member" ? "member" : "team"

  return (
    <TeamUsageDashboard
      initialTab={tab}
      viewTeamIdFromQuery={typeof params.viewTeamId === "string" ? params.viewTeamId : undefined}
    />
  )
}
