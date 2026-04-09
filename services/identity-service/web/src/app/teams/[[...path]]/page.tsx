import { TeamsView } from "@/components/account/teams-view"

type TeamsPageProps = {
  params: Promise<{ path?: string[] }>
}

export default async function TeamsPage(_: TeamsPageProps) {
  return <TeamsView />
}
