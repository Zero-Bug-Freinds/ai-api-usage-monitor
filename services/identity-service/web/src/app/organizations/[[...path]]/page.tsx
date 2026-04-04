import { OrganizationsView } from "@/components/account/organizations-view"

type OrganizationsPageProps = {
  params: Promise<{ path?: string[] }>
}

export default async function OrganizationsPage({ params }: OrganizationsPageProps) {
  const { path } = await params

  return <OrganizationsView pathSegments={path} />
}
