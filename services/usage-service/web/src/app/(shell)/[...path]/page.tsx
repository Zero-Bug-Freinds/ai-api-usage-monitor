import { ProtectedPlaceholderPage } from "@/components/auth/protected-placeholder-page"
import { COMMON_MESSAGES } from "@/lib/usage/messaging/common-messages"

type PageProps = {
  params: Promise<{ path: string[] }>
}

export default async function DashboardNestedPage({ params }: PageProps) {
  const { path } = await params
  return (
    <ProtectedPlaceholderPage
      title="대시보드"
      description={COMMON_MESSAGES.shell.routeNotReady}
      pathSegments={path}
    />
  )
}
