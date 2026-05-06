"use client"

import * as React from "react"
import { ChevronRight, Minus, Plus, Search } from "lucide-react"
import { Checkbox, Label } from "@ai-usage/ui"

type ApiResponse<T> = {
  success: boolean
  message: string
  data: T | null
}

function extractErrorMessage(value: unknown): string | null {
  if (!value || typeof value !== "object") return null
  const obj = value as Record<string, unknown>
  if (typeof obj.message === "string" && obj.message.trim() !== "") return obj.message
  if (typeof obj.error === "string" && obj.error.trim() !== "") return obj.error
  if (typeof obj.detail === "string" && obj.detail.trim() !== "") return obj.detail
  return null
}

type TeamSummary = {
  id: string
  name: string
  createdAt?: string
}

type TeamSummaryLike = {
  id: string | number
  name: string
  createdAt?: string
}

type ExternalKeyProvider = "GEMINI" | "OPENAI" | "ANTHROPIC"

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

type TeamInvitationNotice = {
  invitationId: string
  teamName: string
  viewerRole: "INVITER" | "INVITEE" | "UNKNOWN"
  respondedAt: string | null
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
  return {
    id: String(v.id),
    name: v.name,
    createdAt: typeof v.createdAt === "string" ? v.createdAt : undefined,
  }
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
const EXTERNAL_KEY_PROVIDER_OPTIONS: ExternalKeyProvider[] = ["GEMINI", "OPENAI", "ANTHROPIC"]
const DISMISSED_EXPIRED_INVITATION_STORAGE_KEY = "team.dismissedExpiredInvitationNoticeIds"

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

function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return "-"
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  return date.toLocaleString("ko-KR", { timeZone: "Asia/Seoul" })
}

async function switchActiveTeam(teamId: string) {
  const numericTeamId = Number.parseInt(teamId, 10)
  if (!Number.isFinite(numericTeamId)) {
    throw new Error("유효하지 않은 팀 ID입니다")
  }
  const res = await fetch(`${TEAM_WEB_BASE_PATH}/api/auth/token/switch-team`, {
    method: "POST",
    credentials: "include",
    cache: "no-store",
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify({ targetTeamId: numericTeamId }),
  })
  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as unknown
    const message = extractErrorMessage(body)
    throw new Error(message ?? `팀 전환 토큰 갱신에 실패했습니다 (HTTP ${res.status})`)
  }
}

