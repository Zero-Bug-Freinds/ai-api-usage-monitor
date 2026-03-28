import { ProtectedPlaceholderPage } from "@/components/auth/protected-placeholder-page"

type TeamsPageProps = {
  params: Promise<{ path?: string[] }>
}

export default async function TeamsPage({ params }: TeamsPageProps) {
  const { path } = await params

  return (
    <ProtectedPlaceholderPage
      title="팀"
      description="팀 관리 영역(플레이스홀더)입니다."
      pathSegments={path}
    />
  )
}
