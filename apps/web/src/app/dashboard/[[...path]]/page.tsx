import { ProtectedPlaceholderPage } from "@/components/auth/protected-placeholder-page"

type DashboardPageProps = {
  params: Promise<{ path?: string[] }>
}

export default async function DashboardPage({ params }: DashboardPageProps) {
  const { path } = await params

  return (
    <ProtectedPlaceholderPage
      title="대시보드"
      description="로그인된 사용자 영역입니다."
      pathSegments={path}
    />
  )
}
