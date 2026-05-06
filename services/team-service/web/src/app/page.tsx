import { redirect } from "next/navigation"
import TeamManagementEntry from "@/components/mf/team-management-entry"

export default function TeamWebRootRedirectPage() {
  if (process.env.NODE_ENV === "development") {
    return (
      <main className="min-h-0 flex-1 bg-sidebar text-sidebar-foreground">
        <TeamManagementEntry />
      </main>
    )
  }

  const raw = process.env.NEXT_PUBLIC_TEAM_CONSOLE_URL?.trim()
  const target =
    raw && raw.length > 0 ? raw.replace(/\/+$/, "") : "http://localhost:8888/teams"
  redirect(target)
}
