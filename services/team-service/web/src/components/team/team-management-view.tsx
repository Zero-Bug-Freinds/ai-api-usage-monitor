"use client"

import * as React from "react"
import { ChevronRight, Eye, EyeOff, Minus, Plus, Search } from "lucide-react"
import { Checkbox, Label } from "@ai-usage/ui"

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
  monthlyBudgetUsd: number | null
  createdAt: string
  deletionRequestedAt?: string | null
  permanentDeletionAt?: string | null
  deletionGraceDays?: number | null
}

type _TeamKeyProvider = "OPENAI" | "GEMINI" | "CLAUDE"

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

function formatBudgetUsd(value: number | null | undefined) {
  if (value === null || value === undefined) return null
  return new Intl.NumberFormat("ko-KR", {
    style: "currency",
    currency: "USD",
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  }).format(value)
}

function formatDeletionDeadline(iso: string) {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleString("ko-KR", { timeZone: "Asia/Seoul" })
}

const BUDGET_STEP = 0.01
const DEFAULT_DELETION_GRACE_DAYS = 7
const MIN_DELETION_GRACE_DAYS = 0
const MAX_DELETION_GRACE_DAYS = 365
const GRACE_PERIOD_DELETION_HINT =
  "유예 기간은 0~365일 사이의 정수로 입력해 주세요. (0 입력 시 즉시 삭제)"

function parseTeamApiKeyDeletionGraceInput(raw: string):
  | { valid: true; graceDays: number; immediate: boolean }
  | { valid: false } {
  const trimmed = raw.trim()
  if (trimmed === "") {
    return { valid: true, graceDays: DEFAULT_DELETION_GRACE_DAYS, immediate: false }
  }
  const n = Number.parseInt(trimmed, 10)
  if (!Number.isFinite(n) || n < MIN_DELETION_GRACE_DAYS || n > MAX_DELETION_GRACE_DAYS) {
    return { valid: false }
  }
  return { valid: true, graceDays: n, immediate: n === 0 }
}

const TEAM_WEB_BASE_PATH = "/teams"

type InviteeFieldRow = { id: string; value: string }

function newInviteeRow(): InviteeFieldRow {
  return { id: crypto.randomUUID(), value: "" }
}

function teamApiPath(path: string): string {
  const normalized = path.startsWith("/") ? path : `/${path}`
  return `${TEAM_WEB_BASE_PATH}${normalized}`
}

function useDebounce<T>(value: T, delayMs: number): T {
  const [debouncedValue, setDebouncedValue] = React.useState(value)

  React.useEffect(() => {
    const timeoutId = window.setTimeout(() => setDebouncedValue(value), delayMs)
    return () => window.clearTimeout(timeoutId)
  }, [value, delayMs])

  return debouncedValue
}

function normalizeBudgetNumericString(raw: string): string {
  const t = raw.trim()
  if (t === "") return ""
  const n = Number(t)
  if (!Number.isFinite(n) || n < 0) return ""
  const rounded = Math.round(n / BUDGET_STEP) * BUDGET_STEP
  return Number(rounded.toFixed(2)).toString()
}

async function requestApi(path: string, init?: RequestInit) {
  const res = await fetch(teamApiPath(path), {
    credentials: "include",
    cache: "no-store",
    headers: { Accept: "application/json", ...(init?.headers ?? {}) },
    ...init,
  })
  const json = (await res.json().catch(() => null)) as unknown
  return { res, body: asApiResponse(json) }
}

