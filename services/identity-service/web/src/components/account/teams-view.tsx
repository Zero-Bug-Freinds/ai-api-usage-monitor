"use client"

import * as React from "react"

type ApiResponse<T> = {
  success: boolean
  message: string
  data: T | null
}

type TeamSummary = {
  id: string
  name: string
}

function asApiResponse(value: unknown): ApiResponse<unknown> | null {
  if (!value || typeof value !== "object") return null
  const r = value as Record<string, unknown>
  if (typeof r.success !== "boolean") return null
  if (typeof r.message !== "string") return null
  if (!("data" in r)) return null
  return r as ApiResponse<unknown>
}

export function TeamsView() {
  const [loading, setLoading] = React.useState(true)
  const [error, setError] = React.useState<string | null>(null)
  const [teams, setTeams] = React.useState<TeamSummary[]>([])
  const [teamName, setTeamName] = React.useState("")
  const [createLoading, setCreateLoading] = React.useState(false)
  const [inviteInputByTeamId, setInviteInputByTeamId] = React.useState<Record<string, string>>({})
  const [inviteLoadingTeamId, setInviteLoadingTeamId] = React.useState<string | null>(null)
  const [message, setMessage] = React.useState<{ kind: "success" | "error"; text: string } | null>(null)

  const loadTeams = React.useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await fetch("/api/team/v1/me/teams", {
        method: "GET",
        credentials: "include",
        headers: { Accept: "application/json" },
        cache: "no-store",
      })
      const json = (await res.json()) as unknown
      const body = asApiResponse(json)
      if (!res.ok || !body?.success || !Array.isArray(body.data)) {
        setError(body?.message ?? "팀 목록을 불러오지 못했습니다")
        setTeams([])
        return
      }
      setTeams(
        body.data
          .filter((item) => typeof item === "object" && item !== null)
          .map((item) => item as TeamSummary)
          .filter((item) => typeof item.id === "string" && typeof item.name === "string")
      )
    } catch {
      setError("팀 목록을 불러오지 못했습니다")
      setTeams([])
    } finally {
      setLoading(false)
    }
  }, [])

  React.useEffect(() => {
    void loadTeams()
  }, [loadTeams])

  async function createTeam(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    if (createLoading) return
    const name = teamName.trim()
    if (!name) {
      setMessage({ kind: "error", text: "팀 이름은 필수입니다" })
      return
    }
    setCreateLoading(true)
    setMessage(null)
    try {
      const res = await fetch("/api/team/v1/teams", {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({ name }),
      })
      const json = (await res.json()) as unknown
      const body = asApiResponse(json)
      if (!res.ok || !body?.success) {
        setMessage({ kind: "error", text: body?.message ?? "팀 생성에 실패했습니다" })
        return
      }
      setMessage({ kind: "success", text: "팀이 생성되었습니다" })
      setTeamName("")
      await loadTeams()
    } catch {
      setMessage({ kind: "error", text: "팀 생성에 실패했습니다" })
    } finally {
      setCreateLoading(false)
    }
  }

  async function invite(teamId: string) {
    if (inviteLoadingTeamId) return
    const userId = (inviteInputByTeamId[teamId] ?? "").trim()
    if (!userId) {
      setMessage({ kind: "error", text: "초대할 사용자 아이디를 입력해 주세요" })
      return
    }
    setInviteLoadingTeamId(teamId)
    setMessage(null)
    try {
      const res = await fetch(`/api/team/v1/teams/${encodeURIComponent(teamId)}/members`, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({ userId }),
      })
      const json = (await res.json()) as unknown
      const body = asApiResponse(json)
      if (!res.ok || !body?.success) {
        setMessage({ kind: "error", text: body?.message ?? "초대에 실패했습니다" })
        return
      }
      setMessage({ kind: "success", text: "초대를 보냈습니다" })
      setInviteInputByTeamId((prev) => ({ ...prev, [teamId]: "" }))
    } catch {
      setMessage({ kind: "error", text: "초대에 실패했습니다" })
    } finally {
      setInviteLoadingTeamId(null)
    }
  }

  return (
    <div className="flex min-h-[40vh] flex-col gap-8 py-4">
      <header className="space-y-2">
        <h1 className="text-2xl font-semibold">팀 관리</h1>
        <p className="text-sm text-zinc-600">팀 생성 후 사용자 아이디로 팀원을 초대할 수 있습니다.</p>
      </header>

      <section className="max-w-lg space-y-3 rounded-lg border border-zinc-200 bg-white p-4">
        <h2 className="text-sm font-semibold">팀 만들기</h2>
        <form className="flex flex-col gap-2 sm:flex-row" onSubmit={createTeam}>
          <input
            className="h-10 flex-1 rounded-md border border-zinc-300 bg-white px-3 text-sm"
            value={teamName}
            onChange={(e) => setTeamName(e.target.value)}
            placeholder="예: 플랫폼팀"
            autoComplete="off"
            disabled={createLoading}
            required
          />
          <button
            type="submit"
            className="h-10 rounded-md bg-black px-4 text-sm font-medium text-white disabled:opacity-60"
            disabled={createLoading}
          >
            {createLoading ? "생성 중..." : "팀 만들기"}
          </button>
        </form>
      </section>

      {message ? <p className={message.kind === "success" ? "text-sm text-emerald-600" : "text-sm text-red-600"}>{message.text}</p> : null}
      {loading ? <p className="text-sm text-zinc-500">불러오는 중...</p> : null}
      {error && !loading ? <p className="text-sm text-red-600">{error}</p> : null}

      {!loading && !error && teams.length === 0 ? <p className="text-sm text-zinc-500">참여 중인 팀이 없습니다.</p> : null}

      {!loading && teams.length > 0 ? (
        <ul className="max-w-lg divide-y divide-zinc-200 rounded-lg border border-zinc-200 bg-white">
          {teams.map((team) => (
            <li key={team.id} className="space-y-2 px-4 py-3">
              <p className="font-medium">{team.name}</p>
              <p className="text-xs text-zinc-500">id: {team.id}</p>
              <div className="flex flex-col gap-2 sm:flex-row">
                <input
                  className="h-9 flex-1 rounded-md border border-zinc-300 bg-white px-3 text-sm"
                  value={inviteInputByTeamId[team.id] ?? ""}
                  onChange={(e) => setInviteInputByTeamId((prev) => ({ ...prev, [team.id]: e.target.value }))}
                  placeholder="초대할 사용자 아이디"
                  autoComplete="off"
                  disabled={inviteLoadingTeamId === team.id}
                />
                <button
                  type="button"
                  className="h-9 rounded-md border border-zinc-300 bg-white px-3 text-xs font-medium disabled:opacity-60"
                  disabled={inviteLoadingTeamId === team.id}
                  onClick={() => void invite(team.id)}
                >
                  {inviteLoadingTeamId === team.id ? "초대 중..." : "아이디로 초대"}
                </button>
              </div>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  )
}
