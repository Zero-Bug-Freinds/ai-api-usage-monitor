"use client"

import * as React from "react"

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
  return d.toLocaleString("ko-KR")
}

export function AccountSettingsView({ pathSegments }: { pathSegments?: string[] }) {
  const [session, setSession] = React.useState<SessionResponse | null>(null)
  const [loading, setLoading] = React.useState(true)
  const [error, setError] = React.useState<string | null>(null)

  const [provider, setProvider] = React.useState<ExternalKeyProvider>("OPENAI")
  const [alias, setAlias] = React.useState(() => defaultAlias("OPENAI"))
  const [externalKey, setExternalKey] = React.useState("")
  const [aliasTouched, setAliasTouched] = React.useState(false)
  const [submitLoading, setSubmitLoading] = React.useState(false)
  const [submitMessage, setSubmitMessage] = React.useState<{ kind: "success" | "error"; text: string } | null>(
    null
  )

  /** `null`은 아직 목록을 받기 전(세션 직후 첫 프레임 포함). */
  const [externalKeys, setExternalKeys] = React.useState<ExternalKeySummary[] | null>(null)
  const [keysLoading, setKeysLoading] = React.useState(false)
  const [keysError, setKeysError] = React.useState<string | null>(null)

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

    if (!aliasTrimmed) {
      setSubmitMessage({ kind: "error", text: "별칭(alias)은 필수입니다" })
      return
    }
    if (!externalKeyTrimmed) {
      setSubmitMessage({ kind: "error", text: "외부 API Key는 필수입니다" })
      return
    }

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
          }),
        },
        { authRequired: true }
      )

      const apiResponse = asApiResponse(json)

      if (response.ok && apiResponse?.success) {
        setSubmitMessage({ kind: "success", text: apiResponse.message || "등록되었습니다" })
        setAliasTouched(false)
        setAlias(defaultAlias(provider))
        void loadExternalKeys()
      } else {
        setSubmitMessage({ kind: "error", text: apiResponse?.message || "등록에 실패했습니다" })
      }
    } catch {
      setSubmitMessage({ kind: "error", text: "등록에 실패했습니다" })
    } finally {
      setExternalKey("")
      setSubmitLoading(false)
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
            <p className="text-sm text-muted-foreground">Provider·별칭·등록일만 표시됩니다. 키 값은 저장되지 않거나 암호화되어 있습니다.</p>
          </div>

          {keysLoading || externalKeys === null ? (
            <p className="text-sm text-muted-foreground">목록을 불러오는 중…</p>
          ) : null}
          {keysError && !keysLoading && externalKeys !== null ? <p className="text-sm text-destructive">{keysError}</p> : null}

          {!keysLoading && !keysError && externalKeys !== null && externalKeys.length === 0 ? (
            <p className="rounded-md border border-dashed border-border bg-muted/20 px-4 py-6 text-center text-sm text-muted-foreground">
              등록된 외부 키가 없습니다. 아래에서 추가할 수 있습니다.
            </p>
          ) : null}

          {!keysLoading && !keysError && externalKeys !== null && externalKeys.length > 0 ? (
            <ul className="divide-y divide-border rounded-md border border-border">
              {externalKeys.map((row) => (
                <li key={row.id} className="flex flex-col gap-1 px-4 py-3 text-sm sm:flex-row sm:items-center sm:justify-between">
                  <div className="font-medium">
                    <span className="text-foreground">{providerLabel(row.provider)}</span>
                    <span className="mx-2 text-muted-foreground">·</span>
                    <span className="text-foreground">{row.alias}</span>
                  </div>
                  <div className="text-muted-foreground tabular-nums">{formatCreatedAt(row.createdAt)}</div>
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
            <input
              id="external-key-value"
              className="h-10 rounded-md border border-input bg-background px-3 text-sm"
              type="password"
              value={externalKey}
              onChange={(e) => setExternalKey(e.target.value)}
              placeholder="키를 입력하세요"
              autoComplete="off"
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
