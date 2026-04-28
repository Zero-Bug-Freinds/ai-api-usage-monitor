"use client"

import { useCallback, useEffect, useMemo, useState } from "react"
import { Check, Loader2, MailOpen } from "lucide-react"

import { Button, cn } from "@ai-usage/ui"

import type { InAppNotification, InAppNotificationListResponse } from "./notification-types"

const BASE_PATH = process.env.NEXT_PUBLIC_BASE_PATH ?? "/notifications"

function apiPath(path: string): string {
  const base = BASE_PATH.replace(/\/+$/, "")
  const p = path.startsWith("/") ? path : `/${path}`
  return `${base}${p}`
}

function extractMessage(body: unknown): string | null {
  if (typeof body !== "object" || body === null) return null
  if (!("message" in body)) return null
  const msg = (body as { message?: unknown }).message
  return typeof msg === "string" && msg.length > 0 ? msg : null
}

async function fetchNotifications(limit: number, cursor?: string | null): Promise<InAppNotificationListResponse> {
  const url = new URL(apiPath("/api/notification/in-app-notifications"), window.location.origin)
  url.searchParams.set("limit", String(limit))
  if (cursor) url.searchParams.set("cursor", cursor)

  const res = await fetch(url.toString(), { method: "GET", cache: "no-store" })
  if (!res.ok) {
    const body = await res.json().catch(() => null)
    const message = extractMessage(body) ?? "알림을 불러올 수 없습니다"
    throw new Error(message)
  }
  return (await res.json()) as InAppNotificationListResponse
}

async function markRead(id: string): Promise<void> {
  const res = await fetch(apiPath(`/api/notification/in-app-notifications/${encodeURIComponent(id)}/read`), {
    method: "PATCH",
    cache: "no-store",
  })
  if (!res.ok) {
    const body = await res.json().catch(() => null)
    const message = extractMessage(body) ?? "읽음 처리에 실패했습니다"
    throw new Error(message)
  }
}

async function markAllRead(): Promise<void> {
  const res = await fetch(apiPath("/api/notification/in-app-notifications/read-all"), {
    method: "POST",
    cache: "no-store",
  })
  if (!res.ok) {
    const body = await res.json().catch(() => null)
    const message = extractMessage(body) ?? "전체 읽음 처리에 실패했습니다"
    throw new Error(message)
  }
}

type TeamInviteActionMeta = {
  invitationId?: string
  actions?: { acceptPath?: string; rejectPath?: string }
}

function normalizeNotificationServicePath(path: string): string {
  const raw = path.trim()
  if (raw.length === 0) return raw
  const withSlash = raw.startsWith("/") ? raw : `/${raw}`
  // Backward compatibility: older notifications stored `/api/...` but the BFF already targets `.../api`.
  return withSlash.startsWith("/api/") ? withSlash.slice("/api".length) : withSlash
}

function getTeamInviteActions(meta: unknown): { acceptPath: string; rejectPath: string } | null {
  if (typeof meta !== "object" || meta === null) return null
  const m = meta as TeamInviteActionMeta
  const acceptRaw = m.actions?.acceptPath
  const rejectRaw = m.actions?.rejectPath
  if (typeof acceptRaw !== "string" || acceptRaw.length === 0) return null
  if (typeof rejectRaw !== "string" || rejectRaw.length === 0) return null
  const acceptPath = normalizeNotificationServicePath(acceptRaw)
  const rejectPath = normalizeNotificationServicePath(rejectRaw)
  if (acceptPath.length === 0 || rejectPath.length === 0) return null
  return { acceptPath, rejectPath }
}

async function postAction(path: string): Promise<void> {
  const p = normalizeNotificationServicePath(path)
  const res = await fetch(apiPath(`/api/notification${p}`), { method: "POST", cache: "no-store" })
  if (!res.ok) {
    const body = await res.json().catch(() => null)
    const message = extractMessage(body) ?? "요청에 실패했습니다"
    throw new Error(message)
  }
}

