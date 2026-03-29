import { AccountSettingsView } from "@/components/account/account-settings-view"

type SettingsPageProps = {
  params: Promise<{ path?: string[] }>
}

export default async function SettingsPage({ params }: SettingsPageProps) {
  const { path } = await params

  return <AccountSettingsView pathSegments={path} />
}
