/**
 * 팀 BFF(team-web)는 API·프록시 중심으로 두고, 팀 콘솔 UI는 web-host(apps/web)에서 제공합니다.
 */
export default function TeamWebPlaceholderPage() {
  const entry =
    typeof process.env.NEXT_PUBLIC_TEAM_CONSOLE_URL === "string"
      ? process.env.NEXT_PUBLIC_TEAM_CONSOLE_URL
      : "http://localhost:3002/teams"

  return (
    <main className="flex min-h-[40vh] flex-col items-center justify-center gap-3 p-6 text-center text-sm text-muted-foreground">
      <p>팀 콘솔 화면은 Main Shell(web-host)에서 제공됩니다.</p>
      <p>
        <a href={entry} className="font-medium text-primary underline underline-offset-4">
          팀 콘솔로 이동
        </a>
      </p>
    </main>
  )
}