function formatKoreanDate(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleString("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  })
}

export function NotificationsPage() {
  const [items, setItems] = useState<InAppNotification[]>([])
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [busyActionId, setBusyActionId] = useState<string | null>(null)
  const [busyAll, setBusyAll] = useState(false)

  const unreadCount = useMemo(() => items.filter((n) => !n.readAt).length, [items])

  const loadFirst = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await fetchNotifications(30)
      setItems(res.items)
      setNextCursor(res.nextCursor)
    } catch (e) {
      setError(e instanceof Error ? e.message : "알림을 불러올 수 없습니다")
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadFirst()
  }, [loadFirst])

  const onLoadMore = useCallback(async () => {
    if (!nextCursor) return
    setLoadingMore(true)
    setError(null)
    try {
      const res = await fetchNotifications(30, nextCursor)
      setItems((prev) => {
        const seen = new Set(prev.map((p) => p.id))
        const merged = [...prev]
        for (const n of res.items) {
          if (!seen.has(n.id)) merged.push(n)
        }
        return merged
      })
      setNextCursor(res.nextCursor)
    } catch (e) {
      setError(e instanceof Error ? e.message : "알림을 더 불러올 수 없습니다")
    } finally {
      setLoadingMore(false)
    }
  }, [nextCursor])

  const onMarkRead = useCallback(async (id: string) => {
    setBusyId(id)
    setError(null)
    try {
      await markRead(id)
      setItems((prev) => prev.map((n) => (n.id === id ? { ...n, readAt: new Date().toISOString() } : n)))
    } catch (e) {
      setError(e instanceof Error ? e.message : "읽음 처리에 실패했습니다")
    } finally {
      setBusyId(null)
    }
  }, [])

  const onInviteAction = useCallback(
    async (notificationId: string, path: string) => {
      setBusyActionId(notificationId)
      setError(null)
      try {
        await postAction(path)
        await markRead(notificationId)
        setItems((prev) =>
          prev.map((n) =>
            n.id === notificationId ? { ...n, readAt: new Date().toISOString() } : n,
          ),
        )
        await loadFirst()
      } catch (e) {
        setError(e instanceof Error ? e.message : "요청에 실패했습니다")
      } finally {
        setBusyActionId(null)
      }
    },
    [loadFirst],
  )

  const onMarkAllRead = useCallback(async () => {
    setBusyAll(true)
    setError(null)
    try {
      await markAllRead()
      setItems((prev) => prev.map((n) => (n.readAt ? n : { ...n, readAt: new Date().toISOString() })))
    } catch (e) {
      setError(e instanceof Error ? e.message : "전체 읽음 처리에 실패했습니다")
    } finally {
      setBusyAll(false)
    }
  }, [])

  return (
    <div className="space-y-6">
      <header className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">알림</h1>
          <p className="mt-1 text-sm text-muted-foreground">새 알림은 우측 하단 토스트로도 표시됩니다.</p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="secondary" onClick={() => void loadFirst()} disabled={loading || loadingMore}>
            새로고침
          </Button>
          <Button onClick={() => void onMarkAllRead()} disabled={busyAll || unreadCount === 0}>
            {busyAll ? <Loader2 className="mr-2 size-4 animate-spin" aria-hidden /> : <Check className="mr-2 size-4" aria-hidden />}
            전체 읽음
          </Button>
        </div>
      </header>

      {error ? (
        <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {error}
        </div>
      ) : null}

      {loading ? (
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="size-4 animate-spin" aria-hidden />
          불러오는 중…
        </div>
      ) : items.length === 0 ? (
        <div className="rounded-xl border bg-card p-10 text-center">
          <MailOpen className="mx-auto size-10 text-muted-foreground" aria-hidden />
          <p className="mt-3 text-sm text-muted-foreground">아직 알림이 없습니다.</p>
        </div>
      ) : (
        <div className="space-y-3">
          {items.map((n) => {
            const unread = !n.readAt
            const busy = busyId === n.id
            const busyAction = busyActionId === n.id
            const inviteActions =
              n.type === "team:TEAM_INVITE_CREATED" ? getTeamInviteActions(n.meta) : null
            return (
              <article
                key={n.id}
                className={cn(
                  "rounded-xl border bg-card p-4 shadow-sm transition-colors",
                  unread ? "border-primary/30" : "border-border"
                )}
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <h2 className={cn("truncate text-sm font-semibold", unread ? "text-foreground" : "text-foreground/80")}>
                        {n.title}
                      </h2>
                      {n.type ? (
                        <span className="rounded-full border bg-muted px-2 py-0.5 text-[11px] text-muted-foreground">{n.type}</span>
                      ) : null}
                      {unread ? (
                        <span className="rounded-full bg-primary/10 px-2 py-0.5 text-[11px] font-medium text-primary">NEW</span>
                      ) : null}
                    </div>
                    <p className="mt-2 whitespace-pre-wrap text-sm text-foreground/90">{n.body}</p>
                    <p className="mt-3 text-xs text-muted-foreground">{formatKoreanDate(n.createdAt)}</p>
                    {inviteActions ? (
                      <div className="mt-4 flex flex-wrap gap-2">
                        <Button
                          size="sm"
                          onClick={() => void onInviteAction(n.id, inviteActions.acceptPath)}
                          disabled={!unread || busyAction}
                        >
                          {busyAction ? <Loader2 className="mr-2 size-4 animate-spin" aria-hidden /> : null}
                          수락
                        </Button>
                        <Button
                          size="sm"
                          variant="secondary"
                          onClick={() => void onInviteAction(n.id, inviteActions.rejectPath)}
                          disabled={!unread || busyAction}
                        >
                          {busyAction ? <Loader2 className="mr-2 size-4 animate-spin" aria-hidden /> : null}
                          거절
                        </Button>
                      </div>
                    ) : null}
                  </div>
                  <div className="shrink-0">
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => void onMarkRead(n.id)}
                      disabled={!unread || busy}
                    >
                      {busy ? <Loader2 className="mr-2 size-4 animate-spin" aria-hidden /> : <Check className="mr-2 size-4" aria-hidden />}
                      읽음
                    </Button>
                  </div>
                </div>
              </article>
            )
          })}

          {nextCursor ? (
            <div className="flex justify-center pt-2">
              <Button variant="secondary" onClick={() => void onLoadMore()} disabled={loadingMore}>
                {loadingMore ? <Loader2 className="mr-2 size-4 animate-spin" aria-hidden /> : null}
                더 보기
              </Button>
            </div>
          ) : null}
        </div>
      )}
    </div>
  )
}

