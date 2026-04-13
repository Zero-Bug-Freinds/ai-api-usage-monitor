"use client"

import * as React from "react"
import { ChevronDown, ChevronRight, Eye, EyeOff } from "lucide-react"

import { apiFetch } from "@/lib/api/client-fetch"
import type { ApiResponse } from "@/lib/api/identity/types"

type TeamSummary = {
  id: string
  name: string
}

type TeamSummaryLike = {
  id: string | number
  name: string
}

type TeamKeyProvider = "OPENAI" | "GEMINI" | "CLAUDE"

type TeamApiKeySummary = {
  id: number
  provider: string
  alias: string
  monthlyBudgetUsd: number | null
  createdAt: string
  deletionRequestedAt?: string | null
  permanentDeletionAt?: string | null
  deletionGraceDays?: number | null
}

function asApiResponse(json: unknown): ApiResponse<unknown> | null {
  if (!json || typeof json !== "object") return null
  const r = json as Record<string, unknown>
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
  if (typeof v.createdAt !== "string") return null
  const b = v.monthlyBudgetUsd
  let monthlyBudgetUsd: number | null = null
  if (typeof b === "number" && Number.isFinite(b)) {
    monthlyBudgetUsd = b
  } else if (typeof b === "string" && b.trim() !== "") {
    const n = Number(b)
    if (Number.isFinite(n)) monthlyBudgetUsd = n
  }
  const delReq = v.deletionRequestedAt
  const delPerm = v.permanentDeletionAt
  const gd = v.deletionGraceDays
  let deletionGraceDays: number | null = null
  if (typeof gd === "number" && Number.isFinite(gd)) {
    deletionGraceDays = gd
  }
  return {
    id: v.id,
    provider: v.provider,
    alias: v.alias,
    monthlyBudgetUsd,
    createdAt: v.createdAt,
    deletionRequestedAt: typeof delReq === "string" ? delReq : null,
    permanentDeletionAt: typeof delPerm === "string" ? delPerm : null,
    deletionGraceDays,
  }
}

function providerLabel(provider: string) {
  if (provider === "OPENAI") return "OpenAI"
  if (provider === "GEMINI") return "Gemini"
  if (provider === "CLAUDE") return "Claude"
  return provider
}

function defaultAlias(provider: TeamKeyProvider) {
  return `${providerLabel(provider)} 팀 키 1`
}

