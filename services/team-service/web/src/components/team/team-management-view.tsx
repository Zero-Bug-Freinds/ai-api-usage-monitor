"use client"

import * as React from "react"
import { Eye, EyeOff } from "lucide-react"

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

type TeamApiKeySummary = {
  id: number
  provider: string
  alias: string
  keyPreview: string
  monthlyBudgetUsd: number | null
  createdAt: string
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

function normalizeTeamApiKeySummary(item: unknown): TeamApiKeySummary | null {
  if (!item || typeof item !== "object") return null
  const v = item as Record<string, unknown>
  if (typeof v.id !== "number") return null
  if (typeof v.provider !== "string") return null
  if (typeof v.alias !== "string") return null
  if (typeof v.keyPreview !== "string") return null
  if (typeof v.createdAt !== "string") return null
  const b = v.monthlyBudgetUsd
  let monthlyBudgetUsd: number | null = null
  if (typeof b === "number" && Number.isFinite(b)) {
    monthlyBudgetUsd = b
  } else if (typeof b === "string" && b.trim() !== "") {
    const n = Number(b)
    if (Number.isFinite(n)) monthlyBudgetUsd = n
  }
  return {
    id: v.id,
    provider: v.provider,
    alias: v.alias,
    keyPreview: v.keyPreview,
    monthlyBudgetUsd,
    createdAt: v.createdAt,
  }
}

function formatBudgetUsd(value: number | null | undefined) {
  if (value === null || value === undefined) return null
  return new Intl.NumberFormat("ko-KR", {
    style: "currency",
    currency: "USD",
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  }).format(value)
}

const BUDGET_STEP = 0.01

function normalizeBudgetNumericString(raw: string): string {
  const t = raw.trim()
  if (t === "") return ""
  const n = Number(t)
  if (!Number.isFinite(n) || n < 0) return ""
  const rounded = Math.round(n / BUDGET_STEP) * BUDGET_STEP
  return Number(rounded.toFixed(2)).toString()
}

export function TeamManagementView() {
  const [loading, setLoading] = React.useState(true)
  const [error, setError] = React.useState<string | null>(null)
  const [teams, setTeams] = React.useState<TeamSummary[]>([])
  const [teamName, setTeamName] = React.useState("")
  const [createLoading, setCreateLoading] = React.useState(false)
  const [inviteInputByTeamId, setInviteInputByTeamId] = React.useState<Record<string, string>>({})
  const [inviteLoadingTeamId, setInviteLoadingTeamId] = React.useState<string | null>(null)
  const [message, setMessage] = React.useState<{ kind: "success" | "error"; text: string } | null>(null)

  const [teamApiKeysByTeamId, setTeamApiKeysByTeamId] = React.useState<Record<string, TeamApiKeySummary[]>>({})
  const [apiKeyAliasByTeamId, setApiKeyAliasByTeamId] = React.useState<Record<string, string>>({})
  const [apiKeyValueByTeamId, setApiKeyValueByTeamId] = React.useState<Record<string, string>>({})
  const [apiKeyProviderByTeamId, setApiKeyProviderByTeamId] = React.useState<Record<string, string>>({})
  const [apiKeyMonthlyBudgetByTeamId, setApiKeyMonthlyBudgetByTeamId] = React.useState<Record<string, string>>({})
  const [apiKeyRevealByTeamId, setApiKeyRevealByTeamId] = React.useState<Record<string, boolean>>({})
  const [apiKeyLoadingTeamId, setApiKeyLoadingTeamId] = React.useState<string | null>(null)
  const [editingTeamApiKey, setEditingTeamApiKey] = React.useState<{ teamId: string; keyId: number } | null>(null)
  const [editTeamApiKeyAlias, setEditTeamApiKeyAlias] = React.useState("")
  const [editTeamApiKeyBudget, setEditTeamApiKeyBudget] = React.useState("")
  const [teamApiKeyUpdateLoading, setTeamApiKeyUpdateLoading] = React.useState<string | null>(null)

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

  const loadTeamApiKeys = React.useCallback(async (teamId: string) => {
    try {
      const res = await fetch(`/api/team/v1/teams/${encodeURIComponent(teamId)}/api-keys`, {
        method: "GET",
        credentials: "include",
        headers: { Accept: "application/json" },
        cache: "no-store",
      })
      const json = (await res.json()) as unknown
      const body = asApiResponse(json)
      if (!res.ok || !body?.success || !Array.isArray(body.data)) {
        setTeamApiKeysByTeamId((prev) => ({ ...prev, [teamId]: [] }))
        return
      }
      const apiKeyItems = body.data as unknown[]
      setTeamApiKeysByTeamId((prev) => ({
        ...prev,
        [teamId]: apiKeyItems
          .map((item) => normalizeTeamApiKeySummary(item))
          .filter((item): item is TeamApiKeySummary => item !== null),
      }))
    } catch {
      setTeamApiKeysByTeamId((prev) => ({ ...prev, [teamId]: [] }))
    }
  }, [])

  React.useEffect(() => {
    void loadTeams()
  }, [loadTeams])

  React.useEffect(() => {
    if (teams.length === 0) {
      setTeamApiKeysByTeamId({})
      return
    }
    for (const team of teams) {
      void loadTeamApiKeys(team.id)
    }
  }, [teams, loadTeamApiKeys])

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

  function cancelEditTeamApiKey() {
    setEditingTeamApiKey(null)
    setEditTeamApiKeyAlias("")
    setEditTeamApiKeyBudget("")
    setTeamApiKeyUpdateLoading(null)
  }

  function startEditTeamApiKey(teamId: string, row: TeamApiKeySummary) {
    setEditingTeamApiKey({ teamId, keyId: row.id })
    setEditTeamApiKeyAlias(row.alias)
    setEditTeamApiKeyBudget(
      row.monthlyBudgetUsd !== null && row.monthlyBudgetUsd !== undefined ? String(row.monthlyBudgetUsd) : "",
    )
    setMessage(null)
  }

  async function saveEditTeamApiKey(teamId: string) {
    if (!editingTeamApiKey || editingTeamApiKey.teamId !== teamId || teamApiKeyUpdateLoading) return
    const aliasTrimmed = editTeamApiKeyAlias.trim()
    const budgetTrimmed = editTeamApiKeyBudget.trim()
    if (!aliasTrimmed) {
      setMessage({ kind: "error", text: "API Key 별칭을 입력해 주세요" })
      return
    }
    if (!budgetTrimmed) {
      setMessage({ kind: "error", text: "월 예산은 필수입니다" })
      return
    }
    const fracEdit = budgetTrimmed.includes(".") ? (budgetTrimmed.split(".")[1] ?? "") : ""
    if (fracEdit.length > 2) {
      setMessage({ kind: "error", text: "월 예산은 소수점 둘째 자리까지만 입력할 수 있습니다" })
      return
    }
    const parsedBudget = Number(budgetTrimmed)
    if (!Number.isFinite(parsedBudget) || parsedBudget < 0) {
      setMessage({ kind: "error", text: "예산은 0 이상의 숫자로 입력해 주세요" })
      return
    }
    const monthlyBudgetUsd = Number(parsedBudget.toFixed(2))

    const keyId = editingTeamApiKey.keyId
    const row = (teamApiKeysByTeamId[teamId] ?? []).find((k) => k.id === keyId)
    if (row) {
      const normalizedCurrentBudget =
        row.monthlyBudgetUsd === null || row.monthlyBudgetUsd === undefined
          ? null
          : Number(row.monthlyBudgetUsd.toFixed(2))
      if (aliasTrimmed === row.alias && monthlyBudgetUsd === normalizedCurrentBudget) {
        cancelEditTeamApiKey()
        return
      }
    }

    setTeamApiKeyUpdateLoading(`${teamId}:${keyId}`)
    setMessage(null)
    try {
      const body = { alias: aliasTrimmed, monthlyBudgetUsd }
      const res = await fetch(
        `/api/team/v1/teams/${encodeURIComponent(teamId)}/api-keys/${encodeURIComponent(String(keyId))}`,
        {
          method: "PUT",
          credentials: "include",
          headers: { "Content-Type": "application/json", Accept: "application/json" },
          body: JSON.stringify(body),
        },
      )
      const json = (await res.json()) as unknown
      const bodyRes = asApiResponse(json)
      if (!res.ok || !bodyRes?.success) {
        setMessage({ kind: "error", text: bodyRes?.message ?? "팀 API Key 수정에 실패했습니다" })
        return
      }
      setMessage({ kind: "success", text: "팀 API Key가 수정되었습니다" })
      cancelEditTeamApiKey()
      await loadTeamApiKeys(teamId)
    } catch {
      setMessage({ kind: "error", text: "팀 API Key 수정에 실패했습니다" })
    } finally {
      setTeamApiKeyUpdateLoading(null)
    }
  }

  async function registerTeamApiKey(teamId: string) {
    if (apiKeyLoadingTeamId) return
    const provider = (apiKeyProviderByTeamId[teamId] ?? "OPENAI").trim()
    const alias = (apiKeyAliasByTeamId[teamId] ?? "").trim()
    const externalKey = (apiKeyValueByTeamId[teamId] ?? "").trim()
    const budgetTrimmed = (apiKeyMonthlyBudgetByTeamId[teamId] ?? "").trim()
    if (!alias) {
      setMessage({ kind: "error", text: "API Key 별칭을 입력해 주세요" })
      return
    }
    if (!externalKey) {
      setMessage({ kind: "error", text: "API Key 값을 입력해 주세요" })
      return
    }
    if (!budgetTrimmed) {
      setMessage({ kind: "error", text: "월 예산은 필수입니다" })
      return
    }
    const fracReg = budgetTrimmed.includes(".") ? (budgetTrimmed.split(".")[1] ?? "") : ""
    if (fracReg.length > 2) {
      setMessage({ kind: "error", text: "월 예산은 소수점 둘째 자리까지만 입력할 수 있습니다" })
      return
    }
    const parsedBudget = Number(budgetTrimmed)
    if (!Number.isFinite(parsedBudget) || parsedBudget < 0) {
      setMessage({ kind: "error", text: "예산은 0 이상의 숫자로 입력해 주세요" })
      return
    }
    const monthlyBudgetUsd = Number(parsedBudget.toFixed(2))

    setApiKeyLoadingTeamId(teamId)
    setMessage(null)
    try {
      const res = await fetch(`/api/team/v1/teams/${encodeURIComponent(teamId)}/api-keys`, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({ provider, alias, externalKey, monthlyBudgetUsd }),
      })
      const json = (await res.json()) as unknown
      const body = asApiResponse(json)
      if (!res.ok || !body?.success) {
        setMessage({ kind: "error", text: body?.message ?? "팀 API Key 등록에 실패했습니다" })
        return
      }
      setMessage({ kind: "success", text: "팀 API Key가 등록되었습니다" })
      setApiKeyAliasByTeamId((prev) => ({ ...prev, [teamId]: "" }))
      setApiKeyValueByTeamId((prev) => ({ ...prev, [teamId]: "" }))
      setApiKeyMonthlyBudgetByTeamId((prev) => ({ ...prev, [teamId]: "" }))
      setApiKeyRevealByTeamId((prev) => ({ ...prev, [teamId]: false }))
      await loadTeamApiKeys(teamId)
    } catch {
      setMessage({ kind: "error", text: "팀 API Key 등록에 실패했습니다" })
    } finally {
      setApiKeyLoadingTeamId(null)
    }
  }

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-4xl flex-col gap-6 px-4 py-8">
      <header className="space-y-2">
        <h1 className="text-2xl font-semibold">팀 관리</h1>
        <p className="text-sm text-zinc-600">
          팀 생성 후 사용자 아이디로 팀원을 초대할 수 있으며, 팀 API Key를 등록하고 월 예산(USD)을 설정할 수 있습니다.
        </p>
      </header>

      <section className="space-y-3 rounded-lg border border-zinc-200 bg-white p-4">
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
            {createLoading ? "생성 중…" : "팀 만들기"}
          </button>
        </form>
      </section>

      {message ? <p className={message.kind === "success" ? "text-sm text-emerald-600" : "text-sm text-red-600"}>{message.text}</p> : null}
      {loading ? <p className="text-sm text-zinc-500">불러오는 중…</p> : null}
      {error && !loading ? <p className="text-sm text-red-600">{error}</p> : null}

      {!loading && !error && teams.length === 0 ? <p className="text-sm text-zinc-500">참여 중인 팀이 없습니다.</p> : null}

      {!loading && teams.length > 0 ? (
        <ul className="divide-y divide-zinc-200 rounded-lg border border-zinc-200 bg-white">
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
                  {inviteLoadingTeamId === team.id ? "초대 중…" : "아이디로 초대"}
                </button>
              </div>

              <div className="space-y-2 rounded-md border border-zinc-200 bg-zinc-50 p-3">
                <p className="text-xs font-medium text-zinc-700">팀 API Key 등록</p>
                <div className="flex flex-col gap-2">
                  <select
                    className="h-9 rounded-md border border-zinc-300 bg-white px-3 text-xs"
                    value={apiKeyProviderByTeamId[team.id] ?? "OPENAI"}
                    onChange={(e) => setApiKeyProviderByTeamId((prev) => ({ ...prev, [team.id]: e.target.value }))}
                    disabled={apiKeyLoadingTeamId === team.id}
                  >
                    <option value="OPENAI">OPENAI</option>
                    <option value="GEMINI">GEMINI</option>
                    <option value="CLAUDE">CLAUDE</option>
                  </select>
                  <input
                    className="h-9 rounded-md border border-zinc-300 bg-white px-3 text-xs"
                    value={apiKeyAliasByTeamId[team.id] ?? ""}
                    onChange={(e) => setApiKeyAliasByTeamId((prev) => ({ ...prev, [team.id]: e.target.value }))}
                    placeholder="API Key 별칭"
                    autoComplete="off"
                    disabled={apiKeyLoadingTeamId === team.id}
                  />
                  <div className="flex gap-1">
                    <input
                      type={apiKeyRevealByTeamId[team.id] ? "text" : "password"}
                      className="h-9 min-w-0 flex-1 rounded-md border border-zinc-300 bg-white px-3 text-xs"
                      value={apiKeyValueByTeamId[team.id] ?? ""}
                      onChange={(e) => setApiKeyValueByTeamId((prev) => ({ ...prev, [team.id]: e.target.value }))}
                      placeholder="API Key 값"
                      autoComplete="new-password"
                      disabled={apiKeyLoadingTeamId === team.id}
                    />
                    <button
                      type="button"
                      className="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-md border border-zinc-300 bg-white text-zinc-600 hover:bg-zinc-50 disabled:opacity-50"
                      aria-label={apiKeyRevealByTeamId[team.id] ? "API Key 숨기기" : "API Key 보기"}
                      disabled={apiKeyLoadingTeamId === team.id}
                      onClick={() =>
                        setApiKeyRevealByTeamId((prev) => ({ ...prev, [team.id]: !prev[team.id] }))
                      }
                    >
                      {apiKeyRevealByTeamId[team.id] ? (
                        <EyeOff className="h-4 w-4" aria-hidden />
                      ) : (
                        <Eye className="h-4 w-4" aria-hidden />
                      )}
                    </button>
                  </div>
                  <input
                    type="number"
                    step={0.01}
                    min={0}
                    className="h-9 w-full rounded-md border border-zinc-300 bg-white px-3 text-xs"
                    value={apiKeyMonthlyBudgetByTeamId[team.id] ?? ""}
                    onChange={(e) => {
                      const v = e.target.value
                      if (v === "") {
                        setApiKeyMonthlyBudgetByTeamId((prev) => ({ ...prev, [team.id]: "" }))
                        return
                      }
                      const n = Number(v)
                      if (!Number.isFinite(n) || n < 0) return
                      setApiKeyMonthlyBudgetByTeamId((prev) => ({ ...prev, [team.id]: v }))
                    }}
                    onBlur={() =>
                      setApiKeyMonthlyBudgetByTeamId((prev) => {
                        const cur = prev[team.id] ?? ""
                        if (cur.trim() === "") return prev
                        const next = normalizeBudgetNumericString(cur)
                        if (next === cur) return prev
                        return { ...prev, [team.id]: next }
                      })
                    }
                    placeholder="월 예산 USD (스피너 ±0.01)"
                    inputMode="decimal"
                    autoComplete="off"
                    disabled={apiKeyLoadingTeamId === team.id}
                  />
                  <button
                    type="button"
                    className="h-9 rounded-md border border-zinc-300 bg-white px-3 text-xs font-medium disabled:opacity-60"
                    disabled={apiKeyLoadingTeamId === team.id}
                    onClick={() => void registerTeamApiKey(team.id)}
                  >
                    {apiKeyLoadingTeamId === team.id ? "등록 중…" : "팀 API Key 등록"}
                  </button>
                </div>

                {(teamApiKeysByTeamId[team.id] ?? []).length > 0 ? (
                  <ul className="space-y-2 text-xs text-zinc-700">
                    {(teamApiKeysByTeamId[team.id] ?? []).map((apiKey) => {
                      const isEditing =
                        editingTeamApiKey?.teamId === team.id && editingTeamApiKey?.keyId === apiKey.id
                      const updateKey = `${team.id}:${apiKey.id}`
                      const updating = teamApiKeyUpdateLoading === updateKey
                      return (
                        <li
                          key={`${team.id}-api-key-${apiKey.id}`}
                          className="rounded border border-zinc-200 bg-white px-2 py-2"
                        >
                          {!isEditing ? (
                            <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
                              <div>
                                <p>
                                  {apiKey.provider} / {apiKey.alias} / {apiKey.keyPreview}
                                </p>
                                <p className="text-[11px] text-zinc-500">
                                  월 예산:{" "}
                                  {formatBudgetUsd(apiKey.monthlyBudgetUsd ?? undefined) ?? "— (기존 데이터)"}
                                </p>
                              </div>
                              <button
                                type="button"
                                className="h-8 shrink-0 rounded-md border border-zinc-300 bg-white px-2 text-[11px] font-medium"
                                onClick={() => startEditTeamApiKey(team.id, apiKey)}
                              >
                                수정
                              </button>
                            </div>
                          ) : (
                            <div className="flex flex-col gap-2">
                              <input
                                className="h-8 rounded-md border border-zinc-300 bg-white px-2 text-xs"
                                value={editTeamApiKeyAlias}
                                onChange={(e) => setEditTeamApiKeyAlias(e.target.value)}
                                placeholder="별칭"
                                disabled={updating}
                              />
                              <input
                                type="number"
                                step={0.01}
                                min={0}
                                className="h-8 w-full max-w-[12rem] rounded-md border border-zinc-300 bg-white px-2 text-xs"
                                value={editTeamApiKeyBudget}
                                onChange={(e) => {
                                  const v = e.target.value
                                  if (v === "") {
                                    setEditTeamApiKeyBudget("")
                                    return
                                  }
                                  const n = Number(v)
                                  if (!Number.isFinite(n) || n < 0) return
                                  setEditTeamApiKeyBudget(v)
                                }}
                                onBlur={() =>
                                  setEditTeamApiKeyBudget((prev) =>
                                    prev.trim() === "" ? prev : normalizeBudgetNumericString(prev),
                                  )
                                }
                                placeholder="월 예산 USD"
                                inputMode="decimal"
                                disabled={updating}
                              />
                              <div className="flex gap-2">
                                <button
                                  type="button"
                                  className="h-8 rounded-md bg-black px-3 text-[11px] font-medium text-white disabled:opacity-60"
                                  disabled={updating}
                                  onClick={() => void saveEditTeamApiKey(team.id)}
                                >
                                  {updating ? "저장 중…" : "저장"}
                                </button>
                                <button
                                  type="button"
                                  className="h-8 rounded-md border border-zinc-300 bg-white px-3 text-[11px] font-medium"
                                  disabled={updating}
                                  onClick={cancelEditTeamApiKey}
                                >
                                  취소
                                </button>
                              </div>
                            </div>
                          )}
                        </li>
                      )
                    })}
                  </ul>
                ) : (
                  <p className="text-xs text-zinc-500">등록된 팀 API Key가 없습니다.</p>
                )}
              </div>
            </li>
          ))}
        </ul>
      ) : null}
    </main>
  )
}
