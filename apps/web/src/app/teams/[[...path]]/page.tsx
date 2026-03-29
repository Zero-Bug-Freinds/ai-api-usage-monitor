import { TeamsView } from "@/components/account/teams-view"

type TeamsPageProps = {
  params: Promise<{ path?: string[] }>
}

export default async function TeamsPage({ params }: TeamsPageProps) {
  const { path } = await params

  return <TeamsView pathSegments={path} />
}
