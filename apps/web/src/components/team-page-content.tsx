"use client"

import dynamic from "next/dynamic"
import * as React from "react"

const TeamManagement = dynamic(() => import("team/TeamManagement"), { ssr: false })
const TeamUsageDashboard = dynamic(() => import("usage/TeamUsageDashboard"), { ssr: false })

export function TeamPageContent() {
  const [teamError, setTeamError] = React.useState<string | null>(null)
  const [usageError, setUsageError] = React.useState<string | null>(null)

  return (
    <div className="flex h-full min-h-[70vh] w-full flex-col gap-4 p-4">
      <div className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">팀</h1>
        <p className="text-sm text-muted-foreground">
          와이어프레임: 좌측 팀 관리 / 우측 상단 팀 정보·하단 사용량. Remote는 현재{" "}
          <code className="rounded bg-muted px-1">TeamManagement</code>·
          <code className="rounded bg-muted px-1">TeamUsageDashboard</code> 두 개입니다.
        </p>
      </div>

      <div className="flex min-h-0 w-full flex-1 gap-4">
        <div className="flex w-1/4 min-w-[260px] flex-col gap-4">
          <section className="min-h-0 flex-1 overflow-y-auto rounded-lg border border-border bg-card p-3 shadow-sm">
            <React.Suspense fallback={<p className="text-sm text-muted-foreground">팀 UI 로드 중…</p>}>
              <TeamErrorBoundary onError={setTeamError}>
                <TeamManagement />
              </TeamErrorBoundary>
            </React.Suspense>
            {teamError ? <p className="mt-2 text-sm text-destructive">{teamError}</p> : null}
          </section>
        </div>

        <div className="flex min-w-0 flex-1 flex-col gap-4 md:w-3/4">
          <section className="h-fit rounded-lg border border-border bg-card p-4 shadow-sm">
            <p className="text-sm text-muted-foreground">
              <span className="font-medium text-foreground">TeamInfo</span> 영역: 세부 UI 분리 시 team Remote에
              exposes를 추가합니다. 현재는 좌측 <code className="rounded bg-muted px-1">TeamManagement</code>에 통합되어
              있습니다.
            </p>
          </section>
          <section className="min-h-0 flex-1 rounded-lg border border-border bg-card p-4 shadow-sm">
            <React.Suspense fallback={<p className="text-sm text-muted-foreground">대시보드 로드 중…</p>}>
              <UsageErrorBoundary onError={setUsageError}>
                <TeamUsageDashboard />
              </UsageErrorBoundary>
            </React.Suspense>
            {usageError ? <p className="mt-2 text-sm text-destructive">{usageError}</p> : null}
          </section>
        </div>
      </div>
    </div>
  )
}

function TeamErrorBoundary({
  children,
  onError,
}: {
  children: React.ReactNode
  onError: (message: string | null) => void
}) {
  return (
    <ErrorBoundary
      onError={(e) => onError(e.message)}
      onReset={() => onError(null)}
      title="team Remote"
    >
      {children}
    </ErrorBoundary>
  )
}

function UsageErrorBoundary({
  children,
  onError,
}: {
  children: React.ReactNode
  onError: (message: string | null) => void
}) {
  return (
    <ErrorBoundary onError={(e) => onError(e.message)} onReset={() => onError(null)} title="usage Remote">
      {children}
    </ErrorBoundary>
  )
}

class ErrorBoundary extends React.Component<
  {
    children: React.ReactNode
    title: string
    onError: (error: Error) => void
    onReset: () => void
  },
  { error: Error | null }
> {
  state = { error: null as Error | null }

  static getDerivedStateFromError(error: Error) {
    return { error }
  }

  componentDidCatch(error: Error) {
    this.props.onError(error)
  }

  render() {
    if (this.state.error) {
      return (
        <div className="space-y-2 text-sm">
          <p className="font-medium text-destructive">{this.props.title} 로드 실패</p>
          <p className="text-muted-foreground">{this.state.error.message}</p>
          <button
            type="button"
            className="rounded-md border border-border px-3 py-1.5 text-xs font-medium hover:bg-muted"
            onClick={() => {
              this.setState({ error: null })
              this.props.onReset()
            }}
          >
            다시 시도
          </button>
        </div>
      )
    }
    return this.props.children
  }
}
