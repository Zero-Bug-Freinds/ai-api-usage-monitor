"use client"

import * as React from "react"
import Link from "next/link"

import { usageEntryPublicPath } from "@ai-usage/shell"

import type { ApiResponse, SessionResponse } from "@/lib/api/identity/types"

function parseSessionPayload(json: unknown): SessionResponse | null {
  if (!json || typeof json !== "object") return null
  const body = json as ApiResponse<SessionResponse>
  if (!body.success || !body.data || body.data.authenticated !== true) return null
  return body.data
}

export function LandingHomeWithSession() {
  const [session, setSession] = React.useState<SessionResponse | null>(null)
  const [loading, setLoading] = React.useState(true)

  React.useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const res = await fetch("/api/auth/session", {
          credentials: "include",
          cache: "no-store",
        })
        let json: unknown = null
        try {
          json = await res.json()
        } catch {
          json = null
        }
        if (!cancelled) {
          setSession(parseSessionPayload(json))
        }
      } catch {
        if (!cancelled) setSession(null)
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  const dashboardHref = usageEntryPublicPath()
  const authenticated = Boolean(session)

  return (
    <>
      <header className="border-b border-zinc-200 bg-white px-6 py-4 dark:border-zinc-800 dark:bg-zinc-950">
        <div className="mx-auto flex max-w-5xl items-center justify-between gap-4">
          <Link
            href="/"
            className="text-lg font-semibold tracking-tight text-zinc-900 dark:text-zinc-50"
          >
            AI API Usage Monitor
          </Link>
          <nav className="flex items-center gap-3 text-sm font-medium">
            {loading ? (
              <span className="h-9 w-40 animate-pulse rounded-md bg-zinc-200 dark:bg-zinc-800" aria-hidden />
            ) : authenticated ? (
              <>
                <Link
                  href={dashboardHref}
                  className="rounded-md px-3 py-2 text-zinc-700 transition-colors hover:bg-zinc-100 hover:text-zinc-900 dark:text-zinc-300 dark:hover:bg-zinc-800 dark:hover:text-white"
                >
                  대시보드
                </Link>
                <Link
                  href="/settings"
                  className="rounded-md bg-zinc-900 px-3 py-2 text-white transition-colors hover:bg-zinc-800 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-white"
                >
                  설정
                </Link>
              </>
            ) : (
              <>
                <Link
                  href="/login"
                  className="rounded-md px-3 py-2 text-zinc-700 transition-colors hover:bg-zinc-100 hover:text-zinc-900 dark:text-zinc-300 dark:hover:bg-zinc-800 dark:hover:text-white"
                >
                  로그인
                </Link>
                <Link
                  href="/signup"
                  className="rounded-md bg-zinc-900 px-3 py-2 text-white transition-colors hover:bg-zinc-800 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-white"
                >
                  회원가입
                </Link>
              </>
            )}
          </nav>
        </div>
      </header>
      <main className="mx-auto flex w-full max-w-5xl flex-1 flex-col justify-center px-6 py-16">
        <h1 className="text-3xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
          사용량과 비용을 한곳에서
        </h1>
        <p className="mt-4 max-w-xl text-lg leading-relaxed text-zinc-600 dark:text-zinc-400">
          조직·팀 단위로 API 호출을 추적하고, 대시보드에서 집계·알림을 확인할 수 있습니다. 계정이
          있다면 로그인 후 대시보드로 이동하세요.
        </p>
        <div className="mt-8 flex flex-wrap gap-3">
          {loading ? (
            <>
              <span className="inline-flex h-11 w-24 animate-pulse rounded-md bg-zinc-200 dark:bg-zinc-800" />
              <span className="inline-flex h-11 w-28 animate-pulse rounded-md bg-zinc-200 dark:bg-zinc-800" />
            </>
          ) : authenticated ? (
            <>
              <Link
                href={dashboardHref}
                className="inline-flex h-11 items-center justify-center rounded-md bg-zinc-900 px-5 text-sm font-medium text-white transition-colors hover:bg-zinc-800 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-white"
              >
                대시보드로 이동
              </Link>
              <Link
                href="/settings"
                className="inline-flex h-11 items-center justify-center rounded-md border border-zinc-300 bg-white px-5 text-sm font-medium text-zinc-900 transition-colors hover:bg-zinc-50 dark:border-zinc-700 dark:bg-zinc-950 dark:text-zinc-50 dark:hover:bg-zinc-900"
              >
                설정
              </Link>
            </>
          ) : (
            <>
              <Link
                href="/login"
                className="inline-flex h-11 items-center justify-center rounded-md bg-zinc-900 px-5 text-sm font-medium text-white transition-colors hover:bg-zinc-800 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-white"
              >
                로그인
              </Link>
              <Link
                href="/signup"
                className="inline-flex h-11 items-center justify-center rounded-md border border-zinc-300 bg-white px-5 text-sm font-medium text-zinc-900 transition-colors hover:bg-zinc-50 dark:border-zinc-700 dark:bg-zinc-950 dark:text-zinc-50 dark:hover:bg-zinc-900"
              >
                회원가입
              </Link>
            </>
          )}
        </div>
      </main>
    </>
  )
}
