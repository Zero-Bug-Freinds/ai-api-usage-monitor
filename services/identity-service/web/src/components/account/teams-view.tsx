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

type TeamSummaryLike = {
  id: string | number
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

function normalizeTeamSummary(item: unknown): TeamSummary | null {
  if (!item || typeof item !== "object") return null
  const v = item as TeamSummaryLike
  if ((typeof v.id !== "string" && typeof v.id !== "number") || typeof v.name !== "string") return null
  return { id: String(v.id), name: v.name }
}

export function TeamsView() {
  const [loading, setLoading] = React.useState(true)
  const [error, setError] = React.useState<string | null>(null)
  const [teams, setTeams] = React.useState<TeamSummary[]>([])
  const [teamMemberIdsByTeamId, setTeamMemberIdsByTeamId] = React.useState<Record<string, string[]>>({})
  const [showCreateForm, setShowCreateForm] = React.useState(false)
  const [teamName, setTeamName] = React.useState("")
  const [inviteUserIds, setInviteUserIds] = React.useState<string[]>([""])
  const [createLoading, setCreateLoading] = React.useState(false)
  const [inviteInputByTeamId, setInviteInputByTeamId] = React.useState<Record<string, string>>({})
  const [inviteLoadingTeamId, setInviteLoadingTeamId] = React.useState<string | null>(null)
  const [message, setMessage] = React.useState<{ kind: "success" | "error"; text: string } | null>(null)

  function normalizeInviteUserIds(values: string[]): string[] {
    return Array.from(
      new Set(
        values
          .map((v) => v.trim())
          .filter((v) => v.length > 0)
      )
    )
  }

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

      if (res.status === 404) {
        setTeams([])
        return
      }

      if (!res.ok || !body?.success) {
        setError(body?.message ?? "팀 목록을 불러오지 못했습니다")
        setTeams([])
        return
      }

      if (body.data === null) {
        setTeams([])
        return
      }

      if (!Array.isArray(body.data)) {
        setError("팀 목록을 불러오지 못했습니다")
        setTeams([])
        return
      }

      setTeams(
        body.data
          .map((item) => normalizeTeamSummary(item))
          .filter((item): item is TeamSummary => item !== null)
      )
    } catch {
      setError("팀 목록을 불러오지 못했습니다")
      setTeams([])
    } finally {
      setLoading(false)
    }
  }, [])

  const loadTeamMembers = React.useCallback(async (teamId: string) => {
    try {
      const res = await fetch(`/api/team/v1/teams/${encodeURIComponent(teamId)}/members`, {
        method: "GET",
        credentials: "include",
        headers: { Accept: "application/json" },
        cache: "no-store",
      })
      const json = (await res.json()) as unknown
      const body = asApiResponse(json)
      if (!res.ok || !body?.success || !Array.isArray(body.data)) {
        setTeamMemberIdsByTeamId((prev) => ({ ...prev, [teamId]: [] }))
        return
      }
      const memberIds = body.data.filter((v) => typeof v === "string").map((v) => String(v))
      setTeamMemberIdsByTeamId((prev) => ({ ...prev, [teamId]: memberIds }))
    } catch {
      setTeamMemberIdsByTeamId((prev) => ({ ...prev, [teamId]: [] }))
    }
  }, [])

  React.useEffect(() => {
    void loadTeams()
  }, [loadTeams])

  React.useEffect(() => {
    if (teams.length === 0) {
      setTeamMemberIdsByTeamId({})
      return
    }
    for (const team of teams) {
      void loadTeamMembers(team.id)
    }
  }, [teams, loadTeamMembers])

  async function createTeam(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    if (createLoading) return
    const name = teamName.trim()
    const inviteUserIdsToSend = normalizeInviteUserIds(inviteUserIds)
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

      const createdTeam =
        body.data && typeof body.data === "object" && body.data !== null
          ? (body.data as { id?: unknown; name?: unknown })
          : null
      const createdTeamId =
        typeof createdTeam?.id === "string" || typeof createdTeam?.id === "number"
          ? String(createdTeam.id)
          : null

      if (inviteUserIdsToSend.length > 0 && createdTeamId) {
        let invitedCount = 0
        let failedCount = 0

        for (const userId of inviteUserIdsToSend) {
          try {
            const inviteRes = await fetch(`/api/team/v1/teams/${encodeURIComponent(createdTeamId)}/members`, {
              method: "POST",
              credentials: "include",
              headers: { "Content-Type": "application/json", Accept: "application/json" },
              body: JSON.stringify({ userId }),
            })
            if (inviteRes.ok) {
              invitedCount += 1
            } else {
              failedCount += 1
            }
          } catch {
            failedCount += 1
          }
        }

        if (failedCount > 0) {
          setMessage({
            kind: "error",
            text: `팀은 생성되었습니다. 초대 성공 ${invitedCount}명, 실패 ${failedCount}명`,
          })
        } else {
          setMessage({
            kind: "success",
            text: `팀이 생성되었고 ${invitedCount}명을 초대했습니다`,
          })
        }
      } else {
        setMessage({ kind: "success", text: "팀이 생성되었습니다" })
      }

      setTeamName("")
      setInviteUserIds([""])
      setShowCreateForm(false)
      await loadTeams()
    } catch {
      setMessage({ kind: "error", text: "팀 생성에 실패했습니다" })
    } finally {
      setCreateLoading(false)
    }
  }

  function addInviteInput() {
    setInviteUserIds((prev) => [...prev, ""])
  }

  function removeInviteInput(index: number) {
    setInviteUserIds((prev) => {
      if (prev.length === 1) return [""]
      return prev.filter((_, i) => i !== index)
    })
  }

  function updateInviteInput(index: number, value: string) {
    setInviteUserIds((prev) => prev.map((item, i) => (i === index ? value : item)))
  }

  function openCreateForm() {
    setMessage(null)
    setShowCreateForm(true)
  }

  function closeCreateForm() {
    setShowCreateForm(false)
    setTeamName("")
    setInviteUserIds([""])
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
        {!showCreateForm ? (
          <button
            type="button"
            className="h-10 rounded-md bg-black px-4 text-sm font-medium text-white disabled:opacity-60"
            onClick={openCreateForm}
            disabled={createLoading}
          >
            팀 만들기
          </button>
        ) : (
          <form className="space-y-3" onSubmit={createTeam}>
            <div className="space-y-1">
              <label htmlFor="team-name" className="text-xs font-medium text-zinc-700">
                팀 이름 (필수)
              </label>
              <input
                id="team-name"
                className="h-10 w-full rounded-md border border-zinc-300 bg-white px-3 text-sm"
                value={teamName}
                onChange={(e) => setTeamName(e.target.value)}
                placeholder="예: 플랫폼팀"
                autoComplete="off"
                disabled={createLoading}
                required
              />
            </div>

            <div className="space-y-2">
              <p className="text-xs font-medium text-zinc-700">팀원 초대 (선택)</p>
              {inviteUserIds.map((value, index) => (
                <div key={`invite-${index}`} className="flex items-center gap-2">
                  <input
                    className="h-10 flex-1 rounded-md border border-zinc-300 bg-white px-3 text-sm"
                    value={value}
                    onChange={(e) => updateInviteInput(index, e.target.value)}
                    placeholder="팀원 이메일(아이디) 입력"
                    autoComplete="email"
                    disabled={createLoading}
                  />
                  <button
                    type="button"
                    className="h-10 rounded-md border border-zinc-300 bg-white px-3 text-sm font-medium"
                    onClick={() => removeInviteInput(index)}
                    disabled={createLoading}
                    aria-label={`팀원 입력창 ${index + 1} 삭제`}
                  >
                    -
                  </button>
                </div>
              ))}
              <button
                type="button"
                className="h-9 rounded-md border border-zinc-300 bg-white px-3 text-sm font-medium"
                onClick={addInviteInput}
                disabled={createLoading}
              >
                + 팀원 추가
              </button>
            </div>

            <div className="flex gap-2">
              <button
                type="submit"
                className="h-10 rounded-md bg-black px-4 text-sm font-medium text-white disabled:opacity-60"
                disabled={createLoading}
              >
                {createLoading ? "생성 중..." : "생성"}
              </button>
              <button
                type="button"
                className="h-10 rounded-md border border-zinc-300 bg-white px-4 text-sm font-medium"
                onClick={closeCreateForm}
                disabled={createLoading}
              >
                취소
              </button>
            </div>
          </form>
        )}
      </section>

      {message ? <p className={message.kind === "success" ? "text-sm text-emerald-600" : "text-sm text-red-600"}>{message.text}</p> : null}
      {loading ? <p className="text-sm text-zinc-500">불러오는 중...</p> : null}
      {error && !loading ? <p className="text-sm text-red-600">{error}</p> : null}

      {!loading && !error && teams.length === 0 ? <p className="text-sm text-zinc-500">아직 팀이 구성되지 않았습니다.</p> : null}

      {!loading && teams.length > 0 ? (
        <ul className="max-w-lg divide-y divide-zinc-200 rounded-lg border border-zinc-200 bg-white">
          {teams.map((team) => (
            <li key={team.id} className="space-y-2 px-4 py-3">
              <p className="font-medium">{team.name}</p>
              <p className="text-xs text-zinc-500">id: {team.id}</p>
              <p className="text-xs text-zinc-600">멤버 수: {(teamMemberIdsByTeamId[team.id] ?? []).length}명</p>
              {(teamMemberIdsByTeamId[team.id] ?? []).length > 0 ? (
                <ul className="list-disc pl-5 text-xs text-zinc-700">
                  {(teamMemberIdsByTeamId[team.id] ?? []).map((memberId) => (
                    <li key={`${team.id}-${memberId}`}>{memberId}</li>
                  ))}
                </ul>
              ) : (
                <p className="text-xs text-zinc-500">초대된 멤버가 없습니다.</p>
              )}
              <div className="flex flex-col gap-2 sm:flex-row">
                <input
                  className="h-9 flex-1 rounded-md border border-zinc-300 bg-white px-3 text-sm"
                  value={inviteInputByTeamId[team.id] ?? ""}
                  onChange={(e) => setInviteInputByTeamId((prev) => ({ ...prev, [team.id]: e.target.value }))}
                  placeholder="초대할 사용자 이메일(아이디)"
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
