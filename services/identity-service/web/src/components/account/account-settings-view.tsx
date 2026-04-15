"use client"

import * as React from "react"
import { Eye, EyeOff } from "lucide-react"

import { apiFetch } from "@/lib/api/client-fetch"
import type {
  ApiResponse,
  ExternalKeyListResponseData,
  ExternalKeyProvider,
  ExternalKeySummary,
  SessionResponse,
} from "@/lib/api/identity/types"

function asApiResponse(json: unknown): ApiResponse<unknown> | null {
  if (!json || typeof json !== "object") return null
  const record = json as Record<string, unknown>
  if (typeof record.success !== "boolean") return null
  if (typeof record.message !== "string") return null
  if (!("data" in record)) return null
  return record as ApiResponse<unknown>
}

function providerLabel(provider: ExternalKeyProvider) {
  if (provider === "OPENAI") return "OpenAI"
  if (provider === "GEMINI") return "Gemini"
  return "Anthropic"
}

function defaultAlias(provider: ExternalKeyProvider) {
  return `${providerLabel(provider)} 키 1`
}

function roleLabel(role: string) {
  if (role === "ADMIN") return "관리자"
  if (role === "USER") return "사용자"
  return role
}

function formatCreatedAt(iso: string) {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleString("ko-KR", { timeZone: "Asia/Seoul" })
}

