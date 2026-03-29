import { ProtectedPlaceholderPage } from "@/components/auth/protected-placeholder-page"
import { UsageDashboard } from "@/components/usage/usage-dashboard"

type DashboardPageProps = {
  params: Promise<{ path?: string[] }>
}

export default async function DashboardPage({ params }: DashboardPageProps) {
  const { path } = await params
  const segments = path ?? []

  if (segments.length > 0) {
    return (
      <ProtectedPlaceholderPage
        title="대시보드"
        description="이 하위 경로는 아직 준비 중입니다."
        pathSegments={segments}
      />
    )
  }

  return <UsageDashboard />
}