function formatCreatedAt(iso: string) {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleString("ko-KR", { timeZone: "Asia/Seoul" })
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

const DEFAULT_DELETION_GRACE_DAYS = 7
const MIN_DELETION_GRACE_DAYS = 0
const MAX_DELETION_GRACE_DAYS = 365

/** blur 또는 스피너 조정 후 소수 둘째 자리·0.01 단위로 맞춤 */
function normalizeBudgetNumericString(raw: string): string {
  const t = raw.trim()
  if (t === "") return ""
  const n = Number(t)
  if (!Number.isFinite(n) || n < 0) return ""
  const rounded = Math.round(n / BUDGET_STEP) * BUDGET_STEP
  return Number(rounded.toFixed(2)).toString()
}

export function TeamsView() {
  const [loading, setLoading] = React.useState(true)
  const [error, setError] = React.useState<string | null>(null)
  const [teams, setTeams] = React.useState<TeamSummary[]>([])
  const [teamMemberIdsByTeamId, setTeamMemberIdsByTeamId] = React.useState<Record<string, string[]>>({})
  const [isTeamOwnerByTeamId, setIsTeamOwnerByTeamId] = React.useState<Record<string, boolean>>({})
  const [showCreateForm, setShowCreateForm] = React.useState(false)
  const [teamName, setTeamName] = React.useState("")
  const [inviteUserIds, setInviteUserIds] = React.useState<string[]>([""])
  const [createLoading, setCreateLoading] = React.useState(false)
  const [inviteInputByTeamId, setInviteInputByTeamId] = React.useState<Record<string, string>>({})
  const [inviteLoadingTeamId, setInviteLoadingTeamId] = React.useState<string | null>(null)
  const [message, setMessage] = React.useState<{ kind: "success" | "error"; text: string } | null>(null)

  const [teamApiKeysByTeamId, setTeamApiKeysByTeamId] = React.useState<Record<string, TeamApiKeySummary[]>>({})
  /** 목록 로드 실패(팀별). 수정·삭제 검증 오류는 `keysError`. */
  const [apiKeysListErrorByTeamId, setApiKeysListErrorByTeamId] = React.useState<Record<string, string | null>>({})
  const [keysError, setKeysError] = React.useState<string | null>(null)
  const [apiKeyAliasByTeamId, setApiKeyAliasByTeamId] = React.useState<Record<string, string>>({})
  const [apiKeyValueByTeamId, setApiKeyValueByTeamId] = React.useState<Record<string, string>>({})
  const [apiKeyProviderByTeamId, setApiKeyProviderByTeamId] = React.useState<Record<string, TeamKeyProvider>>({})
  const [apiKeyMonthlyBudgetByTeamId, setApiKeyMonthlyBudgetByTeamId] = React.useState<Record<string, string>>({})
  const [apiKeyAliasTouchedByTeamId, setApiKeyAliasTouchedByTeamId] = React.useState<Record<string, boolean>>({})
  const [apiKeyRegisterLoadingTeamId, setApiKeyRegisterLoadingTeamId] = React.useState<string | null>(null)
  const [apiKeyRegisterMessageByTeamId, setApiKeyRegisterMessageByTeamId] = React.useState<
    Record<string, { kind: "success" | "error"; text: string }>
  >({})
  const [apiKeyRevealByTeamId, setApiKeyRevealByTeamId] = React.useState<Record<string, boolean>>({})

  const [editingKey, setEditingKey] = React.useState<{ teamId: string; row: TeamApiKeySummary } | null>(null)
  const [editAlias, setEditAlias] = React.useState("")
  const [editBudget, setEditBudget] = React.useState("")
  const [saveEditLoading, setSaveEditLoading] = React.useState(false)
  const [deleteLoadingKey, setDeleteLoadingKey] = React.useState<string | null>(null)
  const [cancelDeleteLoadingKey, setCancelDeleteLoadingKey] = React.useState<string | null>(null)
  const [removeMemberLoadingKey, setRemoveMemberLoadingKey] = React.useState<string | null>(null)
  const [deleteTeamLoadingId, setDeleteTeamLoadingId] = React.useState<string | null>(null)
  /** 목록에서는 이름만 보이고, 펼친 팀만 상세·API 조회 */
  const [openTeamId, setOpenTeamId] = React.useState<string | null>(null)

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
      const { response, json } = await apiFetch<unknown>(
        "/api/team/v1/me/teams",
        { method: "GET", credentials: "include", headers: { Accept: "application/json" }, cache: "no-store" },
        { authRequired: true }
      )
      const body = asApiResponse(json)

      if (response.status === 404) {
        setTeams([])
        return
      }

      if (!response.ok || !body?.success) {
        setError(body?.message ?? "팀 목록을 불러오지 못했습니다")
        setTeams([])
        return
      }

      if (body.data === null || !Array.isArray(body.data)) {
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
      const { response, json } = await apiFetch<unknown>(
        `/api/team/v1/teams/${encodeURIComponent(teamId)}/members`,
        { method: "GET", credentials: "include", headers: { Accept: "application/json" }, cache: "no-store" },
        { authRequired: true }
      )
      const body = asApiResponse(json)
      if (!response.ok || !body?.success || !Array.isArray(body.data)) {
        setTeamMemberIdsByTeamId((prev) => ({ ...prev, [teamId]: [] }))
        return
      }
      const memberIds = body.data.filter((v) => typeof v === "string").map((v) => String(v))
      setTeamMemberIdsByTeamId((prev) => ({ ...prev, [teamId]: memberIds }))
    } catch {
      setTeamMemberIdsByTeamId((prev) => ({ ...prev, [teamId]: [] }))
    }
  }, [])

  const loadTeamApiKeys = React.useCallback(async (teamId: string) => {
    try {
      const { response, json } = await apiFetch<unknown>(
        `/api/team/v1/teams/${encodeURIComponent(teamId)}/api-keys`,
        { method: "GET", credentials: "include", headers: { Accept: "application/json" }, cache: "no-store" },
        { authRequired: true }
      )
      const body = asApiResponse(json)
      if (!response.ok || !body?.success || !Array.isArray(body.data)) {
        setTeamApiKeysByTeamId((prev) => ({ ...prev, [teamId]: [] }))
        const msg =
          json && typeof json === "object" && "message" in json
            ? String((json as { message: string }).message)
            : "팀 API Key 목록을 불러오지 못했습니다"
        setApiKeysListErrorByTeamId((prev) => ({ ...prev, [teamId]: msg }))
        return
      }
      const items = body.data as unknown[]
      setTeamApiKeysByTeamId((prev) => ({
        ...prev,
        [teamId]: items
          .map((item) => normalizeTeamApiKeySummary(item))
          .filter((item): item is TeamApiKeySummary => item !== null),
      }))
      setApiKeysListErrorByTeamId((prev) => ({ ...prev, [teamId]: null }))
    } catch {
      setTeamApiKeysByTeamId((prev) => ({ ...prev, [teamId]: [] }))
      setApiKeysListErrorByTeamId((prev) => ({
        ...prev,
        [teamId]: "팀 API Key 목록을 불러오지 못했습니다",
      }))
    }
  }, [])

  const loadTeamOwnerFlag = React.useCallback(async (teamId: string) => {
    try {
      const { response, json } = await apiFetch<unknown>(
        `/api/team/v1/teams/${encodeURIComponent(teamId)}/owner`,
        { method: "GET", credentials: "include", headers: { Accept: "application/json" }, cache: "no-store" },
        { authRequired: true }
      )
      const body = asApiResponse(json)
      if (!response.ok || !body?.success || typeof body.data !== "boolean") {
        setIsTeamOwnerByTeamId((prev) => ({ ...prev, [teamId]: false }))
        return
      }
      const owner = body.data
      setIsTeamOwnerByTeamId((prev) => ({ ...prev, [teamId]: owner }))
    } catch {
      setIsTeamOwnerByTeamId((prev) => ({ ...prev, [teamId]: false }))
    }
  }, [])

  React.useEffect(() => {
    void loadTeams()
  }, [loadTeams])

  React.useEffect(() => {
    if (teams.length === 0) {
      setTeamMemberIdsByTeamId({})
      setIsTeamOwnerByTeamId({})
      setTeamApiKeysByTeamId({})
      setApiKeysListErrorByTeamId({})
      setOpenTeamId(null)
    }
  }, [teams.length])

  React.useEffect(() => {
    if (openTeamId && !teams.some((t) => t.id === openTeamId)) {
      setOpenTeamId(null)
    }
  }, [teams, openTeamId])

  React.useEffect(() => {
    if (!openTeamId) return
    if (!teams.some((t) => t.id === openTeamId)) return
    void loadTeamOwnerFlag(openTeamId)
    void loadTeamMembers(openTeamId)
    void loadTeamApiKeys(openTeamId)
  }, [openTeamId, teams, loadTeamOwnerFlag, loadTeamMembers, loadTeamApiKeys])

  React.useEffect(() => {
    setApiKeyAliasByTeamId((prev) => {
      let changed = false
      const next = { ...prev }
      for (const team of teams) {
        if (next[team.id] === undefined) {
          next[team.id] = defaultAlias("OPENAI")
          changed = true
        }
      }
      return changed ? next : prev
    })
  }, [teams])

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
      const { response, json } = await apiFetch<unknown>(
        "/api/team/v1/teams",
        {
          method: "POST",
          credentials: "include",
          headers: { "Content-Type": "application/json", Accept: "application/json" },
          body: JSON.stringify({ name }),
        },
        { authRequired: true }
      )
      const body = asApiResponse(json)
      if (!response.ok || !body?.success) {
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
            const inviteRes = await apiFetch<unknown>(
              `/api/team/v1/teams/${encodeURIComponent(createdTeamId)}/members`,
              {
                method: "POST",
                credentials: "include",
                headers: { "Content-Type": "application/json", Accept: "application/json" },
                body: JSON.stringify({ userId }),
              },
              { authRequired: true }
            )
            if (inviteRes.response.ok) invitedCount += 1
            else failedCount += 1
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
      const { response, json } = await apiFetch<unknown>(
        `/api/team/v1/teams/${encodeURIComponent(teamId)}/members`,
        {
          method: "POST",
          credentials: "include",
          headers: { "Content-Type": "application/json", Accept: "application/json" },
          body: JSON.stringify({ userId }),
        },
        { authRequired: true }
      )
      const body = asApiResponse(json)
      if (!response.ok || !body?.success) {
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

  function cancelEditKey() {
    setEditingKey(null)
    setEditAlias("")
    setEditBudget("")
    setSaveEditLoading(false)
  }

  function startEditKey(teamId: string, row: TeamApiKeySummary) {
    setKeysError(null)
    setEditingKey({ teamId, row })
    setEditAlias(row.alias)
    setEditBudget(
      row.monthlyBudgetUsd !== null && row.monthlyBudgetUsd !== undefined ? String(row.monthlyBudgetUsd) : "",
    )
  }

  async function saveEditKey() {
    if (!editingKey) return
    const { teamId, row } = editingKey
    const aliasTrimmed = editAlias.trim()
    const budgetTrimmed = editBudget.trim()
    if (!aliasTrimmed) {
      setKeysError("별칭은 필수입니다")
      return
    }
    if (!budgetTrimmed) {
      setKeysError("월 예산은 필수입니다")
      return
    }
    const fracPart = budgetTrimmed.includes(".") ? (budgetTrimmed.split(".")[1] ?? "") : ""
    if (fracPart.length > 2) {
      setKeysError("월 예산은 소수점 둘째 자리까지만 입력할 수 있습니다")
      return
    }
    const parsedBudget = Number(budgetTrimmed)
    if (!Number.isFinite(parsedBudget) || parsedBudget < 0) {
      setKeysError("예산은 0 이상의 숫자로 입력해 주세요")
      return
    }
    const monthlyBudgetUsd = Number(parsedBudget.toFixed(2))
    const normalizedCurrentBudget =
      row.monthlyBudgetUsd === null || row.monthlyBudgetUsd === undefined
        ? null
        : Number(row.monthlyBudgetUsd.toFixed(2))
    if (aliasTrimmed === row.alias && monthlyBudgetUsd === normalizedCurrentBudget) {
      cancelEditKey()
      return
    }

    const body = { alias: aliasTrimmed, monthlyBudgetUsd }

    setSaveEditLoading(true)
    setKeysError(null)
    try {
      const { response, json } = await apiFetch<unknown>(
        `/api/team/v1/teams/${encodeURIComponent(teamId)}/api-keys/${encodeURIComponent(String(row.id))}`,
        {
          method: "PUT",
          credentials: "include",
          cache: "no-store",
          headers: { "Content-Type": "application/json", Accept: "application/json" },
          body: JSON.stringify(body),
        },
        { authRequired: true }
      )
      const apiResponse = asApiResponse(json)
      if (response.ok && apiResponse?.success) {
        cancelEditKey()
        await loadTeamApiKeys(teamId)
      } else {
        setKeysError(apiResponse?.message ?? "팀 API Key 수정에 실패했습니다")
      }
    } catch {
      setKeysError("팀 API Key 수정에 실패했습니다")
    } finally {
      setSaveEditLoading(false)
    }
  }

  async function deleteTeamKey(teamId: string, keyId: number) {
    const ok = window.confirm(
      `이 팀 API 키 삭제를 진행합니다.\n\n0일을 입력하면 즉시 영구 삭제되고, 1일 이상을 입력하면 삭제 예정으로 등록됩니다.\n삭제 예정 상태에서는 언제든 삭제를 취소할 수 있습니다.\n\n다음 단계에서 유예 기간(일)을 입력합니다. (기본 ${DEFAULT_DELETION_GRACE_DAYS}일)`,
    )
    if (!ok) return
    const raw = window.prompt(
      `유예 기간(일) (${MIN_DELETION_GRACE_DAYS}~${MAX_DELETION_GRACE_DAYS}, 0은 즉시 삭제, 비우면 ${DEFAULT_DELETION_GRACE_DAYS}일)`,
      String(DEFAULT_DELETION_GRACE_DAYS),
    )
    if (raw === null) return
    const trimmed = raw.trim()
    let graceDays = DEFAULT_DELETION_GRACE_DAYS
    if (trimmed !== "") {
      const n = Number.parseInt(trimmed, 10)
      if (!Number.isFinite(n) || n < MIN_DELETION_GRACE_DAYS || n > MAX_DELETION_GRACE_DAYS) {
        setKeysError(`유예 기간은 ${MIN_DELETION_GRACE_DAYS}~${MAX_DELETION_GRACE_DAYS}일 사이의 정수로 입력해 주세요`)
        return
      }
      graceDays = n
    }
    const loadingKey = `${teamId}:${keyId}`
    setDeleteLoadingKey(loadingKey)
    setKeysError(null)
    try {
      const q = new URLSearchParams({ gracePeriodDays: String(graceDays) })
      const { response, json } = await apiFetch<unknown>(
        `/api/team/v1/teams/${encodeURIComponent(teamId)}/api-keys/${encodeURIComponent(String(keyId))}?${q.toString()}`,
        { method: "DELETE", credentials: "include", cache: "no-store", headers: { Accept: "application/json" } },
        { authRequired: true }
      )
      const apiResponse = asApiResponse(json)
      if (response.ok && apiResponse?.success) {
        if (editingKey?.teamId === teamId && editingKey.row.id === keyId) cancelEditKey()
        await loadTeamApiKeys(teamId)
      } else {
        setKeysError(apiResponse?.message ?? "삭제에 실패했습니다")
      }
    } catch {
      setKeysError("삭제에 실패했습니다")
    } finally {
      setDeleteLoadingKey(null)
    }
  }

  async function cancelTeamKeyDeletion(teamId: string, keyId: number) {
    const ok = window.confirm("삭제 예정을 해제하고 이 키를 계속 사용하시겠습니까?")
    if (!ok) return
    const loadingKey = `${teamId}:${keyId}`
    setCancelDeleteLoadingKey(loadingKey)
    setKeysError(null)
    try {
      const { response, json } = await apiFetch<unknown>(
        `/api/team/v1/teams/${encodeURIComponent(teamId)}/api-keys/${encodeURIComponent(String(keyId))}/deletion/cancel`,
        { method: "POST", credentials: "include", cache: "no-store", headers: { Accept: "application/json" } },
        { authRequired: true },
      )
      const apiResponse = asApiResponse(json)
      if (response.ok && apiResponse?.success) {
        if (editingKey?.teamId === teamId && editingKey.row.id === keyId) cancelEditKey()
        await loadTeamApiKeys(teamId)
      } else {
        setKeysError(apiResponse?.message ?? "삭제 예정 해제에 실패했습니다")
      }
    } catch {
      setKeysError("삭제 예정 해제에 실패했습니다")
    } finally {
      setCancelDeleteLoadingKey(null)
    }
  }

  async function registerTeamApiKey(teamId: string) {
    if (apiKeyRegisterLoadingTeamId) return
    const provider = apiKeyProviderByTeamId[teamId] ?? "OPENAI"
    const alias = (apiKeyAliasByTeamId[teamId] ?? "").trim()
    const externalKey = (apiKeyValueByTeamId[teamId] ?? "").trim()
    const budgetTrimmed = (apiKeyMonthlyBudgetByTeamId[teamId] ?? "").trim()

    if (!alias) {
      setApiKeyRegisterMessageByTeamId((prev) => ({
        ...prev,
        [teamId]: { kind: "error", text: "API Key 별칭을 입력해 주세요" },
      }))
      return
    }
    if (!externalKey) {
      setApiKeyRegisterMessageByTeamId((prev) => ({
        ...prev,
        [teamId]: { kind: "error", text: "API Key 값을 입력해 주세요" },
      }))
      return
    }
    if (!budgetTrimmed) {
      setApiKeyRegisterMessageByTeamId((prev) => ({
        ...prev,
        [teamId]: { kind: "error", text: "월 예산은 필수입니다" },
      }))
      return
    }
    const fracRegister = budgetTrimmed.includes(".") ? (budgetTrimmed.split(".")[1] ?? "") : ""
    if (fracRegister.length > 2) {
      setApiKeyRegisterMessageByTeamId((prev) => ({
        ...prev,
        [teamId]: { kind: "error", text: "월 예산은 소수점 둘째 자리까지만 입력할 수 있습니다" },
      }))
      return
    }
    const parsedBudget = Number(budgetTrimmed)
    if (!Number.isFinite(parsedBudget) || parsedBudget < 0) {
      setApiKeyRegisterMessageByTeamId((prev) => ({
        ...prev,
        [teamId]: { kind: "error", text: "예산은 0 이상의 숫자로 입력해 주세요" },
      }))
      return
    }
    const monthlyBudgetUsd = Number(parsedBudget.toFixed(2))

    setApiKeyRegisterLoadingTeamId(teamId)
    try {
      const { response, json } = await apiFetch<unknown>(
        `/api/team/v1/teams/${encodeURIComponent(teamId)}/api-keys`,
        {
          method: "POST",
          credentials: "include",
          cache: "no-store",
          headers: { "Content-Type": "application/json", Accept: "application/json" },
          body: JSON.stringify({ provider, alias, externalKey, monthlyBudgetUsd }),
        },
        { authRequired: true }
      )
      const apiResponse = asApiResponse(json)
      if (response.ok && apiResponse?.success) {
        setApiKeyRegisterMessageByTeamId((prev) => ({
          ...prev,
          [teamId]: { kind: "success", text: apiResponse.message || "등록되었습니다" },
        }))
        setApiKeyAliasTouchedByTeamId((prev) => ({ ...prev, [teamId]: false }))
        setApiKeyAliasByTeamId((prev) => ({ ...prev, [teamId]: defaultAlias(provider) }))
        setApiKeyValueByTeamId((prev) => ({ ...prev, [teamId]: "" }))
        setApiKeyMonthlyBudgetByTeamId((prev) => ({ ...prev, [teamId]: "" }))
        setApiKeyRevealByTeamId((prev) => ({ ...prev, [teamId]: false }))
        await loadTeamApiKeys(teamId)
      } else {
        setApiKeyRegisterMessageByTeamId((prev) => ({
          ...prev,
          [teamId]: { kind: "error", text: apiResponse?.message ?? "등록에 실패했습니다" },
        }))
      }
    } catch {
      setApiKeyRegisterMessageByTeamId((prev) => ({
        ...prev,
        [teamId]: { kind: "error", text: "등록에 실패했습니다" },
      }))
    } finally {
      setApiKeyRegisterLoadingTeamId(null)
    }
  }

  async function removeTeamMember(teamId: string, memberId: string) {
    const ok = window.confirm(`팀원 "${memberId}"를 팀에서 삭제하시겠습니까?`)
    if (!ok) return
    const loadingKey = `${teamId}:${memberId}`
    setRemoveMemberLoadingKey(loadingKey)
    setMessage(null)
    try {
      const { response, json } = await apiFetch<unknown>(
        `/api/team/v1/teams/${encodeURIComponent(teamId)}/members/${encodeURIComponent(memberId)}`,
        { method: "DELETE", credentials: "include", headers: { Accept: "application/json" }, cache: "no-store" },
        { authRequired: true }
      )
      const body = asApiResponse(json)
      if (!response.ok || !body?.success) {
        setMessage({ kind: "error", text: body?.message ?? "팀원 삭제에 실패했습니다" })
        return
      }
      setMessage({ kind: "success", text: "팀원을 삭제했습니다" })
      await loadTeamMembers(teamId)
    } catch {
      setMessage({ kind: "error", text: "팀원 삭제에 실패했습니다" })
    } finally {
      setRemoveMemberLoadingKey(null)
    }
  }

  async function deleteTeam(teamId: string, teamName: string) {
    const ok = window.confirm(
      `"${teamName}" 팀을 삭제하시겠습니까?\n\n팀 API Key가 모두 삭제된 상태여야 삭제할 수 있습니다.`
    )
    if (!ok) return
    setDeleteTeamLoadingId(teamId)
    setMessage(null)
    try {
      const { response, json } = await apiFetch<unknown>(
        `/api/team/v1/teams/${encodeURIComponent(teamId)}`,
        { method: "DELETE", credentials: "include", headers: { Accept: "application/json" }, cache: "no-store" },
        { authRequired: true }
      )
      const body = asApiResponse(json)
      if (!response.ok || !body?.success) {
        setMessage({ kind: "error", text: body?.message ?? "팀 삭제에 실패했습니다" })
        return
      }
      setMessage({ kind: "success", text: "팀을 삭제했습니다" })
      cancelEditKey()
      await loadTeams()
    } catch {
      setMessage({ kind: "error", text: "팀 삭제에 실패했습니다" })
    } finally {
      setDeleteTeamLoadingId(null)
    }
  }

  return (
    <div className="flex min-h-[40vh] flex-col gap-8 py-4">
      <header className="space-y-2">
        <h1 className="text-2xl font-semibold tracking-tight">팀 관리</h1>
        <p className="text-sm text-muted-foreground">
          팀을 만든 뒤 멤버를 초대하고, 팀 단위 공급사 API 키와 월 예산(USD)을 등록·수정·삭제할 수 있습니다. (계정 설정의 개인 외부 키와 같은 사용 흐름입니다.)
        </p>
      </header>

      <section className="max-w-lg space-y-3 rounded-lg border border-border bg-card p-5 shadow-sm">
        {!showCreateForm ? (
          <button
            type="button"
            className="inline-flex h-10 items-center justify-center rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground disabled:opacity-60"
            onClick={openCreateForm}
            disabled={createLoading}
          >
            팀 만들기
          </button>
        ) : (
          <form className="space-y-3" onSubmit={createTeam}>
            <div className="space-y-1">
              <label htmlFor="team-name" className="text-sm font-medium">
                팀 이름 (필수)
              </label>
              <input
                id="team-name"
                className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                value={teamName}
                onChange={(e) => setTeamName(e.target.value)}
                placeholder="예: 플랫폼팀"
                autoComplete="off"
                disabled={createLoading}
                required
              />
            </div>

            <div className="space-y-2">
              <p className="text-sm font-medium">팀원 초대 (선택)</p>
              {inviteUserIds.map((value, index) => (
                <div key={`invite-${index}`} className="flex items-center gap-2">
                  <input
                    className="h-10 flex-1 rounded-md border border-input bg-background px-3 text-sm"
                    value={value}
                    onChange={(e) => updateInviteInput(index, e.target.value)}
                    placeholder="팀원 이메일(아이디) 입력"
                    autoComplete="email"
                    disabled={createLoading}
                  />
                  <button
                    type="button"
                    className="h-10 rounded-md border border-border bg-background px-3 text-sm font-medium"
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
                className="h-9 rounded-md border border-border bg-background px-3 text-sm font-medium"
                onClick={addInviteInput}
                disabled={createLoading}
              >
                + 팀원 추가
              </button>
            </div>

            <div className="flex gap-2">
              <button
                type="submit"
                className="inline-flex h-10 items-center justify-center rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground disabled:opacity-60"
                disabled={createLoading}
              >
                {createLoading ? "생성 중…" : "생성"}
              </button>
              <button
                type="button"
                className="h-10 rounded-md border border-border bg-background px-4 text-sm font-medium"
                onClick={closeCreateForm}
                disabled={createLoading}
              >
                취소
              </button>
            </div>
          </form>
        )}
      </section>

      {message ? (
        <p className={message.kind === "success" ? "text-sm text-emerald-600" : "text-sm text-destructive"}>{message.text}</p>
      ) : null}
      {loading ? <p className="text-sm text-muted-foreground">불러오는 중…</p> : null}
      {error && !loading ? <p className="text-sm text-destructive">{error}</p> : null}

      {!loading && !error && teams.length === 0 ? (
        <p className="text-sm text-muted-foreground">아직 팀이 구성되지 않았습니다.</p>
      ) : null}

      {keysError ? <p className="text-sm text-destructive">{keysError}</p> : null}

      {!loading && teams.length > 0 ? (
        <ul className="max-w-lg divide-y divide-border rounded-lg border border-border bg-card shadow-sm">
          {teams.map((team) => {
            const isOpen = openTeamId === team.id
            const isOwner = isTeamOwnerByTeamId[team.id] === true
            return (
            <li key={team.id} className="overflow-hidden">
              <button
                type="button"
                className="flex w-full items-center gap-2 px-4 py-3 text-left transition-colors hover:bg-muted/60"
                aria-expanded={isOpen}
                onClick={() => {
                  setOpenTeamId((prev) => {
                    if (prev === team.id) {
                      cancelEditKey()
                      return null
                    }
                    cancelEditKey()
                    return team.id
                  })
                }}
              >
                {isOpen ? (
                  <ChevronDown className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
                ) : (
                  <ChevronRight className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
                )}
                <span className="min-w-0 flex-1 font-medium">{team.name}</span>
              </button>

              {isOpen ? (
              <div className="space-y-4 border-t border-border bg-muted/5 px-4 pb-4 pt-2">
              <div>
                <p className="text-xs text-muted-foreground">id: {team.id}</p>
                <p className="mt-1 text-xs text-muted-foreground">
                  멤버 {(teamMemberIdsByTeamId[team.id] ?? []).length}명
                </p>
                {(teamMemberIdsByTeamId[team.id] ?? []).length > 0 ? (
                  <ul className="mt-2 space-y-1 text-xs text-muted-foreground">
                    {(teamMemberIdsByTeamId[team.id] ?? []).map((memberId) => (
                      <li key={`${team.id}-${memberId}`} className="flex items-center justify-between gap-2">
                        <span className="truncate">{memberId}</span>
                        {isOwner ? (
                          <button
                            type="button"
                            className="shrink-0 rounded-md border border-destructive/40 bg-background px-2 py-1 text-[11px] font-medium text-destructive hover:bg-destructive/10 disabled:opacity-50"
                            disabled={removeMemberLoadingKey === `${team.id}:${memberId}`}
                            onClick={() => void removeTeamMember(team.id, memberId)}
                          >
                            {removeMemberLoadingKey === `${team.id}:${memberId}` ? "삭제 중…" : "팀원 삭제"}
                          </button>
                        ) : null}
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="mt-1 text-xs text-muted-foreground">초대된 멤버가 없습니다.</p>
                )}
                {!isOwner ? (
                  <p className="mt-2 text-xs text-muted-foreground">팀장만 팀원 삭제를 할 수 있습니다.</p>
                ) : null}
                <div className="mt-2 flex flex-col gap-2 sm:flex-row">
                  <input
                    className="h-9 flex-1 rounded-md border border-input bg-background px-3 text-sm"
                    value={inviteInputByTeamId[team.id] ?? ""}
                    onChange={(e) => setInviteInputByTeamId((prev) => ({ ...prev, [team.id]: e.target.value }))}
                    placeholder="초대할 사용자 이메일(아이디)"
                    autoComplete="off"
                    disabled={inviteLoadingTeamId === team.id}
                  />
                  <button
                    type="button"
                    className="h-9 shrink-0 rounded-md border border-border bg-background px-3 text-xs font-medium disabled:opacity-60"
                    disabled={inviteLoadingTeamId === team.id}
                    onClick={() => void invite(team.id)}
                  >
                    {inviteLoadingTeamId === team.id ? "초대 중…" : "아이디로 초대"}
                  </button>
                </div>
              </div>

              <div className="space-y-2 rounded-md border border-border bg-muted/20 p-3">
                <div className="space-y-1">
                  <p className="text-sm font-semibold">등록된 팀 API Key</p>
                  <p className="text-xs text-muted-foreground">
                    Provider·별칭·월 예산을 표시합니다. 수정 시에는 별칭과 월 예산만 바꿀 수 있고, 키 값은 등록 시에만 설정됩니다.
                  </p>
                </div>
                {apiKeysListErrorByTeamId[team.id] ? (
                  <p className="text-xs text-destructive">{apiKeysListErrorByTeamId[team.id]}</p>
                ) : null}

                {(teamApiKeysByTeamId[team.id] ?? []).length > 0 ? (
                  <ul className="divide-y divide-border rounded-md border border-border bg-background">
                    {(teamApiKeysByTeamId[team.id] ?? []).map((apiKey) => {
                      const isEditing = editingKey?.teamId === team.id && editingKey.row.id === apiKey.id
                      const delKey = `${team.id}:${apiKey.id}`
                      const deleting = deleteLoadingKey === delKey
                      const canceling = cancelDeleteLoadingKey === delKey
                      const keyPendingDeletion = Boolean(apiKey.deletionRequestedAt)
                      return (
                        <li key={`${team.id}-key-${apiKey.id}`} className="flex flex-col gap-2 px-3 py-3 text-sm sm:flex-row sm:items-start sm:justify-between">
                          <div className="min-w-0 space-y-1">
                            <div className="font-medium">
                              <span>{providerLabel(apiKey.provider)}</span>
                              <span className="mx-2 text-muted-foreground">·</span>
                              {isEditing ? (
                                <span className="inline-flex flex-col gap-2 align-middle">
                                  <input
                                    className="h-8 rounded-md border border-input bg-background px-2 text-sm"
                                    value={editAlias}
                                    onChange={(e) => setEditAlias(e.target.value)}
                                    disabled={saveEditLoading}
                                    autoComplete="off"
                                  />
                                  <input
                                    type="number"
                                    step={0.01}
                                    min={0}
                                    className="h-8 w-full max-w-[12rem] rounded-md border border-input bg-background px-2 text-sm"
                                    inputMode="decimal"
                                    value={editBudget}
                                    onChange={(e) => {
                                      const v = e.target.value
                                      if (v === "") {
                                        setEditBudget("")
                                        return
                                      }
                                      const n = Number(v)
                                      if (!Number.isFinite(n) || n < 0) return
                                      setEditBudget(v)
                                    }}
                                    onBlur={() =>
                                      setEditBudget((prev) =>
                                        prev.trim() === "" ? prev : normalizeBudgetNumericString(prev),
                                      )
                                    }
                                    placeholder="월 예산 USD"
                                    disabled={saveEditLoading}
                                  />
                                </span>
                              ) : (
                                <span>
                                  {apiKey.alias}
                                  {keyPendingDeletion ? (
                                    <span className="ml-1.5 text-amber-700 dark:text-amber-500">(삭제 예정)</span>
                                  ) : null}
                                </span>
                              )}
                            </div>
                            <p className="text-xs text-muted-foreground">
                              월 예산:{" "}
                              {formatBudgetUsd(apiKey.monthlyBudgetUsd ?? undefined) ?? "— (미설정·기존 데이터)"}
                            </p>
                            {keyPendingDeletion && apiKey.permanentDeletionAt ? (
                              <p className="text-xs text-amber-800 dark:text-amber-300">
                                영구 삭제 예정: {formatCreatedAt(apiKey.permanentDeletionAt)}
                                {typeof apiKey.deletionGraceDays === "number"
                                  ? ` (${apiKey.deletionGraceDays}일 유예)`
                                  : ""}
                              </p>
                            ) : null}
                          </div>
                          <div className="flex shrink-0 flex-col items-stretch gap-2 sm:items-end">
                            <div className="text-xs text-muted-foreground tabular-nums sm:text-right">
                              {formatCreatedAt(apiKey.createdAt)}
                            </div>
                            <div className="flex flex-wrap gap-2">
                              {isEditing ? (
                                <>
                                  <button
                                    type="button"
                                    className="rounded-md border border-border bg-background px-3 py-1.5 text-xs font-medium hover:bg-muted disabled:opacity-50"
                                    disabled={saveEditLoading}
                                    onClick={() => void saveEditKey()}
                                  >
                                    {saveEditLoading ? "저장 중…" : "저장"}
                                  </button>
                                  <button
                                    type="button"
                                    className="rounded-md border border-border bg-background px-3 py-1.5 text-xs font-medium hover:bg-muted disabled:opacity-50"
                                    disabled={saveEditLoading}
                                    onClick={cancelEditKey}
                                  >
                                    취소
                                  </button>
                                </>
                              ) : (
                                <button
                                  type="button"
                                  className="rounded-md border border-border bg-background px-3 py-1.5 text-xs font-medium hover:bg-muted disabled:opacity-50"
                                  disabled={keyPendingDeletion || deleting || canceling || saveEditLoading}
                                  onClick={() => startEditKey(team.id, apiKey)}
                                >
                                  수정
                                </button>
                              )}
                              {!isEditing ? (
                                keyPendingDeletion ? (
                                  isOwner ? (
                                    <button
                                      type="button"
                                      className="rounded-md border border-border bg-background px-3 py-1.5 text-xs font-medium hover:bg-muted disabled:opacity-50"
                                      disabled={canceling || deleting}
                                      onClick={() => void cancelTeamKeyDeletion(team.id, apiKey.id)}
                                    >
                                      {canceling ? "처리 중…" : "삭제 취소"}
                                    </button>
                                  ) : null
                                ) : (
                                  isOwner ? (
                                    <button
                                      type="button"
                                      className="rounded-md border border-destructive/40 bg-background px-3 py-1.5 text-xs font-medium text-destructive hover:bg-destructive/10 disabled:opacity-50"
                                      disabled={deleting}
                                      onClick={() => void deleteTeamKey(team.id, apiKey.id)}
                                    >
                                      {deleting ? "처리 중…" : "삭제"}
                                    </button>
                                  ) : null
                                )
                              ) : null}
                            </div>
                          </div>
                        </li>
                      )
                    })}
                  </ul>
                ) : (
                  <p className="rounded-md border border-dashed border-border bg-background px-4 py-4 text-center text-xs text-muted-foreground">
                    등록된 팀 API Key가 없습니다. 아래에서 추가할 수 있습니다.
                  </p>
                )}
              </div>

              {isOwner ? (
              <div className="space-y-3 rounded-md border border-border bg-muted/10 p-3">
                <div className="space-y-1">
                  <p className="text-sm font-semibold">팀 API Key 등록</p>
                  <p className="text-xs text-muted-foreground">제출 후 입력한 키 값은 보안을 위해 비웁니다.</p>
                </div>
                <div className="grid gap-2">
                  <select
                    className="h-9 rounded-md border border-input bg-background px-3 text-sm"
                    value={apiKeyProviderByTeamId[team.id] ?? "OPENAI"}
                    onChange={(e) => {
                      const p = e.target.value as TeamKeyProvider
                      setApiKeyProviderByTeamId((prev) => ({ ...prev, [team.id]: p }))
                      if (!apiKeyAliasTouchedByTeamId[team.id]) {
                        setApiKeyAliasByTeamId((prev) => ({ ...prev, [team.id]: defaultAlias(p) }))
                      }
                    }}
                    disabled={apiKeyRegisterLoadingTeamId === team.id}
                  >
                    <option value="OPENAI">OPENAI</option>
                    <option value="GEMINI">GEMINI</option>
                    <option value="CLAUDE">CLAUDE</option>
                  </select>
                  <input
                    className="h-9 rounded-md border border-input bg-background px-3 text-sm"
                    value={apiKeyAliasByTeamId[team.id] ?? ""}
                    onChange={(e) => {
                      setApiKeyAliasTouchedByTeamId((prev) => ({ ...prev, [team.id]: true }))
                      setApiKeyAliasByTeamId((prev) => ({ ...prev, [team.id]: e.target.value }))
                    }}
                    placeholder="별칭"
                    autoComplete="off"
                    disabled={apiKeyRegisterLoadingTeamId === team.id}
                  />
                  <div className="flex gap-1">
                    <input
                      type={apiKeyRevealByTeamId[team.id] ? "text" : "password"}
                      className="h-9 min-w-0 flex-1 rounded-md border border-input bg-background px-3 text-sm"
                      value={apiKeyValueByTeamId[team.id] ?? ""}
                      onChange={(e) => setApiKeyValueByTeamId((prev) => ({ ...prev, [team.id]: e.target.value }))}
                      placeholder="API Key 값"
                      autoComplete="new-password"
                      disabled={apiKeyRegisterLoadingTeamId === team.id}
                    />
                    <button
                      type="button"
                      className="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-md border border-input bg-background text-muted-foreground hover:bg-muted disabled:opacity-50"
                      aria-label={apiKeyRevealByTeamId[team.id] ? "API Key 숨기기" : "API Key 보기"}
                      disabled={apiKeyRegisterLoadingTeamId === team.id}
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
                    className="h-9 w-full rounded-md border border-input bg-background px-3 text-sm"
                    inputMode="decimal"
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
                    placeholder="월 예산 USD"
                    autoComplete="off"
                    disabled={apiKeyRegisterLoadingTeamId === team.id}
                  />
                  {apiKeyRegisterMessageByTeamId[team.id] ? (
                    <p
                      className={
                        apiKeyRegisterMessageByTeamId[team.id]?.kind === "success"
                          ? "text-xs text-emerald-600"
                          : "text-xs text-destructive"
                      }
                    >
                      {apiKeyRegisterMessageByTeamId[team.id]?.text}
                    </p>
                  ) : null}
                  <button
                    type="button"
                    className="inline-flex h-9 items-center rounded-md border border-border bg-background px-3 text-sm font-medium hover:bg-muted disabled:opacity-60"
                    disabled={apiKeyRegisterLoadingTeamId === team.id}
                    onClick={() => void registerTeamApiKey(team.id)}
                  >
                    {apiKeyRegisterLoadingTeamId === team.id ? "등록 중…" : "등록"}
                  </button>
                </div>
              </div>
              ) : (
                <p className="text-xs text-muted-foreground">팀장만 팀 API Key 등록/삭제를 할 수 있습니다.</p>
              )}
              {isOwner ? (
              <div className="rounded-md border border-destructive/30 bg-destructive/5 p-3">
                <p className="text-xs text-muted-foreground">
                  팀 삭제는 팀장만 가능하며, 팀 API Key를 모두 삭제한 뒤에만 진행됩니다.
                </p>
                <button
                  type="button"
                  className="mt-2 inline-flex h-9 items-center rounded-md border border-destructive/40 bg-background px-3 text-xs font-medium text-destructive hover:bg-destructive/10 disabled:opacity-50"
                  disabled={deleteTeamLoadingId === team.id}
                  onClick={() => void deleteTeam(team.id, team.name)}
                >
                  {deleteTeamLoadingId === team.id ? "팀 삭제 중…" : "팀 삭제"}
                </button>
              </div>
              ) : null}
              </div>
              ) : null}
            </li>
            )
          })}
        </ul>
      ) : null}
    </div>
  )
}
