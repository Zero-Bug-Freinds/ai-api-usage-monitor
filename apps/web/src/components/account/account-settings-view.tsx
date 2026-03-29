"use client"

import * as React from "react"

import { apiFetch } from "@/lib/api/client-fetch"
import type { SessionResponse } from "@/lib/api/identity/types"

function roleLabel(role: string) {
  if (role === "ADMIN") return "관리자"
  if (role === "USER") return "사용자"
  return role
}

export function AccountSettingsView({ pathSegments }: { pathSegments?: string[] }) {
  const [session, setSession] = React.useState<SessionResponse | null>(null)
  const [loading, setLoading] = React.useState(true)
  const [error, setError] = React.useState<string | null>(null)

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

  const subpath = pathSegments?.length ? pathSegments.join(" / ") : null

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
    </div>
  )
}
