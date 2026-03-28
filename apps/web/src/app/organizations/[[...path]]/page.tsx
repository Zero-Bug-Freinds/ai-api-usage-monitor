import { ProtectedPlaceholderPage } from "@/components/auth/protected-placeholder-page"

type OrganizationsPageProps = {
  params: Promise<{ path?: string[] }>
}

export default async function OrganizationsPage({ params }: OrganizationsPageProps) {
  const { path } = await params

  return (
    <ProtectedPlaceholderPage
      title="조직"
      description="조직 관리 영역(플레이스홀더)입니다."
      pathSegments={path}
    />
  )
}
