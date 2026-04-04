import { ProtectedPlaceholderPage } from "@/components/auth/protected-placeholder-page"

type PageProps = {
  params: Promise<{ path: string[] }>
}

export default async function DashboardNestedPage({ params }: PageProps) {
  const { path } = await params
  return (
    <ProtectedPlaceholderPage
      title="대시보드"
      description="이 하위 경로는 아직 준비 중입니다."
      pathSegments={path}
    />
  )
}
