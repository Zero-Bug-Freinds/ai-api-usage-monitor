import { ProtectedPlaceholderPage } from "@/components/auth/protected-placeholder-page"

type SettingsPageProps = {
  params: Promise<{ path?: string[] }>
}

export default async function SettingsPage({ params }: SettingsPageProps) {
  const { path } = await params

  return (
    <ProtectedPlaceholderPage
      title="설정"
      description="계정 및 앱 설정 영역(플레이스홀더)입니다."
      pathSegments={path}
    />
  )
}