export function TeamManagementView() {
  const [loading, setLoading] = React.useState(true)
  const [error, setError] = React.useState<string | null>(null)
  const [teams, setTeams] = React.useState<TeamSummary[]>([])
  const [keyword, setKeyword] = React.useState("")
  const debouncedKeyword = useDebounce(keyword, 500)
  const [isSearching, setIsSearching] = React.useState(false)
  const [teamMemberIdsByTeamId, setTeamMemberIdsByTeamId] = React.useState<Record<string, string[]>>({})
  const [isTeamOwnerByTeamId, setIsTeamOwnerByTeamId] = React.useState<Record<string, boolean>>({})
  const [showCreateForm, setShowCreateForm] = React.useState(false)
  const [teamName, setTeamName] = React.useState("")
  const [inviteesOnCreate, setInviteesOnCreate] = React.useState<InviteeFieldRow[]>(() => [newInviteeRow()])
  const [createLoading, setCreateLoading] = React.useState(false)
  const [inviteInputsByTeamId, setInviteInputsByTeamId] = React.useState<Record<string, InviteeFieldRow[]>>({})
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
  const [deleteLoadingKey, setDeleteLoadingKey] = React.useState<string | null>(null)
  const [teamApiKeyDeletionModal, setTeamApiKeyDeletionModal] = React.useState<{
    teamId: string
    keyId: number
    graceDaysInput: string
    retainLogs: boolean
  } | null>(null)
  const [teamApiKeyDeletionGraceError, setTeamApiKeyDeletionGraceError] = React.useState<string | null>(null)
  const [cancelDeleteLoadingKey, setCancelDeleteLoadingKey] = React.useState<string | null>(null)
  const [removeMemberLoadingKey, setRemoveMemberLoadingKey] = React.useState<string | null>(null)
  const [deleteTeamLoadingId, setDeleteTeamLoadingId] = React.useState<string | null>(null)
  const [selectedTeamId, setSelectedTeamId] = React.useState<string | null>(null)
  const [activeTab, setActiveTab] = React.useState<"dashboard" | "members" | "settings">("dashboard")
  const latestLoadSeqRef = React.useRef(0)

  const loadTeams = React.useCallback(async (keywordParam?: string) => {
    const requestSeq = latestLoadSeqRef.current + 1
    latestLoadSeqRef.current = requestSeq
    setLoading(true)
    setIsSearching(true)
    setError(null)
    const q = new URLSearchParams({
      page: "0",
      size: "10",
    })
    const trimmedKeyword = (keywordParam ?? "").trim()
    if (trimmedKeyword !== "") {
      q.set("keyword", trimmedKeyword)
    }
    try {
      const { res, body } = await requestApi(`/api/team/v1/teams?${q.toString()}`, { method: "GET" })
      if (requestSeq !== latestLoadSeqRef.current) {
        return
      }
      const pageData = body?.data
      const content =
        pageData && typeof pageData === "object" && Array.isArray((pageData as { content?: unknown[] }).content)
          ? (pageData as { content: unknown[] }).content
          : null
      if (!res.ok || !body?.success || content === null) {
        setError(body?.message ?? "팀 목록을 불러오지 못했습니다")
        setTeams([])
        return
      }
      setTeams(
        content
          .map((item) => normalizeTeamSummary(item))
          .filter((item): item is TeamSummary => item !== null)
      )
    } catch {
      if (requestSeq !== latestLoadSeqRef.current) {
        return
      }
      setError("팀 목록을 불러오지 못했습니다")
      setTeams([])
    } finally {
      if (requestSeq === latestLoadSeqRef.current) {
        setLoading(false)
        setIsSearching(false)
      }
    }
  }, [])

  const loadTeamMembers = React.useCallback(async (teamId: string) => {
    try {
      const { res, body } = await requestApi(`/api/team/v1/teams/${encodeURIComponent(teamId)}/members`, { method: "GET" })
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

  const loadTeamOwnerFlag = React.useCallback(async (teamId: string) => {
    try {
      const { res, body } = await requestApi(`/api/team/v1/teams/${encodeURIComponent(teamId)}/owner`, { method: "GET" })
      if (!res.ok || !body?.success || typeof body.data !== "boolean") {
        setIsTeamOwnerByTeamId((prev) => ({ ...prev, [teamId]: false }))
        return
      }
      setIsTeamOwnerByTeamId((prev) => ({ ...prev, [teamId]: body.data as boolean }))
    } catch {
      setIsTeamOwnerByTeamId((prev) => ({ ...prev, [teamId]: false }))
    }
  }, [])

  const loadTeamApiKeys = React.useCallback(async (teamId: string) => {
    try {
      const { res, body } = await requestApi(`/api/team/v1/teams/${encodeURIComponent(teamId)}/api-keys`, { method: "GET" })
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
    void loadTeams(debouncedKeyword)
  }, [loadTeams, debouncedKeyword])

  React.useEffect(() => {
    if (teams.length === 0) {
      setTeamMemberIdsByTeamId({})
      setIsTeamOwnerByTeamId({})
      setTeamApiKeysByTeamId({})
      setSelectedTeamId(null)
    }
  }, [teams.length])

  React.useEffect(() => {
    if (selectedTeamId && !teams.some((t) => t.id === selectedTeamId)) {
      setSelectedTeamId(null)
    }
  }, [teams, selectedTeamId])

  React.useEffect(() => {
    setActiveTab("dashboard")
  }, [selectedTeamId])

  React.useEffect(() => {
    if (!selectedTeamId) return
    if (!teams.some((t) => t.id === selectedTeamId)) return
    void loadTeamOwnerFlag(selectedTeamId)
    void loadTeamMembers(selectedTeamId)
    void loadTeamApiKeys(selectedTeamId)
  }, [selectedTeamId, teams, loadTeamApiKeys, loadTeamMembers, loadTeamOwnerFlag])

  async function createTeam(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    if (createLoading) return
    const name = teamName.trim()
    const inviteeIds = inviteesOnCreate.map((r) => r.value.trim()).filter((v) => v !== "")
    if (!name) {
      setMessage({ kind: "error", text: "팀 이름은 필수입니다" })
      return
    }
    setCreateLoading(true)
    setMessage(null)
    try {
      const { res, body } = await requestApi("/api/team/v1/teams", {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({ name }),
      })
      if (!res.ok || !body?.success) {
        setMessage({ kind: "error", text: body?.message ?? "팀 생성에 실패했습니다" })
        return
      }

      const createdTeam =
        body.data && typeof body.data === "object" ? (body.data as { id?: unknown }) : null
      const createdTeamId =
        typeof createdTeam?.id === "string" || typeof createdTeam?.id === "number" ? String(createdTeam.id) : null

      if (inviteeIds.length > 0 && createdTeamId) {
        let okCount = 0
        let failCount = 0
        for (const uid of inviteeIds) {
          const inviteRes = await requestApi(`/api/team/v1/teams/${encodeURIComponent(createdTeamId)}/members`, {
            method: "POST",
            headers: { "Content-Type": "application/json", Accept: "application/json" },
            body: JSON.stringify({ userId: uid }),
          })
          if (inviteRes.res.ok && inviteRes.body?.success) {
            okCount += 1
          } else {
            failCount += 1
          }
        }
        if (failCount === 0) {
          setMessage({
            kind: "success",
            text:
              okCount === 1
                ? "팀이 생성되었고 팀원 초대를 보냈습니다"
                : `팀이 생성되었고 팀원 ${okCount}명에게 초대를 보냈습니다`,
          })
        } else if (okCount === 0) {
          setMessage({ kind: "error", text: "팀은 생성되었지만 팀원 초대에 실패했습니다" })
        } else {
          setMessage({
            kind: "error",
            text: `팀은 생성되었습니다. 초대 성공 ${okCount}명, 실패 ${failCount}명입니다`,
          })
        }
      } else {
        setMessage({ kind: "success", text: "팀이 생성되었습니다" })
      }

      setTeamName("")
      setInviteesOnCreate([newInviteeRow()])
      setShowCreateForm(false)
      await loadTeams(debouncedKeyword)
    } catch {
      setMessage({ kind: "error", text: "팀 생성에 실패했습니다" })
    } finally {
      setCreateLoading(false)
    }
  }

  function openCreateForm() {
    setMessage(null)
    setShowCreateForm(true)
  }

  function closeCreateForm() {
    setShowCreateForm(false)
    setTeamName("")
    setInviteesOnCreate([newInviteeRow()])
  }

  async function invite(teamId: string) {
    if (inviteLoadingTeamId) return
    const rows = inviteInputsByTeamId[teamId] ?? [newInviteeRow()]
    const userIds = rows.map((r) => r.value.trim()).filter((v) => v !== "")
    if (userIds.length === 0) {
      setMessage({ kind: "error", text: "초대할 사용자 이메일 또는 아이디를 입력해 주세요" })
      return
    }
    setInviteLoadingTeamId(teamId)
    setMessage(null)
    try {
      let okCount = 0
      let failCount = 0
      let lastError: string | null = null
      for (const userId of userIds) {
        const { res, body } = await requestApi(`/api/team/v1/teams/${encodeURIComponent(teamId)}/members`, {
          method: "POST",
          headers: { "Content-Type": "application/json", Accept: "application/json" },
          body: JSON.stringify({ userId }),
        })
        if (res.ok && body?.success) {
          okCount += 1
        } else {
          failCount += 1
          lastError = body?.message ?? null
        }
      }
      if (failCount === 0) {
        setMessage({
          kind: "success",
          text: okCount === 1 ? "초대를 보냈습니다" : `초대를 ${okCount}명에게 보냈습니다`,
        })
        setInviteInputsByTeamId((prev) => ({ ...prev, [teamId]: [newInviteeRow()] }))
        await loadTeamMembers(teamId)
      } else if (okCount === 0) {
        setMessage({ kind: "error", text: lastError ?? "초대에 실패했습니다" })
      } else {
        setMessage({
          kind: "error",
          text: lastError
            ? `일부만 초대되었습니다 (성공 ${okCount}명, 실패 ${failCount}명). ${lastError}`
            : `일부만 초대되었습니다 (성공 ${okCount}명, 실패 ${failCount}명)`,
        })
        setInviteInputsByTeamId((prev) => ({ ...prev, [teamId]: [newInviteeRow()] }))
        await loadTeamMembers(teamId)
      }
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
      const { res, body: bodyRes } = await requestApi(
        `/api/team/v1/teams/${encodeURIComponent(teamId)}/api-keys/${encodeURIComponent(String(keyId))}`,
        {
          method: "PUT",
          headers: { "Content-Type": "application/json", Accept: "application/json" },
          body: JSON.stringify(body),
        },
      )
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
      const { res, body } = await requestApi(`/api/team/v1/teams/${encodeURIComponent(teamId)}/api-keys`, {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({ provider, alias, externalKey, monthlyBudgetUsd }),
      })
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

  function openTeamApiKeyDeletionModal(teamId: string, keyId: number) {
    setMessage(null)
    setTeamApiKeyDeletionGraceError(null)
    setTeamApiKeyDeletionModal({
      teamId,
      keyId,
      graceDaysInput: String(DEFAULT_DELETION_GRACE_DAYS),
      retainLogs: true,
    })
  }

  function closeTeamApiKeyDeletionModal() {
    setTeamApiKeyDeletionModal(null)
    setTeamApiKeyDeletionGraceError(null)
  }

  async function confirmTeamApiKeyDeletion() {
    if (!teamApiKeyDeletionModal) return
    const parsed = parseTeamApiKeyDeletionGraceInput(teamApiKeyDeletionModal.graceDaysInput)
    if (!parsed.valid) {
      setTeamApiKeyDeletionGraceError(GRACE_PERIOD_DELETION_HINT)
      return
    }
    const { teamId, keyId, retainLogs } = teamApiKeyDeletionModal
    const { graceDays } = parsed
    const loadingKey = `${teamId}:${keyId}`
    setTeamApiKeyDeletionGraceError(null)
    setDeleteLoadingKey(loadingKey)
    setMessage(null)
    try {
      const q = new URLSearchParams({ gracePeriodDays: String(graceDays) })
      q.set("retainLogs", String(retainLogs))
      const { res, body } = await requestApi(
        `/api/team/v1/teams/${encodeURIComponent(teamId)}/api-keys/${encodeURIComponent(String(keyId))}?${q.toString()}`,
        { method: "DELETE" },
      )
      if (!res.ok || !body?.success) {
        setMessage({ kind: "error", text: body?.message ?? "팀 API Key 삭제에 실패했습니다" })
        return
      }
      closeTeamApiKeyDeletionModal()
      setMessage({ kind: "success", text: "팀 API Key 삭제 요청이 처리되었습니다" })
      await loadTeamApiKeys(teamId)
    } catch {
      setMessage({ kind: "error", text: "팀 API Key 삭제에 실패했습니다" })
    } finally {
      setDeleteLoadingKey(null)
    }
  }

  async function cancelTeamApiKeyDeletion(teamId: string, keyId: number) {
    const loadingKey = `${teamId}:${keyId}`
    setCancelDeleteLoadingKey(loadingKey)
    setMessage(null)
    try {
      const { res, body } = await requestApi(
        `/api/team/v1/teams/${encodeURIComponent(teamId)}/api-keys/${encodeURIComponent(String(keyId))}/deletion/cancel`,
        { method: "POST" },
      )
      if (!res.ok || !body?.success) {
        setMessage({ kind: "error", text: body?.message ?? "삭제 예약 취소에 실패했습니다" })
        return
      }
      setMessage({ kind: "success", text: "삭제 예약이 취소되었습니다" })
      await loadTeamApiKeys(teamId)
    } catch {
      setMessage({ kind: "error", text: "삭제 예약 취소에 실패했습니다" })
    } finally {
      setCancelDeleteLoadingKey(null)
    }
  }

  async function removeTeamMember(teamId: string, memberId: string) {
    if (!window.confirm(`팀원 "${memberId}"를 삭제할까요?`)) return
    const loadingKey = `${teamId}:${memberId}`
    setRemoveMemberLoadingKey(loadingKey)
    setMessage(null)
    try {
      const { res, body } = await requestApi(
        `/api/team/v1/teams/${encodeURIComponent(teamId)}/members/${encodeURIComponent(memberId)}`,
        { method: "DELETE" },
      )
      if (!res.ok || !body?.success) {
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
    if (!window.confirm(`"${teamName}" 팀을 삭제할까요?\n(팀 API 키를 먼저 모두 삭제해야 합니다)`)) return
    setDeleteTeamLoadingId(teamId)
    setMessage(null)
    try {
      const { res, body } = await requestApi(`/api/team/v1/teams/${encodeURIComponent(teamId)}`, { method: "DELETE" })
      if (!res.ok || !body?.success) {
        setMessage({ kind: "error", text: body?.message ?? "팀 삭제에 실패했습니다" })
        return
      }
      setMessage({ kind: "success", text: "팀을 삭제했습니다" })
      await loadTeams(debouncedKeyword)
      cancelEditTeamApiKey()
    } catch {
      setMessage({ kind: "error", text: "팀 삭제에 실패했습니다" })
    } finally {
      setDeleteTeamLoadingId(null)
    }
  }

  const selectedTeam = selectedTeamId ? teams.find((team) => team.id === selectedTeamId) ?? null : null

  const teamDeletionModalParsed = teamApiKeyDeletionModal
    ? parseTeamApiKeyDeletionGraceInput(teamApiKeyDeletionModal.graceDaysInput)
    : null

  return (
    <main className="flex min-h-screen overflow-hidden bg-white">
      {teamApiKeyDeletionModal ? (
        <div
          className="fixed inset-0 z-[100] flex items-center justify-center bg-black/40 px-4 py-6"
          role="presentation"
          onMouseDown={(e) => {
            if (e.target === e.currentTarget) closeTeamApiKeyDeletionModal()
          }}
        >
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="team-api-key-delete-title"
            className="w-full max-w-md rounded-lg border border-zinc-200 bg-white p-5 shadow-lg"
            onMouseDown={(e) => e.stopPropagation()}
          >
            <h3 id="team-api-key-delete-title" className="text-sm font-semibold text-zinc-900">
              팀 API Key 삭제
            </h3>
            <p className="mt-2 text-sm text-zinc-600">
              {teamDeletionModalParsed?.valid && teamDeletionModalParsed.immediate
                ? null
                : "유예 기간이 지나면 키가 영구 삭제됩니다. 유예 중에는 삭제 취소를 할 수 있습니다."}
            </p>
            {teamDeletionModalParsed?.valid && teamDeletionModalParsed.immediate ? (
              <p className="mt-2 text-sm font-medium text-red-600">이 API Key는 즉시 영구 삭제됩니다.</p>
            ) : null}
            <div className="mt-4 space-y-1.5">
              <label className="text-xs font-medium text-zinc-800" htmlFor="team-api-key-delete-grace">
                유예 기간(일)
              </label>
              <input
                id="team-api-key-delete-grace"
                type="text"
                inputMode="numeric"
                className="h-10 w-full rounded-md border border-zinc-300 bg-white px-3 text-sm tabular-nums"
                value={teamApiKeyDeletionModal.graceDaysInput}
                onChange={(e) => {
                  setTeamApiKeyDeletionGraceError(null)
                  setTeamApiKeyDeletionModal((prev) =>
                    prev ? { ...prev, graceDaysInput: e.target.value } : prev
                  )
                }}
                autoComplete="off"
                disabled={deleteLoadingKey === `${teamApiKeyDeletionModal.teamId}:${teamApiKeyDeletionModal.keyId}`}
              />
              <p className="text-xs text-zinc-500">{GRACE_PERIOD_DELETION_HINT}</p>
              {teamApiKeyDeletionGraceError ? (
                <p className="text-xs text-red-600">{teamApiKeyDeletionGraceError}</p>
              ) : null}
            </div>
            <div className="mt-4 flex gap-3 rounded-md border border-zinc-200 bg-zinc-50 p-3">
              <Checkbox
                id="team-api-key-retain-logs"
                checked={teamApiKeyDeletionModal.retainLogs}
                onCheckedChange={(v) => {
                  setTeamApiKeyDeletionGraceError(null)
                  setTeamApiKeyDeletionModal((prev) =>
                    prev ? { ...prev, retainLogs: v === true } : prev
                  )
                }}
                disabled={deleteLoadingKey === `${teamApiKeyDeletionModal.teamId}:${teamApiKeyDeletionModal.keyId}`}
                className="mt-0.5"
              />
              <div className="min-w-0 space-y-1">
                <Label htmlFor="team-api-key-retain-logs" className="text-xs font-medium leading-snug text-zinc-800">
                  팀 API 사용 기록 보존
                </Label>
                <p className="text-xs leading-relaxed text-zinc-600">
                  체크 해제 시, API Key가 최종적으로 완전히 삭제되는 시점에 이 키로 발생한 모든 팀 호출 로그와 통계도 함께 영구적으로 삭제됩니다.
                </p>
              </div>
            </div>
            <div className="mt-5 flex flex-wrap justify-end gap-2">
              <button
                type="button"
                className="h-9 rounded-md border border-zinc-300 bg-white px-3 text-xs font-medium text-zinc-800 hover:bg-zinc-50 disabled:opacity-50"
                disabled={
                  deleteLoadingKey === `${teamApiKeyDeletionModal.teamId}:${teamApiKeyDeletionModal.keyId}`
                }
                onClick={closeTeamApiKeyDeletionModal}
              >
                취소
              </button>
              <button
                type="button"
                className={
                  teamDeletionModalParsed?.valid && teamDeletionModalParsed.immediate
                    ? "h-9 rounded-md bg-red-600 px-3 text-xs font-medium text-white hover:bg-red-700 disabled:opacity-50"
                    : "h-9 rounded-md border border-red-300 bg-white px-3 text-xs font-medium text-red-600 hover:bg-red-50 disabled:opacity-50"
                }
                disabled={
                  !teamDeletionModalParsed?.valid ||
                  deleteLoadingKey === `${teamApiKeyDeletionModal.teamId}:${teamApiKeyDeletionModal.keyId}`
                }
                onClick={() => void confirmTeamApiKeyDeletion()}
              >
                {deleteLoadingKey === `${teamApiKeyDeletionModal.teamId}:${teamApiKeyDeletionModal.keyId}`
                  ? "처리 중…"
                  : teamDeletionModalParsed?.valid && teamDeletionModalParsed.immediate
                    ? "즉시 삭제"
                    : "삭제 예약"}
              </button>
            </div>
          </div>
        </div>
      ) : null}
      <aside className="w-72 shrink-0 border-r border-zinc-200 bg-gray-50">
        <div className="flex h-full flex-col">
          <div className="border-b border-zinc-200 px-4 py-4">
            <div className="flex items-center justify-between gap-2">
              <h2 className="text-base font-semibold text-zinc-900">팀 목록</h2>
              <button
                type="button"
                className="h-8 rounded-md border border-zinc-300 bg-white px-2.5 text-xs font-medium text-zinc-700 hover:bg-zinc-100 disabled:opacity-60"
                onClick={openCreateForm}
                disabled={createLoading}
              >
                + 새 팀
              </button>
            </div>
            <p className="mt-1 text-xs text-zinc-500">팀을 선택하면 우측에서 상세 설정을 수정할 수 있습니다.</p>
            <div className="relative mt-3">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-zinc-400" aria-hidden />
              <input
                id="team-search"
                className="h-10 w-full rounded-md border border-zinc-300 bg-white pl-9 pr-3 text-sm outline-none transition focus:border-zinc-400 focus:ring-2 focus:ring-zinc-200"
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                placeholder="팀 이름 검색"
                autoComplete="off"
              />
            </div>
            {showCreateForm ? (
              <form className="mt-3 space-y-3 rounded-md border border-zinc-200 bg-white p-3" onSubmit={createTeam}>
                <div className="space-y-1">
                  <label className="text-xs font-medium">팀 이름 (필수)</label>
                  <input
                    className="h-9 w-full rounded-md border border-zinc-300 bg-white px-3 text-sm"
                    value={teamName}
                    onChange={(e) => setTeamName(e.target.value)}
                    placeholder="예: 플랫폼팀"
                    autoComplete="off"
                    disabled={createLoading}
                    required
                  />
                </div>
                <div className="space-y-2">
                  <label className="text-xs font-medium">팀원 초대 (선택)</label>
                  <div className="space-y-2">
                    {inviteesOnCreate.map((row) => (
                      <div key={row.id} className="flex gap-2">
                        <input
                          className="h-9 min-w-0 flex-1 rounded-md border border-zinc-300 bg-white px-3 text-sm"
                          value={row.value}
                          onChange={(e) => {
                            const v = e.target.value
                            setInviteesOnCreate((prev) =>
                              prev.map((r) => (r.id === row.id ? { ...r, value: v } : r)),
                            )
                          }}
                          placeholder="초대할 사용자 이메일 또는 아이디"
                          autoComplete="off"
                          disabled={createLoading}
                        />
                        {inviteesOnCreate.length > 1 ? (
                          <button
                            type="button"
                            className="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-md border border-zinc-300 bg-white text-zinc-700 hover:bg-zinc-50 disabled:opacity-50"
                            aria-label="이 초대 행 삭제"
                            disabled={createLoading}
                            onClick={() =>
                              setInviteesOnCreate((prev) => prev.filter((r) => r.id !== row.id))
                            }
                          >
                            <Minus className="h-4 w-4" aria-hidden />
                          </button>
                        ) : null}
                      </div>
                    ))}
                    <button
                      type="button"
                      className="inline-flex h-9 w-full items-center justify-center gap-1.5 rounded-md border border-dashed border-zinc-300 bg-zinc-50 px-3 text-xs text-zinc-700 hover:bg-zinc-100 disabled:opacity-50"
                      disabled={createLoading}
                      onClick={() => setInviteesOnCreate((prev) => [...prev, newInviteeRow()])}
                    >
                      <Plus className="h-4 w-4" aria-hidden />
                      초대 대상 추가
                    </button>
                  </div>
                </div>
                <div className="flex gap-2">
                  <button
                    type="submit"
                    className="h-9 rounded-md bg-black px-3 text-xs font-medium text-white disabled:opacity-60"
                    disabled={createLoading}
                  >
                    {createLoading ? "생성 중…" : "생성"}
                  </button>
                  <button
                    type="button"
                    className="h-9 rounded-md border border-zinc-300 bg-white px-3 text-xs font-medium"
                    onClick={closeCreateForm}
                    disabled={createLoading}
                  >
                    취소
                  </button>
                </div>
              </form>
            ) : null}
          </div>

          <div className="min-h-0 flex-1 overflow-y-auto p-3">
            {loading ? <p className="px-2 py-3 text-sm text-zinc-500">{isSearching ? "검색 중..." : "불러오는 중…"}</p> : null}
            {error && !loading ? <p className="px-2 py-3 text-sm text-red-600">{error}</p> : null}
            {!loading && !error && teams.length === 0 ? (
              <p className="px-2 py-3 text-sm text-zinc-500">{debouncedKeyword.trim() ? "검색된 팀이 없습니다" : "참여 중인 팀이 없습니다."}</p>
            ) : null}
            {!loading && teams.length > 0 ? (
              <ul className="space-y-2">
                {teams.map((team) => {
                  const isSelected = selectedTeamId === team.id
                  return (
                    <li key={team.id}>
                      <button
                        type="button"
                        className={`w-full rounded-lg border px-3 py-2 text-left transition ${
                          isSelected
                            ? "border-zinc-900 bg-white shadow-sm"
                            : "border-zinc-200 bg-white hover:border-zinc-300 hover:bg-zinc-50"
                        }`}
                        onClick={() => {
                          cancelEditTeamApiKey()
                          setSelectedTeamId(team.id)
                          setInviteInputsByTeamId((prev) =>
                            prev[team.id] !== undefined ? prev : { ...prev, [team.id]: [newInviteeRow()] },
                          )
                        }}
                      >
                        <div className="flex items-center gap-2">
                          {isSelected ? (
                            <ChevronRight className="h-4 w-4 shrink-0 text-zinc-700" aria-hidden />
                          ) : (
                            <ChevronRight className="h-4 w-4 shrink-0 text-zinc-400" aria-hidden />
                          )}
                          <span className="truncate text-sm font-medium text-zinc-900">{team.name}</span>
                        </div>
                      </button>
                    </li>
                  )
                })}
              </ul>
            ) : null}
          </div>
        </div>
      </aside>

      <section className="flex-1 overflow-y-auto bg-white px-6 py-5">

        {message ? (
          <p className={`mt-4 text-sm ${message.kind === "success" ? "text-emerald-600" : "text-red-600"}`}>{message.text}</p>
        ) : null}

        {!selectedTeam ? (
          <div className="mt-6 flex min-h-[360px] items-center justify-center rounded-lg border border-dashed border-zinc-300 bg-zinc-50">
            <p className="text-sm text-zinc-500">왼쪽에서 팀을 선택해 주세요.</p>
          </div>
        ) : (
          <div className="mt-6 space-y-4 rounded-lg border border-zinc-200 bg-zinc-50/50 p-4">
            <div className="flex items-center justify-between gap-3">
              <h2 className="text-lg font-semibold">{selectedTeam.name}</h2>
              <p className="text-xs text-zinc-500">멤버 {(teamMemberIdsByTeamId[selectedTeam.id] ?? []).length}명</p>
            </div>
            <div className="border-b border-zinc-200">
              <nav className="flex items-center gap-5">
                <button
                  type="button"
                  className={`pb-2 text-sm font-medium ${
                    activeTab === "dashboard"
                      ? "border-b-2 border-blue-500 text-blue-600"
                      : "border-b-2 border-transparent text-zinc-500 hover:text-zinc-700"
                  }`}
                  onClick={() => setActiveTab("dashboard")}
                >
                  대시보드
                </button>
                <button
                  type="button"
                  className={`pb-2 text-sm font-medium ${
                    activeTab === "members"
                      ? "border-b-2 border-blue-500 text-blue-600"
                      : "border-b-2 border-transparent text-zinc-500 hover:text-zinc-700"
                  }`}
                  onClick={() => setActiveTab("members")}
                >
                  멤버 관리
                </button>
                <button
                  type="button"
                  className={`pb-2 text-sm font-medium ${
                    activeTab === "settings"
                      ? "border-b-2 border-blue-500 text-blue-600"
                      : "border-b-2 border-transparent text-zinc-500 hover:text-zinc-700"
                  }`}
                  onClick={() => setActiveTab("settings")}
                >
                  API 및 설정
                </button>
              </nav>
            </div>

            {activeTab === "dashboard" ? (
              <div className="flex min-h-[320px] items-center justify-center rounded-lg border border-dashed border-zinc-300 bg-white p-6">
                <p className="text-sm text-zinc-500">여기에 사용량 차트와 대시보드가 들어갈 예정입니다.</p>
              </div>
            ) : null}

            {activeTab === "members" ? (
              <>
                <div>
                  {(teamMemberIdsByTeamId[selectedTeam.id] ?? []).length > 0 ? (
                    <ul className="space-y-1 text-xs text-zinc-600">
                      {(teamMemberIdsByTeamId[selectedTeam.id] ?? []).map((memberId) => (
                        <li key={`${selectedTeam.id}-${memberId}`} className="flex items-center justify-between gap-2">
                          <span className="truncate">{memberId}</span>
                          {isTeamOwnerByTeamId[selectedTeam.id] ? (
                            <button
                              type="button"
                              className="rounded border border-red-300 bg-white px-2 py-1 text-[11px] text-red-600 disabled:opacity-50"
                              disabled={removeMemberLoadingKey === `${selectedTeam.id}:${memberId}`}
                              onClick={() => void removeTeamMember(selectedTeam.id, memberId)}
                            >
                              {removeMemberLoadingKey === `${selectedTeam.id}:${memberId}` ? "삭제 중…" : "팀원 삭제"}
                            </button>
                          ) : null}
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <p className="mt-1 text-xs text-zinc-500">등록된 팀원이 없습니다.</p>
                  )}
                </div>

                <div className="space-y-2">
                  <p className="text-xs font-medium text-zinc-700">팀원 초대</p>
                  {(inviteInputsByTeamId[selectedTeam.id] ?? []).map((row) => (
                    <div key={row.id} className="flex gap-2">
                      <input
                        className="h-9 min-w-0 flex-1 rounded-md border border-zinc-300 bg-white px-3 text-sm"
                        value={row.value}
                        onChange={(e) => {
                          const v = e.target.value
                          setInviteInputsByTeamId((prev) => {
                            const list = prev[selectedTeam.id] ?? []
                            return {
                              ...prev,
                              [selectedTeam.id]: list.map((r) => (r.id === row.id ? { ...r, value: v } : r)),
                            }
                          })
                        }}
                        placeholder="초대할 사용자 이메일 또는 아이디"
                        autoComplete="off"
                        disabled={inviteLoadingTeamId === selectedTeam.id}
                      />
                      {(inviteInputsByTeamId[selectedTeam.id] ?? []).length > 1 ? (
                        <button
                          type="button"
                          className="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-md border border-zinc-300 bg-white text-zinc-700 hover:bg-zinc-50 disabled:opacity-50"
                          aria-label="이 초대 행 삭제"
                          disabled={inviteLoadingTeamId === selectedTeam.id}
                          onClick={() =>
                            setInviteInputsByTeamId((prev) => {
                              const list = prev[selectedTeam.id] ?? []
                              return {
                                ...prev,
                                [selectedTeam.id]: list.filter((r) => r.id !== row.id),
                              }
                            })
                          }
                        >
                          <Minus className="h-4 w-4" aria-hidden />
                        </button>
                      ) : null}
                    </div>
                  ))}
                  <button
                    type="button"
                    className="inline-flex h-9 w-full items-center justify-center gap-1.5 rounded-md border border-dashed border-zinc-300 bg-white px-3 text-xs text-zinc-700 hover:bg-zinc-50 disabled:opacity-50 sm:w-auto sm:justify-start"
                    disabled={inviteLoadingTeamId === selectedTeam.id}
                    onClick={() =>
                      setInviteInputsByTeamId((prev) => {
                        const list = prev[selectedTeam.id] ?? [newInviteeRow()]
                        return { ...prev, [selectedTeam.id]: [...list, newInviteeRow()] }
                      })
                    }
                  >
                    <Plus className="h-4 w-4" aria-hidden />
                    초대 대상 추가
                  </button>
                  <button
                    type="button"
                    className="h-9 w-full rounded-md border border-zinc-300 bg-white px-3 text-xs font-medium disabled:opacity-60 sm:w-auto"
                    disabled={inviteLoadingTeamId === selectedTeam.id}
                    onClick={() => void invite(selectedTeam.id)}
                  >
                    {inviteLoadingTeamId === selectedTeam.id ? "초대 중…" : "멤버 초대"}
                  </button>
                </div>
              </>
            ) : null}

            {activeTab === "settings" ? (
              <>
                <div className="space-y-2 rounded-md border border-zinc-200 bg-zinc-50 p-3">
              <p className="text-xs font-medium text-zinc-700">팀 API Key 등록</p>
              <div className="flex flex-col gap-2">
                <select
                  className="h-9 rounded-md border border-zinc-300 bg-white px-3 text-xs"
                  value={apiKeyProviderByTeamId[selectedTeam.id] ?? "OPENAI"}
                  onChange={(e) => setApiKeyProviderByTeamId((prev) => ({ ...prev, [selectedTeam.id]: e.target.value }))}
                  disabled={apiKeyLoadingTeamId === selectedTeam.id}
                >
                  <option value="OPENAI">OPENAI</option>
                  <option value="GEMINI">GEMINI</option>
                  <option value="CLAUDE">CLAUDE</option>
                </select>
                <input
                  className="h-9 rounded-md border border-zinc-300 bg-white px-3 text-xs"
                  value={apiKeyAliasByTeamId[selectedTeam.id] ?? ""}
                  onChange={(e) => setApiKeyAliasByTeamId((prev) => ({ ...prev, [selectedTeam.id]: e.target.value }))}
                  placeholder="API Key 별칭"
                  autoComplete="off"
                  disabled={apiKeyLoadingTeamId === selectedTeam.id}
                />
                <div className="flex gap-1">
                  <input
                    type={apiKeyRevealByTeamId[selectedTeam.id] ? "text" : "password"}
                    className="h-9 min-w-0 flex-1 rounded-md border border-zinc-300 bg-white px-3 text-xs"
                    value={apiKeyValueByTeamId[selectedTeam.id] ?? ""}
                    onChange={(e) => setApiKeyValueByTeamId((prev) => ({ ...prev, [selectedTeam.id]: e.target.value }))}
                    placeholder="API Key 값"
                    autoComplete="new-password"
                    disabled={apiKeyLoadingTeamId === selectedTeam.id}
                  />
                  <button
                    type="button"
                    className="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-md border border-zinc-300 bg-white text-zinc-600 hover:bg-zinc-50 disabled:opacity-50"
                    aria-label={apiKeyRevealByTeamId[selectedTeam.id] ? "API Key 숨기기" : "API Key 보기"}
                    disabled={apiKeyLoadingTeamId === selectedTeam.id}
                    onClick={() =>
                      setApiKeyRevealByTeamId((prev) => ({ ...prev, [selectedTeam.id]: !prev[selectedTeam.id] }))
                    }
                  >
                    {apiKeyRevealByTeamId[selectedTeam.id] ? (
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
                  value={apiKeyMonthlyBudgetByTeamId[selectedTeam.id] ?? ""}
                  onChange={(e) => {
                    const v = e.target.value
                    if (v === "") {
                      setApiKeyMonthlyBudgetByTeamId((prev) => ({ ...prev, [selectedTeam.id]: "" }))
                      return
                    }
                    const n = Number(v)
                    if (!Number.isFinite(n) || n < 0) return
                    setApiKeyMonthlyBudgetByTeamId((prev) => ({ ...prev, [selectedTeam.id]: v }))
                  }}
                  onBlur={() =>
                    setApiKeyMonthlyBudgetByTeamId((prev) => {
                      const cur = prev[selectedTeam.id] ?? ""
                      if (cur.trim() === "") return prev
                      const next = normalizeBudgetNumericString(cur)
                      if (next === cur) return prev
                      return { ...prev, [selectedTeam.id]: next }
                    })
                  }
                  placeholder="월 예산 USD"
                  inputMode="decimal"
                  autoComplete="off"
                  disabled={apiKeyLoadingTeamId === selectedTeam.id}
                />
                <button
                  type="button"
                  className="h-9 rounded-md border border-zinc-300 bg-white px-3 text-xs font-medium disabled:opacity-60"
                  disabled={apiKeyLoadingTeamId === selectedTeam.id}
                  onClick={() => void registerTeamApiKey(selectedTeam.id)}
                >
                  {apiKeyLoadingTeamId === selectedTeam.id ? "등록 중…" : "팀 API Key 등록"}
                </button>
              </div>

              {(teamApiKeysByTeamId[selectedTeam.id] ?? []).length > 0 ? (
                <ul className="space-y-2 text-xs text-zinc-700">
                  {(teamApiKeysByTeamId[selectedTeam.id] ?? []).map((apiKey) => {
                    const isEditing =
                      editingTeamApiKey?.teamId === selectedTeam.id && editingTeamApiKey?.keyId === apiKey.id
                    const updateKey = `${selectedTeam.id}:${apiKey.id}`
                    const updating = teamApiKeyUpdateLoading === updateKey
                    const keyPendingDeletion = Boolean(apiKey.deletionRequestedAt)
                    return (
                      <li
                        key={`${selectedTeam.id}-api-key-${apiKey.id}`}
                        className="rounded border border-zinc-200 bg-white px-2 py-2"
                      >
                        {!isEditing ? (
                          <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between sm:gap-3">
                            <div className="min-w-0 flex-1">
                              <p>
                                {apiKey.provider} · {apiKey.alias}
                                {keyPendingDeletion ? (
                                  <span className="ml-1.5 rounded bg-zinc-200 px-1.5 py-0.5 text-[10px] font-medium text-zinc-700">
                                    삭제 예정
                                  </span>
                                ) : null}
                              </p>
                              <p className="text-[11px] text-zinc-500">
                                월 예산:{" "}
                                {formatBudgetUsd(apiKey.monthlyBudgetUsd ?? undefined) ?? "— (기존 데이터)"}
                              </p>
                              {keyPendingDeletion && apiKey.permanentDeletionAt ? (
                                <p className="text-[11px] text-amber-800">
                                  영구 삭제 예정: {formatDeletionDeadline(apiKey.permanentDeletionAt)}
                                  {typeof apiKey.deletionGraceDays === "number"
                                    ? ` (${apiKey.deletionGraceDays}일 유예)`
                                    : ""}
                                </p>
                              ) : null}
                            </div>
                            <div className="flex shrink-0 items-center gap-2">
                              <button
                                type="button"
                                className="h-8 shrink-0 rounded-md border border-zinc-300 bg-white px-2 text-[11px] font-medium disabled:cursor-not-allowed disabled:opacity-50"
                                disabled={keyPendingDeletion}
                                title={keyPendingDeletion ? "삭제 예정인 키는 수정할 수 없습니다" : undefined}
                                onClick={() => startEditTeamApiKey(selectedTeam.id, apiKey)}
                              >
                                수정
                              </button>
                              {isTeamOwnerByTeamId[selectedTeam.id] ? (
                                keyPendingDeletion ? (
                                  <button
                                    type="button"
                                    className="h-8 shrink-0 rounded-md border border-zinc-300 bg-white px-2 text-[11px] font-medium disabled:opacity-50"
                                    disabled={cancelDeleteLoadingKey === `${selectedTeam.id}:${apiKey.id}`}
                                    onClick={() => void cancelTeamApiKeyDeletion(selectedTeam.id, apiKey.id)}
                                  >
                                    {cancelDeleteLoadingKey === `${selectedTeam.id}:${apiKey.id}` ? "처리 중…" : "삭제 취소"}
                                  </button>
                                ) : (
                                  <button
                                    type="button"
                                    className="h-8 shrink-0 rounded-md border border-red-300 bg-white px-2 text-[11px] font-medium text-red-600 disabled:opacity-50"
                                    disabled={deleteLoadingKey === `${selectedTeam.id}:${apiKey.id}`}
                                    onClick={() => openTeamApiKeyDeletionModal(selectedTeam.id, apiKey.id)}
                                  >
                                    {deleteLoadingKey === `${selectedTeam.id}:${apiKey.id}` ? "처리 중…" : "삭제"}
                                  </button>
                                )
                              ) : null}
                            </div>
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
                                onClick={() => void saveEditTeamApiKey(selectedTeam.id)}
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

                {isTeamOwnerByTeamId[selectedTeam.id] ? (
                  <div className="rounded-md border border-red-200 bg-red-50 p-3">
                    <p className="text-xs text-zinc-600">팀장은 팀 API 키를 모두 정리한 뒤 팀을 삭제할 수 있습니다.</p>
                    <button
                      type="button"
                      className="mt-2 rounded-md border border-red-300 bg-white px-3 py-1 text-xs font-medium text-red-600 disabled:opacity-50"
                      disabled={deleteTeamLoadingId === selectedTeam.id}
                      onClick={() => void deleteTeam(selectedTeam.id, selectedTeam.name)}
                    >
                      {deleteTeamLoadingId === selectedTeam.id ? "팀 삭제 중…" : "팀 삭제"}
                    </button>
                  </div>
                ) : null}
              </>
            ) : null}
          </div>
        )}
      </section>
    </main>
  )
}
