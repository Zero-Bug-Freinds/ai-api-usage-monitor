"use client"

import * as React from "react"

import { fetchIdentityManagement } from "@/lib/identity-management/fetch-management-api"
import type { TeamSummary } from "@/lib/identity-management/types"

function isTeamList(data: unknown): data is TeamSummary[] {
  if (!Array.isArray(data)) return false
  return data.every(
    (item) =>
      typeof item === "object" &&
      item !== null &&
      typeof (item as TeamSummary).id === "string" &&
      typeof (item as TeamSummary).name === "string"
  )
}

export function TeamsView({ pathSegments }: { pathSegments?: string[] }) {
  const [items, setItems] = React.useState<TeamSummary[] | null>(null)
  const [loading, setLoading] = React.useState(true)
  const [error, setError] = React.useState<string | null>(null)
  const [notAvailable, setNotAvailable] = React.useState(false)

  React.useEffect(() => {
    let cancelled = false
    ;(async () => {
      setLoading(true)
      setError(null)
      setNotAvailable(false)
      try {
        const data = await fetchIdentityManagement<unknown>("v1/me/teams", "/teams")
        if (cancelled) return
        if (data === null || (Array.isArray(data) && data.length === 0)) {
          setItems(Array.isArray(data) ? data : [])
          return
        }
        if (isTeamList(data)) {
          setItems(data)
        } else {
          setError("팀 목록 형식이 올바르지 않습니다")
        }
      } catch (e) {
        if (cancelled) return
        const status = (e as Error & { status?: number }).status
        if (status === 404) {
          setNotAvailable(true)
          setItems([])
        } else {
          setError(e instanceof Error ? e.message : "팀 목록을 불러오지 못했습니다")
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  const subpath = pathSegments?.length ? pathSegments.join(" / ") : null

  return (
    <div className="flex min-h-[40vh] flex-col gap-8 py-4">
      <div className="space-y-2">
        <h1 className="text-2xl font-semibold tracking-tight">팀</h1>
        <p className="text-sm text-muted-foreground">
          참여 중인 팀 목록입니다. Identity 서비스의 <span className="font-mono text-xs">GET /api/v1/me/teams</span> 계약과
          연동됩니다.
        </p>
      </div>

      {subpath ? (
        <div className="rounded-lg border border-border bg-muted/20 px-4 py-3 text-sm text-muted-foreground">
          <p className="font-mono text-foreground/80">{subpath}</p>
          <p className="mt-2">팀 상세·편집 화면은 추후 제공됩니다.</p>
        </div>
      ) : null}

      {loading ? <p className="text-sm text-muted-foreground">불러오는 중…</p> : null}
      {error && !loading ? <p className="text-sm text-destructive">{error}</p> : null}
      {notAvailable && !loading && !error ? (
        <p className="text-sm text-muted-foreground">
          팀 관리 API가 아직 Identity 서비스에 없거나 경로가 다릅니다. 백엔드에{" "}
          <span className="font-mono text-xs">GET /api/v1/me/teams</span> 가 준비되면 이 목록이 채워집니다.
        </p>
      ) : null}

      {!loading && !error && items && items.length === 0 && !notAvailable ? (
        <p className="text-sm text-muted-foreground">참여 중인 팀이 없습니다.</p>
      ) : null}

      {!loading && items && items.length > 0 ? (
        <ul className="divide-y divide-border rounded-lg border border-border">
          {items.map((team) => (
            <li key={team.id} className="px-4 py-3">
              <p className="font-medium">{team.name}</p>
              {team.organizationId ? (
                <p className="mt-1 font-mono text-xs text-muted-foreground">organization: {team.organizationId}</p>
              ) : null}
              {team.description ? <p className="mt-1 text-sm text-muted-foreground">{team.description}</p> : null}
              <p className="mt-1 font-mono text-xs text-muted-foreground">id: {team.id}</p>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  )
}
