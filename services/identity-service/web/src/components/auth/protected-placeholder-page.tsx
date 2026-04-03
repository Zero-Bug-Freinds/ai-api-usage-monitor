type ProtectedPlaceholderPageProps = {
  title: string
  description: string
  pathSegments?: string[]
}

/**
 * 대시보드 셸(`DashboardShell`) 안에서 쓰인다. 로그아웃·내비는 사이드바에 있다.
 */
export function ProtectedPlaceholderPage({ title, description, pathSegments }: ProtectedPlaceholderPageProps) {
  const subpath = pathSegments?.length ? pathSegments.join(" / ") : null

  return (
    <div className="flex min-h-[40vh] max-w-lg flex-col gap-6 py-4">
      <div className="space-y-2">
        <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
        {subpath ? <p className="font-mono text-sm text-muted-foreground">{subpath}</p> : null}
        <p className="text-sm text-muted-foreground">{description}</p>
      </div>
      <p className="text-sm text-muted-foreground">왼쪽 메뉴에서 다른 영역으로 이동할 수 있습니다.</p>
    </div>
  )
}