export function TeamManagementView() {
  const [loading, setLoading] = React.useState(true)
  const [error, setError] = React.useState<string | null>(null)
  const [message, setMessage] = React.useState<{ kind: "success" | "error"; text: string } | null>(null)
  const [teams, setTeams] = React.useState<TeamSummary[]>([])
  const [keyword, setKeyword] = React.useState("")
  const debouncedKeyword = useDebounce(keyword, 500)
  const [isSearching, setIsSearching] = React.useState(false)
  const [teamMemberIdsByTeamId, setTeamMemberIdsByTeamId] = React.useState<Record<string, string[]>>({})
  const [isTeamOwnerByTeamId, setIsTeamOwnerByTeamId] = React.useState<Record<string, boolean | null>>({})
  const [showCreateForm, setShowCreateForm] = React.useState(false)
  const [teamName, setTeamName] = React.useState("")
  const [inviteesOnCreate, setInviteesOnCreate] = React.useState<InviteeFieldRow[]>(() => [newInviteeRow()])
  const [createLoading, setCreateLoading] = React.useState(false)
  const [inviteInputsByTeamId, setInviteInputsByTeamId] = React.useState<Record<string, InviteeFieldRow[]>>({})
  const [inviteLoadingTeamId, setInviteLoadingTeamId] = React.useState<string | null>(null)
  const [teamApiKeysByTeamId, setTeamApiKeysByTeamId] = React.useState<Record<string, TeamApiKeySummary[]>>({})
  const [expiredInvitationNotices, setExpiredInvitationNotices] = React.useState<TeamInvitationNotice[]>([])
  const [dismissedInvitationNoticeIds, setDismissedInvitationNoticeIds] = React.useState<Record<string, true>>({})
  const [expiredInvitationLoading, setExpiredInvitationLoading] = React.useState(false)
  const [switchingTeamId, setSwitchingTeamId] = React.useState<string | null>(null)
  const [apiKeyAliasByTeamId, setApiKeyAliasByTeamId] = React.useState<Record<string, string>>({})
  const [apiKeyValueByTeamId, setApiKeyValueByTeamId] = React.useState<Record<string, string>>({})
  const [apiKeyProviderByTeamId, setApiKeyProviderByTeamId] = React.useState<Record<string, ExternalKeyProvider>>({})
  const [apiKeyMonthlyBudgetByTeamId, setApiKeyMonthlyBudgetByTeamId] = React.useState<Record<string, string>>({})
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
  const latestLoadSeqRef = React.useRef(0)

  function filterTeamsByKeyword(items: TeamSummary[], keywordParam?: string): TeamSummary[] {
    const trimmedKeyword = (keywordParam ?? "").trim().toLowerCase()
    if (!trimmedKeyword) {
      return items
    }
    return items.filter((item) => item.name.toLowerCase().includes(trimmedKeyword))
  }

  const loadTeams = React.useCallback(async (keywordParam?: string) => {
    const requestSeq = latestLoadSeqRef.current + 1
    latestLoadSeqRef.current = requestSeq
    setLoading(true)
    setIsSearching(true)
    setError(null)
    try {
      const { res, body } = await requestApi("/api/team/v1/me/teams", { method: "GET" })
      if (requestSeq !== latestLoadSeqRef.current) {
        return
      }
      if (!res.ok || !body?.success || !Array.isArray(body?.data)) {
        setError(body?.message ?? "팀 목록을 불러오지 못했습니다")
        return
      }
      const normalizedTeams = body.data
        .map((item) => normalizeTeamSummary(item))
        .filter((item): item is TeamSummary => item !== null)
      setTeams(filterTeamsByKeyword(normalizedTeams, keywordParam))
    } catch {
      if (requestSeq !== latestLoadSeqRef.current) {
        return
      }
      setError("팀 목록을 불러오지 못했습니다")
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
    setIsTeamOwnerByTeamId((prev) => ({ ...prev, [teamId]: null }))
    try {
      const { res, body } = await requestApi(`/api/team/v1/teams/${encodeURIComponent(teamId)}/owner`, { method: "GET" })
      if (!res.ok || !body?.success || typeof body.data !== "boolean") {
        setIsTeamOwnerByTeamId((prev) => ({ ...prev, [teamId]: null }))
        return
      }
      setIsTeamOwnerByTeamId((prev) => ({ ...prev, [teamId]: body.data as boolean }))
    } catch {
      setIsTeamOwnerByTeamId((prev) => ({ ...prev, [teamId]: null }))
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

  const loadExpiredInvitationNotices = React.useCallback(async () => {
    setExpiredInvitationLoading(true)
    try {
      const { res, body } = await requestApi("/api/team/v1/me/team-invitations?includeExpired=true", { method: "GET" })
      if (!res.ok || !body?.success || !Array.isArray(body.data)) {
        setExpiredInvitationNotices([])
        return
      }
      const notices: TeamInvitationNotice[] = (body.data as unknown[])
        .map((item) => {
          if (!item || typeof item !== "object") return null
          const v = item as Record<string, unknown>
          if (typeof v.invitationId !== "string") return null
          if (typeof v.teamName !== "string") return null
          if (v.status !== "EXPIRED") return null
          const viewerRole = v.viewerRole
          const normalizedRole: TeamInvitationNotice["viewerRole"] =
            viewerRole === "INVITER" || viewerRole === "INVITEE" ? viewerRole : "UNKNOWN"
          return {
            invitationId: v.invitationId,
            teamName: v.teamName,
            viewerRole: normalizedRole,
            respondedAt: typeof v.respondedAt === "string" ? v.respondedAt : null,
          }
        })
        .filter((item): item is TeamInvitationNotice => item !== null)
      setExpiredInvitationNotices(notices)
    } catch {
      setExpiredInvitationNotices([])
    } finally {
      setExpiredInvitationLoading(false)
    }
  }, [])

  React.useEffect(() => {
    void loadTeams(debouncedKeyword)
  }, [loadTeams, debouncedKeyword])

  React.useEffect(() => {
    void loadExpiredInvitationNotices()
  }, [loadExpiredInvitationNotices])

  React.useEffect(() => {
    try {
      const raw = window.localStorage.getItem(DISMISSED_EXPIRED_INVITATION_STORAGE_KEY)
      if (!raw) return
      const parsed = JSON.parse(raw) as unknown
      if (!parsed || typeof parsed !== "object") return
      const restored: Record<string, true> = {}
      for (const [k, v] of Object.entries(parsed as Record<string, unknown>)) {
        if (v === true) restored[k] = true
      }
      setDismissedInvitationNoticeIds(restored)
    } catch {
      // ignore malformed local storage
    }
  }, [])

  React.useEffect(() => {
    try {
      window.localStorage.setItem(
        DISMISSED_EXPIRED_INVITATION_STORAGE_KEY,
        JSON.stringify(dismissedInvitationNoticeIds),
      )
    } catch {
      // ignore storage failures
    }
  }, [dismissedInvitationNoticeIds])

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
    if (!selectedTeamId) return
    if (!teams.some((t) => t.id === selectedTeamId)) return
    void loadTeamOwnerFlag(selectedTeamId)
    void loadTeamMembers(selectedTeamId)
    void loadTeamApiKeys(selectedTeamId)
  }, [selectedTeamId, teams, loadTeamApiKeys, loadTeamMembers, loadTeamOwnerFlag])

  React.useEffect(() => {
    if (!message) return
    if (message.kind === "error") return
    const timeoutId = window.setTimeout(() => setMessage(null), 3500)
    return () => window.clearTimeout(timeoutId)
  }, [message])

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

  async function _invite(teamId: string) {
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

  function _startEditTeamApiKey(teamId: string, row: TeamApiKeySummary) {
    setEditingTeamApiKey({ teamId, keyId: row.id })
    setEditTeamApiKeyAlias(row.alias)
    setEditTeamApiKeyBudget(
      row.monthlyBudgetUsd !== null && row.monthlyBudgetUsd !== undefined ? String(row.monthlyBudgetUsd) : "",
    )
    setMessage(null)
  }

  async function _saveEditTeamApiKey(teamId: string) {
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

  async function _registerTeamApiKey(teamId: string) {
    if (apiKeyLoadingTeamId) return
    const provider = apiKeyProviderByTeamId[teamId] ?? "OPENAI"
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
      setApiKeyProviderByTeamId((prev) => ({ ...prev, [teamId]: "OPENAI" }))
      setApiKeyMonthlyBudgetByTeamId((prev) => ({ ...prev, [teamId]: "" }))
      await loadTeamApiKeys(teamId)
    } catch {
      setMessage({ kind: "error", text: "팀 API Key 등록에 실패했습니다" })
    } finally {
      setApiKeyLoadingTeamId(null)
    }
  }

  function _openTeamApiKeyDeletionModal(teamId: string, keyId: number) {
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

  async function _cancelTeamApiKeyDeletion(teamId: string, keyId: number) {
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

  async function _removeTeamMember(teamId: string, memberId: string) {
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

  async function _deleteTeam(teamId: string, teamName: string) {
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

  async function _selectTeam(teamId: string, isSelected: boolean) {
    cancelEditTeamApiKey()
    if (isSelected) {
      setSelectedTeamId(null)
      return
    }
    if (switchingTeamId) return
    setSelectedTeamId(teamId)
    setInviteInputsByTeamId((prev) => (prev[teamId] !== undefined ? prev : { ...prev, [teamId]: [newInviteeRow()] }))
    setSwitchingTeamId(teamId)
    setMessage(null)
    try {
      await switchActiveTeam(teamId)
      setMessage({ kind: "success", text: "활성 팀이 전환되었습니다" })
    } catch (e) {
      const details = e instanceof Error ? e.message : "팀 전환 토큰 갱신에 실패했습니다"
      setMessage({
        kind: "error",
        text: `${details} (상세 UI는 계속 사용 가능합니다)`,
      })
    } finally {
      setSwitchingTeamId(null)
    }
  }

  const teamDeletionModalParsed = teamApiKeyDeletionModal
    ? parseTeamApiKeyDeletionGraceInput(teamApiKeyDeletionModal.graceDaysInput)
    : null
  const visibleExpiredInvitationNotices = expiredInvitationNotices.filter(
    (notice) => dismissedInvitationNoticeIds[notice.invitationId] !== true,
  )

  return (
    <main className="flex h-full min-h-0 w-full min-w-0 max-w-full flex-col overflow-x-hidden bg-background text-foreground">
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
            className="w-full max-w-md rounded-lg border border-border bg-card p-5 shadow-lg"
            onMouseDown={(e) => e.stopPropagation()}
          >
            <h3 id="team-api-key-delete-title" className="text-sm font-semibold text-foreground">
              팀 API Key 삭제
            </h3>
            <p className="mt-2 text-sm text-muted-foreground">
              {teamDeletionModalParsed?.valid && teamDeletionModalParsed.immediate
                ? null
                : "유예 기간이 지나면 키가 영구 삭제됩니다. 유예 중에는 삭제 취소를 할 수 있습니다."}
            </p>
            {teamDeletionModalParsed?.valid && teamDeletionModalParsed.immediate ? (
              <p className="mt-2 text-sm font-medium text-red-600">이 API Key는 즉시 영구 삭제됩니다.</p>
            ) : null}
            <div className="mt-4 space-y-1.5">
              <label className="text-xs font-medium text-foreground" htmlFor="team-api-key-delete-grace">
                유예 기간(일)
              </label>
              <input
                id="team-api-key-delete-grace"
                type="text"
                inputMode="numeric"
                className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm tabular-nums text-foreground"
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
              <p className="text-xs text-muted-foreground">{GRACE_PERIOD_DELETION_HINT}</p>
              {teamApiKeyDeletionGraceError ? (
                <p className="text-xs text-red-600">{teamApiKeyDeletionGraceError}</p>
              ) : null}
            </div>
            <div className="mt-4 flex gap-3 rounded-md border border-border bg-muted/40 p-3">
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
                <Label htmlFor="team-api-key-retain-logs" className="text-xs font-medium leading-snug text-foreground">
                  팀 API 사용 기록 보존
                </Label>
                <p className="text-xs leading-relaxed text-muted-foreground">
                  체크 해제 시, API Key가 최종적으로 완전히 삭제되는 시점에 이 키로 발생한 모든 팀 호출 로그와 통계도 함께 영구적으로 삭제됩니다.
                </p>
              </div>
            </div>
            <div className="mt-5 flex flex-wrap justify-end gap-2">
              <button
                type="button"
                className="h-9 rounded-md border border-input bg-background px-3 text-xs font-medium text-foreground hover:bg-muted disabled:opacity-50"
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
                    : "h-9 rounded-md border border-red-300 bg-background px-3 text-xs font-medium text-red-600 hover:bg-red-50 disabled:opacity-50"
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
<<<<<<< HEAD
      <aside className="w-full min-w-0 max-w-full shrink-0 border-r border-border bg-sidebar">
        <div className="flex min-w-0 flex-col p-3">
          <div className="sticky top-0 z-10 rounded-t-lg border border-border bg-background px-4 py-4 backdrop-blur-sm">
=======
      <aside className="h-full w-full min-w-0 max-w-full shrink-0 border-r border-border bg-muted/20">
        <div className="flex h-full min-h-0 min-w-0 flex-col">
          <div className="sticky top-0 z-10 border-b border-border bg-muted/20 px-4 py-4 backdrop-blur-sm">
>>>>>>> origin/develop
            <div className="flex items-center justify-between gap-2">
              <h2 className="text-base font-semibold text-foreground">팀 목록</h2>
              <button
                type="button"
                className="h-8 rounded-md border border-border bg-background px-2.5 text-xs font-medium text-foreground hover:bg-muted disabled:opacity-60"
                onClick={openCreateForm}
                disabled={createLoading}
              >
                + 새 팀
              </button>
            </div>
            <p className="mt-1 text-xs text-muted-foreground">팀을 선택하면 항목 아래에서 상세 정보를 확인할 수 있습니다.</p>
<<<<<<< HEAD
            <div className="relative mt-3 flex items-center">
              <Search className="pointer-events-none absolute left-4 top-1/2 h-[15px] w-[15px] -translate-y-1/2 text-muted-foreground/70" aria-hidden />
              <input
                id="team-search"
                className="h-12 min-h-[48px] w-full rounded-md border border-input bg-background pl-12 pr-4 text-sm text-foreground outline-none transition placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring/40"
=======
            <div className="relative mt-3">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground/70" aria-hidden />
              <input
                id="team-search"
                className="h-11 min-h-[60px] w-full rounded-md border border-input bg-background pl-9 pr-3 text-sm text-foreground outline-none transition placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring/40"
>>>>>>> origin/develop
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                placeholder="팀 이름 검색"
                autoComplete="off"
              />
            </div>
            {showCreateForm ? (
              <form className="mt-3 space-y-3 rounded-md border border-border bg-background p-3" onSubmit={createTeam}>
                <div className="space-y-1">
                  <label className="text-xs font-medium text-foreground">팀 이름 (필수)</label>
                  <input
                    className="h-9 w-full rounded-md border border-input bg-background px-3 text-sm text-foreground placeholder:text-muted-foreground"
                    value={teamName}
                    onChange={(e) => setTeamName(e.target.value)}
                    placeholder="예: 플랫폼팀"
                    autoComplete="off"
                    disabled={createLoading}
                    required
                  />
                </div>
                <div className="space-y-2">
                  <label className="text-xs font-medium text-foreground">팀원 초대 (선택)</label>
                  <div className="space-y-2">
                    {inviteesOnCreate.map((row) => (
                      <div key={row.id} className="flex gap-2">
                        <input
                          className="h-9 min-w-0 flex-1 rounded-md border border-input bg-background px-3 text-sm text-foreground placeholder:text-muted-foreground"
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
                            className="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-md border border-border bg-background text-foreground hover:bg-muted disabled:opacity-50"
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
                      className="inline-flex h-9 w-full items-center justify-center gap-1.5 rounded-md border border-dashed border-border bg-muted/30 px-3 text-xs text-foreground hover:bg-muted disabled:opacity-50"
                      disabled={createLoading}
                      onClick={() => setInviteesOnCreate((prev) => [...prev, newInviteeRow()])}
                    >
                      <Plus className="h-4 w-4" aria-hidden />
                      초대 대상 추가
                    </button>
                  </div>
                </div>
                <div className="flex min-w-0 flex-wrap gap-2">
                  <button
                    type="submit"
                    className="h-9 rounded-md bg-foreground px-3 text-xs font-medium text-background disabled:opacity-60"
                    disabled={createLoading}
                  >
                    {createLoading ? "생성 중…" : "생성"}
                  </button>
                  <button
                    type="button"
                    className="h-9 rounded-md border border-border bg-background px-3 text-xs font-medium text-foreground hover:bg-muted"
                    onClick={closeCreateForm}
                    disabled={createLoading}
                  >
                    취소
                  </button>
                </div>
              </form>
            ) : null}
          </div>

<<<<<<< HEAD
          <div className="min-w-0 flex-1 overflow-x-hidden rounded-b-lg border-x border-b border-border bg-background p-4 pt-3 pb-6">
=======
          <div className="min-h-0 min-w-0 flex-1 overflow-y-auto overflow-x-hidden p-3">
>>>>>>> origin/develop
            {message ? (
              <div
                className={`mb-2 rounded-md border px-3 py-2 text-[11px] font-medium leading-5 ${
                  message.kind === "success"
                    ? "border-emerald-200 bg-emerald-50 text-emerald-700"
                    : "border-red-200 bg-red-50 text-red-700"
                }`}
                role="status"
              >
                {message.text}
              </div>
            ) : null}
            <div className="mb-2 rounded-lg border border-amber-200 bg-amber-50 p-3">
              <div className="flex items-center justify-between gap-2">
                <p className="text-xs font-semibold text-amber-800">만료된 초대 알림</p>
                <button
                  type="button"
                  className="h-7 rounded border border-amber-300 bg-background px-2 text-[11px] text-amber-800 hover:bg-amber-100 disabled:opacity-50"
                  onClick={() => void loadExpiredInvitationNotices()}
                  disabled={expiredInvitationLoading}
                >
                  {expiredInvitationLoading ? "불러오는 중…" : "새로고침"}
                </button>
              </div>
              {expiredInvitationLoading ? (
                <p className="mt-2 text-xs text-amber-800">만료된 초대 알림을 불러오는 중…</p>
              ) : visibleExpiredInvitationNotices.length === 0 ? (
                <p className="mt-2 text-xs text-amber-800">표시할 만료된 초대 알림이 없습니다.</p>
              ) : (
                <ul className="mt-2 space-y-2">
                  {visibleExpiredInvitationNotices.map((notice) => (
                    <li
                      key={`expired-invitation-${notice.invitationId}`}
                      className="rounded-md border border-amber-200 bg-background px-2 py-2"
                    >
                      <div className="flex items-start justify-between gap-2">
                        <div className="min-w-0">
                          <p className="truncate text-xs font-semibold text-amber-900">{notice.teamName}</p>
                          <p className="mt-1 text-[11px] text-amber-800">
                            {notice.viewerRole === "INVITER"
                              ? "보낸 초대가 만료되었습니다. 다시 초대를 보내주세요."
                              : "받은 초대가 만료되었습니다."}
                          </p>
                          {notice.respondedAt ? (
                            <p className="mt-1 text-[10px] text-amber-800/80">만료 처리: {formatDateTime(notice.respondedAt)}</p>
                          ) : null}
                        </div>
                        <button
                          type="button"
                          className="h-6 rounded border border-amber-300 bg-background px-1.5 text-[10px] text-amber-800 hover:bg-amber-100"
                          onClick={() =>
                            setDismissedInvitationNoticeIds((prev) => ({
                              ...prev,
                              [notice.invitationId]: true,
                            }))
                          }
                        >
                          X
                        </button>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </div>
            {loading ? <p className="px-2 py-3 text-sm text-muted-foreground">{isSearching ? "검색 중..." : "불러오는 중…"}</p> : null}
            {error && !loading ? <p className="px-2 py-3 text-sm text-red-600">{error}</p> : null}
            {!loading && !error && teams.length === 0 ? (
              <p className="px-2 py-3 text-sm text-muted-foreground">{debouncedKeyword.trim() ? "검색된 팀이 없습니다" : "참여 중인 팀이 없습니다."}</p>
            ) : null}
            {!loading && teams.length > 0 ? (
<<<<<<< HEAD
              <ul className="flex min-h-full flex-col space-y-3">
=======
              <ul className="flex min-h-full flex-col space-y-2">
>>>>>>> origin/develop
                {teams.map((team) => {
                  const isSelected = selectedTeamId === team.id
                  return (
                    <li key={team.id} className="space-y-3">
                      <button
                        type="button"
                        className={`w-full rounded-lg border-2 px-3 py-2 text-left transition ${
                          isSelected
<<<<<<< HEAD
                            ? "border-black bg-card"
                            : "border-transparent bg-card hover:border-foreground/30 hover:bg-muted/40"
=======
                            ? "border-foreground bg-card shadow-sm"
                            : "border-border bg-card hover:border-foreground/30 hover:bg-muted/40"
>>>>>>> origin/develop
                        }`}
                        onClick={() => void _selectTeam(team.id, isSelected)}
                        disabled={switchingTeamId !== null && switchingTeamId !== team.id}
                      >
                        <div className="flex items-center gap-2">
                          {isSelected ? (
                            <ChevronRight className="h-4 w-4 shrink-0 text-foreground" aria-hidden />
                          ) : (
                            <ChevronRight className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
                          )}
                          <span className="truncate text-sm font-medium text-foreground">{team.name}</span>
                          {switchingTeamId === team.id ? (
<<<<<<< HEAD
                            <span className="inline-flex shrink-0 whitespace-nowrap rounded bg-muted px-1.5 py-0.5 text-[11px] font-medium leading-5 text-muted-foreground">
                              전환 중…
                            </span>
=======
                            <span className="rounded bg-muted px-1.5 py-0.5 text-[10px] text-muted-foreground">전환 중…</span>
>>>>>>> origin/develop
                          ) : null}
                        </div>
                      </button>
                      {isSelected ? (
<<<<<<< HEAD
                        <div className="min-w-0 space-y-4 overflow-x-hidden break-words rounded-lg bg-card p-3">
=======
                        <div className="min-w-0 space-y-3 overflow-x-hidden break-words rounded-lg border border-border bg-card p-3">
>>>>>>> origin/develop
                          <div className="flex items-center justify-between gap-2">
                            <p className="text-xs font-semibold text-foreground">멤버 목록</p>
                            <p className="text-[11px] text-muted-foreground">
                              {(teamMemberIdsByTeamId[team.id] ?? []).length}명
                            </p>
                          </div>
                          {(teamMemberIdsByTeamId[team.id] ?? []).length > 0 ? (
<<<<<<< HEAD
                            <ul className="space-y-2 text-xs text-muted-foreground">
=======
                            <ul className="space-y-1 text-xs text-muted-foreground">
>>>>>>> origin/develop
                              {(teamMemberIdsByTeamId[team.id] ?? []).map((memberId) => (
                                <li key={`${team.id}-member-inline-${memberId}`} className="flex items-center justify-between gap-2">
                                  <span className="truncate">{memberId}</span>
                                  {isTeamOwnerByTeamId[team.id] === true ? (
                                    <button
                                      type="button"
                                      className="rounded border border-red-300 bg-background px-2 py-1 text-[11px] text-red-600 disabled:opacity-50"
                                      disabled={removeMemberLoadingKey === `${team.id}:${memberId}`}
                                      onClick={() => void _removeTeamMember(team.id, memberId)}
                                    >
                                      {removeMemberLoadingKey === `${team.id}:${memberId}` ? "삭제 중…" : "삭제"}
                                    </button>
                                  ) : null}
                                </li>
                              ))}
                            </ul>
                          ) : (
                            <p className="text-xs text-muted-foreground">등록된 팀원이 없습니다.</p>
                          )}
<<<<<<< HEAD
                          <div className="space-y-3 rounded-md bg-background p-3">
=======
                          <div className="space-y-2 rounded-md border border-border bg-muted/40 p-2">
>>>>>>> origin/develop
                            {(inviteInputsByTeamId[team.id] ?? []).map((row) => (
                              <div key={`${team.id}-${row.id}`} className="flex min-w-0 flex-wrap gap-2">
                                <input
                                  className="h-8 min-w-0 flex-1 rounded-md border border-input bg-background px-2 text-xs text-foreground"
                                  value={row.value}
                                  onChange={(e) => {
                                    const v = e.target.value
                                    setInviteInputsByTeamId((prev) => {
                                      const list = prev[team.id] ?? []
                                      return { ...prev, [team.id]: list.map((r) => (r.id === row.id ? { ...r, value: v } : r)) }
                                    })
                                  }}
                                  placeholder="초대할 사용자 이메일/아이디"
                                  autoComplete="off"
                                  disabled={inviteLoadingTeamId === team.id}
                                />
                                {(inviteInputsByTeamId[team.id] ?? []).length > 1 ? (
                                  <button
                                    type="button"
                                    className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-md border border-input bg-background text-foreground hover:bg-muted disabled:opacity-50"
                                    aria-label="이 초대 행 삭제"
                                    disabled={inviteLoadingTeamId === team.id}
                                    onClick={() =>
                                      setInviteInputsByTeamId((prev) => {
                                        const list = prev[team.id] ?? []
                                        return { ...prev, [team.id]: list.filter((r) => r.id !== row.id) }
                                      })
                                    }
                                  >
                                    <Minus className="h-4 w-4" aria-hidden />
                                  </button>
                                ) : null}
                              </div>
                            ))}
                            <div className="flex min-w-0 flex-wrap gap-2">
                              <button
                                type="button"
                                className="h-8 rounded-md border border-dashed border-input bg-background px-2 text-[11px] text-foreground hover:bg-muted disabled:opacity-50"
                                disabled={inviteLoadingTeamId === team.id}
                                onClick={() =>
                                  setInviteInputsByTeamId((prev) => {
                                    const list = prev[team.id] ?? [newInviteeRow()]
                                    return { ...prev, [team.id]: [...list, newInviteeRow()] }
                                  })
                                }
                              >
                                초대 대상 추가
                              </button>
                              <button
                                type="button"
                                className="h-8 rounded-md border border-input bg-background px-2 text-[11px] font-medium text-foreground disabled:opacity-60"
                                disabled={inviteLoadingTeamId === team.id}
                                onClick={() => void _invite(team.id)}
                              >
                                {inviteLoadingTeamId === team.id ? "초대 중…" : "멤버 초대"}
                              </button>
                            </div>
                          </div>

<<<<<<< HEAD
                          <div className="mt-2 space-y-3 border-t border-border pt-4">
                            <p className="text-xs font-semibold text-foreground">API Key 목록</p>
                            <div className="mt-2 space-y-3 rounded-md bg-background p-3">
=======
                          <div className="border-t border-border pt-3">
                            <p className="text-xs font-semibold text-foreground">API Key 목록</p>
                            <div className="mt-2 space-y-2 rounded-md border border-border bg-muted/40 p-2">
>>>>>>> origin/develop
                              <select
                                className="h-8 w-full rounded-md border border-input bg-background px-2 text-xs text-foreground"
                                value={apiKeyProviderByTeamId[team.id] ?? "OPENAI"}
                                onChange={(e) =>
                                  setApiKeyProviderByTeamId((prev) => ({
                                    ...prev,
                                    [team.id]: e.target.value as ExternalKeyProvider,
                                  }))
                                }
                                disabled={apiKeyLoadingTeamId === team.id}
                              >
                                {EXTERNAL_KEY_PROVIDER_OPTIONS.map((provider) => (
                                  <option key={`${team.id}-provider-${provider}`} value={provider}>
                                    {provider}
                                  </option>
                                ))}
                              </select>
                              <input
                                className="h-8 w-full rounded-md border border-input bg-background px-2 text-xs text-foreground"
                                value={apiKeyAliasByTeamId[team.id] ?? ""}
                                onChange={(e) => setApiKeyAliasByTeamId((prev) => ({ ...prev, [team.id]: e.target.value }))}
                                placeholder="API Key 별칭"
                                autoComplete="off"
                                disabled={apiKeyLoadingTeamId === team.id}
                              />
                              <input
                                type="password"
                                className="h-8 w-full rounded-md border border-input bg-background px-2 text-xs text-foreground"
                                value={apiKeyValueByTeamId[team.id] ?? ""}
                                onChange={(e) => setApiKeyValueByTeamId((prev) => ({ ...prev, [team.id]: e.target.value }))}
                                placeholder="API Key 값"
                                autoComplete="new-password"
                                disabled={apiKeyLoadingTeamId === team.id}
                              />
                              <input
                                type="number"
                                step={0.01}
                                min={0}
                                className="h-8 w-full rounded-md border border-input bg-background px-2 text-xs text-foreground"
                                value={apiKeyMonthlyBudgetByTeamId[team.id] ?? ""}
                                onChange={(e) => setApiKeyMonthlyBudgetByTeamId((prev) => ({ ...prev, [team.id]: e.target.value }))}
                                placeholder="월 예산 USD"
                                inputMode="decimal"
                                autoComplete="off"
                                disabled={apiKeyLoadingTeamId === team.id}
                              />
                              <button
                                type="button"
                                className="h-8 rounded-md border border-input bg-background px-2 text-[11px] font-medium text-foreground disabled:opacity-60"
                                disabled={apiKeyLoadingTeamId === team.id}
                                onClick={() => void _registerTeamApiKey(team.id)}
                              >
                                {apiKeyLoadingTeamId === team.id ? "등록 중…" : "팀 API Key 등록"}
                              </button>
                            </div>
                            {(teamApiKeysByTeamId[team.id] ?? []).length > 0 ? (
                              <ul className="mt-2 space-y-2 text-xs text-muted-foreground">
                                {(teamApiKeysByTeamId[team.id] ?? []).map((apiKey) => {
                                  const isEditing =
                                    editingTeamApiKey?.teamId === team.id && editingTeamApiKey?.keyId === apiKey.id
                                  const keyPendingDeletion = Boolean(apiKey.deletionRequestedAt)
                                  const keyAction = `${team.id}:${apiKey.id}`
                                  return (
<<<<<<< HEAD
                                    <li key={`${team.id}-key-inline-${apiKey.id}`} className="rounded bg-background p-2">
=======
                                    <li key={`${team.id}-key-inline-${apiKey.id}`} className="rounded border border-border bg-card p-2">
>>>>>>> origin/develop
                                      {!isEditing ? (
                                        <div className="space-y-1">
                                          <p className="truncate">
                                            {apiKey.provider} · {apiKey.alias}
                                            {keyPendingDeletion ? (
                                              <span className="ml-1 rounded bg-muted px-1 py-0.5 text-[10px] font-medium text-foreground">삭제 예정</span>
                                            ) : null}
                                          </p>
                                          <div className="flex flex-wrap gap-1">
                                            <button
                                              type="button"
                                              className="h-7 rounded border border-input bg-background px-2 text-[11px] font-medium text-foreground disabled:opacity-50"
                                              disabled={keyPendingDeletion}
                                              onClick={() => _startEditTeamApiKey(team.id, apiKey)}
                                            >
                                              수정
                                            </button>
                                            {keyPendingDeletion ? (
                                              <button
                                                type="button"
                                                className="h-7 rounded border border-input bg-background px-2 text-[11px] font-medium text-foreground disabled:opacity-50"
                                                disabled={cancelDeleteLoadingKey === keyAction}
                                                onClick={() => void _cancelTeamApiKeyDeletion(team.id, apiKey.id)}
                                              >
                                                {cancelDeleteLoadingKey === keyAction ? "처리 중…" : "삭제 취소"}
                                              </button>
                                            ) : (
                                              <button
                                                type="button"
                                                className="h-7 rounded border border-red-300 bg-background px-2 text-[11px] font-medium text-red-600 disabled:opacity-50"
                                                disabled={deleteLoadingKey === keyAction}
                                                onClick={() => _openTeamApiKeyDeletionModal(team.id, apiKey.id)}
                                              >
                                                {deleteLoadingKey === keyAction ? "처리 중…" : "삭제"}
                                              </button>
                                            )}
                                          </div>
                                        </div>
                                      ) : (
                                        <div className="space-y-2">
                                          <input
                                            className="h-8 w-full rounded-md border border-input bg-background px-2 text-xs text-foreground"
                                            value={editTeamApiKeyAlias}
                                            onChange={(e) => setEditTeamApiKeyAlias(e.target.value)}
                                            placeholder="별칭"
                                            disabled={teamApiKeyUpdateLoading === keyAction}
                                          />
                                          <input
                                            type="number"
                                            step={0.01}
                                            min={0}
                                            className="h-8 w-full rounded-md border border-input bg-background px-2 text-xs text-foreground"
                                            value={editTeamApiKeyBudget}
                                            onChange={(e) => setEditTeamApiKeyBudget(e.target.value)}
                                            placeholder="월 예산 USD"
                                            disabled={teamApiKeyUpdateLoading === keyAction}
                                          />
                                          <div className="flex min-w-0 flex-wrap gap-1">
                                            <button
                                              type="button"
                                              className="h-7 rounded bg-black px-2 text-[11px] font-medium text-white disabled:opacity-60"
                                              disabled={teamApiKeyUpdateLoading === keyAction}
                                              onClick={() => void _saveEditTeamApiKey(team.id)}
                                            >
                                              {teamApiKeyUpdateLoading === keyAction ? "저장 중…" : "저장"}
                                            </button>
                                            <button
                                              type="button"
                                              className="h-7 rounded border border-input bg-background px-2 text-[11px] font-medium text-foreground"
                                              disabled={teamApiKeyUpdateLoading === keyAction}
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
                              <p className="mt-1 text-xs text-muted-foreground">등록된 팀 API Key가 없습니다.</p>
                            )}
                          </div>
<<<<<<< HEAD
                          <div className="space-y-3 border-t border-border pt-4">
                            {isTeamOwnerByTeamId[team.id] === true ? (
                              <div className="space-y-2 bg-transparent p-0">
                              <p className="text-[11px] text-muted-foreground">팀장은 팀 API 키를 모두 정리한 뒤 팀을 삭제할 수 있습니다.</p>
                              <button
                                type="button"
                                className="rounded border border-red-300 bg-background px-2 py-1 text-[11px] font-medium text-red-600 disabled:opacity-50"
=======
                          {isTeamOwnerByTeamId[team.id] === true ? (
                            <div className="rounded-md border border-red-200 bg-red-50 p-2">
                              <p className="text-[11px] text-muted-foreground">팀장은 팀 API 키를 모두 정리한 뒤 팀을 삭제할 수 있습니다.</p>
                              <button
                                type="button"
                                className="mt-1 rounded border border-red-300 bg-background px-2 py-1 text-[11px] font-medium text-red-600 disabled:opacity-50"
>>>>>>> origin/develop
                                disabled={deleteTeamLoadingId === team.id}
                                onClick={() => void _deleteTeam(team.id, team.name)}
                              >
                                {deleteTeamLoadingId === team.id ? "팀 삭제 중…" : "팀 삭제"}
                              </button>
<<<<<<< HEAD
                              </div>
                            ) : isTeamOwnerByTeamId[team.id] === false ? (
                              <div className="rounded-md bg-transparent p-0">
                                <p className="text-[11px] text-muted-foreground">
                                  팀 삭제는 팀장만 가능합니다. (현재 계정은 팀장 권한이 아닙니다)
                                </p>
                              </div>
                            ) : (
                              <div className="rounded-md bg-transparent p-0">
                                <p className="text-[11px] text-muted-foreground">팀장 권한을 확인하는 중입니다.</p>
                              </div>
                            )}
                          </div>
=======
                            </div>
                          ) : isTeamOwnerByTeamId[team.id] === false ? (
                            <div className="rounded-md border border-border bg-muted/40 p-2">
                              <p className="text-[11px] text-muted-foreground">
                                팀 삭제는 팀장만 가능합니다. (현재 계정은 팀장 권한이 아닙니다)
                              </p>
                            </div>
                          ) : (
                            <div className="rounded-md border border-border bg-muted/40 p-2">
                              <p className="text-[11px] text-muted-foreground">팀장 권한을 확인하는 중입니다.</p>
                            </div>
                          )}
>>>>>>> origin/develop
                        </div>
                      ) : null}
                    </li>
                  )
                })}
              </ul>
            ) : null}
          </div>
        </div>
      </aside>
    </main>
  )
}