function formatDeadline(iso: string | null | undefined) {
  if (!iso) return ""
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

const DEFAULT_DELETION_GRACE_DAYS = 7
const MIN_DELETION_GRACE_DAYS = 1
const MAX_DELETION_GRACE_DAYS = 365

function sanitizeBudgetInput(value: string) {
  const normalized = value.replace(/,/g, ".")
  const filtered = normalized.replace(/[^\d.]/g, "")
  const [integerPart, ...rest] = filtered.split(".")
  const fractionPart = rest.join("")
  if (rest.length === 0) return integerPart
  return `${integerPart}.${fractionPart}`
}

function isPendingDeletion(row: ExternalKeySummary) {
  return Boolean(row.deletionRequestedAt && row.permanentDeletionAt)
}

export function AccountSettingsView({ pathSegments }: { pathSegments?: string[] }) {
  const [session, setSession] = React.useState<SessionResponse | null>(null)
  const [loading, setLoading] = React.useState(true)
  const [error, setError] = React.useState<string | null>(null)

  const [provider, setProvider] = React.useState<ExternalKeyProvider>("OPENAI")
  const [alias, setAlias] = React.useState(() => defaultAlias("OPENAI"))
  const [externalKey, setExternalKey] = React.useState("")
  const [monthlyBudgetUsdInput, setMonthlyBudgetUsdInput] = React.useState("")
  const [aliasTouched, setAliasTouched] = React.useState(false)
  const [submitLoading, setSubmitLoading] = React.useState(false)
  const [submitMessage, setSubmitMessage] = React.useState<{ kind: "success" | "error"; text: string } | null>(
    null
  )

  /** `null`은 아직 목록을 받기 전(세션 직후 첫 프레임 포함). */
  const [externalKeys, setExternalKeys] = React.useState<ExternalKeySummary[] | null>(null)
  const [keysLoading, setKeysLoading] = React.useState(false)
  const [keysError, setKeysError] = React.useState<string | null>(null)
  const [keyActionId, setKeyActionId] = React.useState<number | null>(null)
  const [editKeyId, setEditKeyId] = React.useState<number | null>(null)
  const [editAliasValue, setEditAliasValue] = React.useState("")
  const [editMonthlyBudgetUsdInput, setEditMonthlyBudgetUsdInput] = React.useState("")
  const [saveEditLoadingId, setSaveEditLoadingId] = React.useState<number | null>(null)
  const [revealExternalKey, setRevealExternalKey] = React.useState(false)

  const [deletePassword, _setDeletePassword] = React.useState("")
  const [_deleteLoading, setDeleteLoading] = React.useState(false)
  const [_deleteError, setDeleteError] = React.useState<string | null>(null)

  const loadExternalKeys = React.useCallback(async (signal?: AbortSignal) => {
    setKeysLoading(true)
    setKeysError(null)
    try {
      const { response, json } = await apiFetch<ExternalKeyListResponseData>(
        "/api/auth/external-keys",
        { credentials: "include", cache: "no-store", ...(signal ? { signal } : {}) },
        { authRequired: true }
      )
      if (signal?.aborted) return
      if (response.ok && json?.success && Array.isArray(json.data)) {
        setExternalKeys(json.data)
      } else {
        setKeysError(json?.message ?? "외부 키 목록을 불러오지 못했습니다")
        setExternalKeys([])
      }
    } catch {
      if (signal?.aborted) return
      setKeysError("외부 키 목록을 불러오지 못했습니다")
      setExternalKeys([])
    } finally {
      if (!signal?.aborted) setKeysLoading(false)
    }
  }, [])

  React.useEffect(() => {
    let cancelled = false
    ;(async () => {
      setLoading(true)
      setError(null)
      try {
        const { response, json } = await apiFetch<SessionResponse>(
          "/api/auth/session",
          { credentials: "include", cache: "no-store" },
          { authRequired: true }
        )
        if (cancelled) return
        if (response.ok && json?.success && json.data) {
          setSession(json.data)
        } else {
          setError(json?.message ?? "세션을 불러오지 못했습니다")
        }
      } catch {
        if (!cancelled) setError("세션을 불러오지 못했습니다")
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  React.useEffect(() => {
    if (!session) return
    const ac = new AbortController()
    void loadExternalKeys(ac.signal)
    return () => ac.abort()
  }, [session, loadExternalKeys])

  React.useEffect(() => {
    if (!aliasTouched || !alias.trim()) setAlias(defaultAlias(provider))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [provider])

  const subpath = pathSegments?.length ? pathSegments.join(" / ") : null

  async function onSubmitExternalKey(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    if (submitLoading) return

    const aliasTrimmed = alias.trim()
    const externalKeyTrimmed = externalKey.trim()
    const budgetTrimmed = monthlyBudgetUsdInput.trim()

    if (!aliasTrimmed) {
      setSubmitMessage({ kind: "error", text: "별칭(alias)은 필수입니다" })
      return
    }
    if (!externalKeyTrimmed) {
      setSubmitMessage({ kind: "error", text: "외부 API Key는 필수입니다" })
      return
    }

    if (!budgetTrimmed) {
      setSubmitMessage({ kind: "error", text: "월 예산은 필수입니다" })
      return
    }
    const parsedBudget = Number(budgetTrimmed)
    if (!Number.isFinite(parsedBudget) || parsedBudget < 0) {
      setSubmitMessage({ kind: "error", text: "예산은 0 이상의 숫자로 입력해 주세요" })
      return
    }
    const monthlyBudgetUsd = Number(parsedBudget.toFixed(2))

    setSubmitLoading(true)
    setSubmitMessage(null)

    try {
      const { response, json } = await apiFetch<unknown>(
        "/api/auth/external-keys",
        {
          method: "POST",
          credentials: "include",
          cache: "no-store",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            provider,
            alias: aliasTrimmed,
            externalKey: externalKeyTrimmed,
            monthlyBudgetUsd,
          }),
        },
        { authRequired: true }
      )

      const apiResponse = asApiResponse(json)

      if (response.ok && apiResponse?.success) {
        setSubmitMessage({ kind: "success", text: apiResponse.message || "등록되었습니다" })
        setAliasTouched(false)
        setAlias(defaultAlias(provider))
        setRevealExternalKey(false)
        void loadExternalKeys()
      } else {
        setSubmitMessage({ kind: "error", text: apiResponse?.message || "등록에 실패했습니다" })
      }
    } catch {
      setSubmitMessage({ kind: "error", text: "등록에 실패했습니다" })
    } finally {
      setExternalKey("")
      setMonthlyBudgetUsdInput("")
      setSubmitLoading(false)
    }
  }

  async function requestKeyDeletion(id: number) {
    const ok = window.confirm(
      `이 API 키를 삭제 예약합니다.\n\n유예 기간이 지나면 DB에서 키가 영구 삭제됩니다. 유예 중에는 취소할 수 있습니다.\n과거 사용량 로그는 usage 쪽 기록에 남을 수 있습니다.\n\n다음 단계에서 유예 기간(일)을 입력합니다. (기본 ${DEFAULT_DELETION_GRACE_DAYS}일)`,
    )
    if (!ok) return
    const raw = window.prompt(
      `유예 기간(일) (${MIN_DELETION_GRACE_DAYS}~${MAX_DELETION_GRACE_DAYS}, 비우면 ${DEFAULT_DELETION_GRACE_DAYS}일)`,
      String(DEFAULT_DELETION_GRACE_DAYS),
    )
    if (raw === null) return
    const trimmed = raw.trim()
    let graceDays = DEFAULT_DELETION_GRACE_DAYS
    if (trimmed !== "") {
      const n = Number.parseInt(trimmed, 10)
      if (!Number.isFinite(n) || n < MIN_DELETION_GRACE_DAYS || n > MAX_DELETION_GRACE_DAYS) {
        setKeysError(
          `유예 기간은 ${MIN_DELETION_GRACE_DAYS}~${MAX_DELETION_GRACE_DAYS}일 사이의 정수로 입력해 주세요`,
        )
        return
      }
      graceDays = n
    }
    setKeyActionId(id)
    try {
      const q = new URLSearchParams({ gracePeriodDays: String(graceDays) })
      const { response, json } = await apiFetch<unknown>(
        `/api/auth/external-keys/${id}?${q.toString()}`,
        { method: "DELETE", credentials: "include", cache: "no-store", headers: { Accept: "application/json" } },
        { authRequired: true }
      )
      const apiResponse = asApiResponse(json)
      if (response.ok && apiResponse?.success) {
        void loadExternalKeys()
      } else {
        setKeysError(apiResponse?.message ?? "삭제 예약에 실패했습니다")
      }
    } catch {
      setKeysError("삭제 예약에 실패했습니다")
    } finally {
      setKeyActionId(null)
    }
  }

  async function cancelKeyDeletion(id: number) {
    setKeyActionId(id)
    try {
      const { response, json } = await apiFetch<unknown>(
        `/api/auth/external-keys/${id}/deletion-cancel`,
        { method: "POST", credentials: "include", cache: "no-store", headers: { Accept: "application/json" } },
        { authRequired: true }
      )
      const apiResponse = asApiResponse(json)
      if (response.ok && apiResponse?.success) {
        void loadExternalKeys()
      } else {
        setKeysError(apiResponse?.message ?? "삭제 취소에 실패했습니다")
      }
    } catch {
      setKeysError("삭제 취소에 실패했습니다")
    } finally {
      setKeyActionId(null)
    }
  }

  function startKeyEdit(row: ExternalKeySummary) {
    setKeysError(null)
    setEditKeyId(row.id)
    setEditAliasValue(row.alias)
    setEditMonthlyBudgetUsdInput(
      row.monthlyBudgetUsd !== null && row.monthlyBudgetUsd !== undefined ? String(row.monthlyBudgetUsd) : ""
    )
  }

  function cancelKeyEdit() {
    setEditKeyId(null)
    setEditAliasValue("")
    setEditMonthlyBudgetUsdInput("")
  }

  async function saveKeyEdit(row: ExternalKeySummary) {
    const aliasTrimmed = editAliasValue.trim()
    if (!aliasTrimmed) {
      setKeysError("별칭(alias)은 필수입니다")
      return
    }
    const budgetTrimmed = editMonthlyBudgetUsdInput.trim()
    if (!budgetTrimmed) {
      setKeysError("월 예산은 필수입니다")
      return
    }
    const parsedBudget = Number(budgetTrimmed)
    if (!Number.isFinite(parsedBudget) || parsedBudget < 0) {
      setKeysError("예산은 0 이상의 숫자로 입력해 주세요")
      return
    }
    const monthlyBudgetUsd = Number(parsedBudget.toFixed(2))
    const normalizedCurrentBudget =
      row.monthlyBudgetUsd === null || row.monthlyBudgetUsd === undefined ? null : Number(row.monthlyBudgetUsd.toFixed(2))
    if (aliasTrimmed === row.alias && monthlyBudgetUsd === normalizedCurrentBudget) {
      cancelKeyEdit()
      return
    }

    setSaveEditLoadingId(row.id)
    setKeysError(null)
    try {
      const { response, json } = await apiFetch<unknown>(
        `/api/auth/external-keys/${row.id}`,
        {
          method: "PUT",
          credentials: "include",
          cache: "no-store",
          headers: { "Content-Type": "application/json", Accept: "application/json" },
          body: JSON.stringify({ alias: aliasTrimmed, monthlyBudgetUsd }),
        },
        { authRequired: true }
      )
      const apiResponse = asApiResponse(json)
      if (response.ok && apiResponse?.success) {
        cancelKeyEdit()
        void loadExternalKeys()
      } else {
        setKeysError(apiResponse?.message ?? "정보 수정에 실패했습니다")
      }
    } catch {
      setKeysError("정보 수정에 실패했습니다")
    } finally {
      setSaveEditLoadingId(null)
    }
  }

  async function _onDeleteAccount() {
    setDeleteError(null)
    if (!deletePassword.trim()) {
      setDeleteError("Password is required")
      return
    }
    const ok = window.confirm("Delete your account permanently? This cannot be undone.")
    if (!ok) {
      return
    }
    setDeleteLoading(true)
    try {
      const { response, json } = await apiFetch<unknown>(
        "/api/auth/delete-account",
        {
          method: "POST",
          credentials: "include",
          cache: "no-store",
          headers: { "Content-Type": "application/json", Accept: "application/json" },
          body: JSON.stringify({ password: deletePassword }),
        },
        { authRequired: true }
      )
      const apiResponse = asApiResponse(json)
      if (response.ok && apiResponse?.success) {
        window.location.assign("/login")
        return
      }
      setDeleteError(apiResponse?.message ?? "Account deletion failed")
    } catch {
      setDeleteError("Account deletion failed")
    } finally {
      setDeleteLoading(false)
    }
  }

  return (
    <div className="flex min-h-[40vh] flex-col gap-8 py-4">
      <div className="space-y-2">
        <h1 className="text-2xl font-semibold tracking-tight">설정</h1>
        <p className="text-sm text-muted-foreground">로그인 계정 정보입니다. 조직·팀은 각 메뉴에서 관리합니다.</p>
      </div>

      {subpath ? (
        <div className="rounded-lg border border-border bg-muted/20 px-4 py-3 text-sm text-muted-foreground">
          <p className="font-mono text-foreground/80">{subpath}</p>
          <p className="mt-2">이 하위 경로의 화면은 아직 제공되지 않습니다.</p>
        </div>
      ) : null}

      <section className="max-w-lg space-y-3 rounded-lg border border-border bg-card p-5 shadow-sm">
        <h2 className="text-sm font-semibold tracking-tight">계정</h2>
        {loading ? <p className="text-sm text-muted-foreground">불러오는 중…</p> : null}
        {error && !loading ? <p className="text-sm text-destructive">{error}</p> : null}
        {!loading && !error && session ? (
          <dl className="grid gap-3 text-sm">
            <div>
              <dt className="text-muted-foreground">이메일</dt>
              <dd className="mt-0.5 font-medium">{session.email}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">역할</dt>
              <dd className="mt-0.5 font-medium">{roleLabel(session.role)}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">인증 상태</dt>
              <dd className="mt-0.5 font-medium">{session.authenticated ? "로그인됨" : "확인 필요"}</dd>
            </div>
          </dl>
        ) : null}
      </section>

      {session ? (
        <section className="max-w-lg space-y-3 rounded-lg border border-border bg-card p-5 shadow-sm">
          <div className="space-y-1">
            <h2 className="text-sm font-semibold tracking-tight">등록된 외부 API Key</h2>
            <p className="text-sm text-muted-foreground">
              Provider·별칭·등록일만 표시됩니다. 삭제 예정인 키는 별칭 옆에 표시되며, 유예 기간 안에 삭제 예약을 취소할 수 있습니다.
            </p>
          </div>

          {keysLoading || externalKeys === null ? (
            <p className="text-sm text-muted-foreground">목록을 불러오는 중…</p>
          ) : null}
          {keysError && !keysLoading && externalKeys !== null ? <p className="text-sm text-destructive">{keysError}</p> : null}

          {!keysLoading && externalKeys !== null && externalKeys.length === 0 ? (
            <p className="rounded-md border border-dashed border-border bg-muted/20 px-4 py-6 text-center text-sm text-muted-foreground">
              등록된 외부 키가 없습니다. 아래에서 추가할 수 있습니다.
            </p>
          ) : null}

          {!keysLoading && externalKeys !== null && externalKeys.length > 0 ? (
            <ul className="divide-y divide-border rounded-md border border-border">
              {externalKeys.map((row) => (
                <li
                  key={row.id}
                  className="flex flex-col gap-2 px-4 py-3 text-sm sm:flex-row sm:items-start sm:justify-between"
                >
                  <div className="min-w-0 space-y-1">
                    <div className="font-medium">
                      <span className="text-foreground">{providerLabel(row.provider)}</span>
                      <span className="mx-2 text-muted-foreground">·</span>
                      {editKeyId === row.id ? (
                        <div className="inline-flex flex-col gap-2 align-middle">
                          <input
                            className="h-8 rounded-md border border-input bg-background px-2 text-sm"
                            value={editAliasValue}
                            onChange={(e) => setEditAliasValue(e.target.value)}
                            disabled={saveEditLoadingId === row.id}
                            autoComplete="off"
                          />
                          <input
                            className="h-8 rounded-md border border-input bg-background px-2 text-sm"
                            type="text"
                            inputMode="decimal"
                            value={editMonthlyBudgetUsdInput}
                            onChange={(e) => setEditMonthlyBudgetUsdInput(sanitizeBudgetInput(e.target.value))}
                            placeholder="월 예산(USD)"
                            disabled={saveEditLoadingId === row.id}
                            required
                          />
                        </div>
                      ) : (
                        <span className="text-foreground">{row.alias}</span>
                      )}
                      {isPendingDeletion(row) ? (
                        <span className="ml-1.5 text-amber-700 dark:text-amber-500">(삭제 예정)</span>
                      ) : null}
                    </div>
                    {isPendingDeletion(row) && row.permanentDeletionAt ? (
                      <p className="text-xs text-amber-800 dark:text-amber-300">
                        영구 삭제 예정: {formatDeadline(row.permanentDeletionAt)}
                        {typeof row.deletionGraceDays === "number" ? ` (${row.deletionGraceDays}일 유예)` : ""}
                      </p>
                    ) : null}
                    {row.monthlyBudgetUsd !== null && row.monthlyBudgetUsd !== undefined ? (
                      <p className="text-xs text-muted-foreground">월 예산: {formatBudgetUsd(row.monthlyBudgetUsd)}</p>
                    ) : null}
                  </div>
                  <div className="flex shrink-0 flex-col items-stretch gap-2 sm:items-end">
                    <div className="text-muted-foreground tabular-nums sm:text-right">{formatCreatedAt(row.createdAt)}</div>
                    <div className="flex flex-wrap gap-2">
                      {!isPendingDeletion(row) ? (
                        editKeyId === row.id ? (
                          <>
                            <button
                              type="button"
                              className="rounded-md border border-border bg-background px-3 py-1.5 text-xs font-medium hover:bg-muted disabled:opacity-50"
                              disabled={saveEditLoadingId === row.id}
                              onClick={() => void saveKeyEdit(row)}
                            >
                              {saveEditLoadingId === row.id ? "저장 중…" : "저장"}
                            </button>
                            <button
                              type="button"
                              className="rounded-md border border-border bg-background px-3 py-1.5 text-xs font-medium hover:bg-muted disabled:opacity-50"
                              disabled={saveEditLoadingId === row.id}
                              onClick={cancelKeyEdit}
                            >
                              취소
                            </button>
                          </>
                        ) : (
                          <button
                            type="button"
                            className="rounded-md border border-border bg-background px-3 py-1.5 text-xs font-medium hover:bg-muted disabled:opacity-50"
                            disabled={keyActionId === row.id}
                            onClick={() => startKeyEdit(row)}
                          >
                            수정
                          </button>
                        )
                      ) : null}
                      {isPendingDeletion(row) ? (
                        <button
                          type="button"
                          className="rounded-md border border-border bg-background px-3 py-1.5 text-xs font-medium hover:bg-muted disabled:opacity-50"
                          disabled={keyActionId === row.id}
                          onClick={() => void cancelKeyDeletion(row.id)}
                        >
                          {keyActionId === row.id ? "처리 중…" : "삭제 취소"}
                        </button>
                      ) : (
                        <button
                          type="button"
                          className="rounded-md border border-destructive/40 bg-background px-3 py-1.5 text-xs font-medium text-destructive hover:bg-destructive/10 disabled:opacity-50"
                          disabled={keyActionId === row.id}
                          onClick={() => void requestKeyDeletion(row.id)}
                        >
                          {keyActionId === row.id ? "처리 중…" : "삭제 예약"}
                        </button>
                      )}
                    </div>
                  </div>
                </li>
              ))}
            </ul>
          ) : null}
        </section>
      ) : null}

      <section className="max-w-lg space-y-3 rounded-lg border border-border bg-card p-5 shadow-sm">
        <div className="space-y-1">
          <h2 className="text-sm font-semibold tracking-tight">외부 API Key 등록</h2>
          <p className="text-sm text-muted-foreground">
            개인 용도의 외부 Provider 키를 등록합니다. 키 값은 저장되기 전에 서버에서 암호화됩니다.
          </p>
        </div>

        <form className="space-y-3" onSubmit={onSubmitExternalKey}>
          <div className="grid gap-1.5">
            <label className="text-sm font-medium" htmlFor="external-key-provider">
              Provider
            </label>
            <select
              id="external-key-provider"
              className="h-10 rounded-md border border-input bg-background px-3 text-sm"
              value={provider}
              onChange={(e) => setProvider(e.target.value as ExternalKeyProvider)}
              disabled={submitLoading}
            >
              <option value="GEMINI">GEMINI</option>
              <option value="OPENAI">OPENAI</option>
              <option value="ANTHROPIC">ANTHROPIC</option>
            </select>
          </div>

          <div className="grid gap-1.5">
            <label className="text-sm font-medium" htmlFor="external-key-alias">
              별칭(alias)
            </label>
            <input
              id="external-key-alias"
              className="h-10 rounded-md border border-input bg-background px-3 text-sm"
              value={alias}
              onChange={(e) => {
                setAliasTouched(true)
                setAlias(e.target.value)
              }}
              placeholder={defaultAlias(provider)}
              autoComplete="off"
              disabled={submitLoading}
              required
            />
          </div>

          <div className="grid gap-1.5">
            <label className="text-sm font-medium" htmlFor="external-key-value">
              외부 API Key
            </label>
            <div className="flex gap-1">
              <input
                id="external-key-value"
                className="h-10 min-w-0 flex-1 rounded-md border border-input bg-background px-3 text-sm"
                type={revealExternalKey ? "text" : "password"}
                value={externalKey}
                onChange={(e) => setExternalKey(e.target.value)}
                placeholder="키를 입력하세요"
                autoComplete="new-password"
                disabled={submitLoading}
                required
              />
              <button
                type="button"
                className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-md border border-input bg-background text-muted-foreground hover:bg-muted disabled:opacity-50"
                aria-label={revealExternalKey ? "API Key 숨기기" : "API Key 보기"}
                disabled={submitLoading}
                onClick={() => setRevealExternalKey((v) => !v)}
              >
                {revealExternalKey ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>
          </div>

          <div className="grid gap-1.5">
            <label className="text-sm font-medium" htmlFor="external-key-monthly-budget-usd">
              월 예산 (USD)
            </label>
            <input
              id="external-key-monthly-budget-usd"
              className="h-10 rounded-md border border-input bg-background px-3 text-sm"
              type="text"
              inputMode="decimal"
              value={monthlyBudgetUsdInput}
              onChange={(e) => setMonthlyBudgetUsdInput(sanitizeBudgetInput(e.target.value))}
              placeholder="예: 20"
              disabled={submitLoading}
              required
            />
          </div>

          {submitMessage ? (
            <p className={submitMessage.kind === "success" ? "text-sm text-emerald-600" : "text-sm text-destructive"}>
              {submitMessage.text}
            </p>
          ) : null}

          <div className="flex items-center gap-2">
            <button
              type="submit"
              className="inline-flex h-10 items-center justify-center rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground disabled:cursor-not-allowed disabled:opacity-60"
              disabled={submitLoading}
            >
              {submitLoading ? "등록 중…" : "등록"}
            </button>
            <p className="text-xs text-muted-foreground">
              제출 후에는 보안을 위해 입력한 키 값이 즉시 초기화됩니다.
            </p>
          </div>
        </form>
      </section>
    </div>
  )
}
