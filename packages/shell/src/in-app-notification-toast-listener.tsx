"use client"

import { useEffect, useMemo, useRef } from "react"

import { useInAppToast } from "./in-app-toast-provider"

/** Same-origin path at web-edge; not tied to any app `basePath`. */
export const IN_APP_NOTIFICATIONS_POLL_URL =
  "/notifications/api/notification/in-app-notifications?limit=10" as const

type InAppNotification = {
  id: string
  readAt: string | null
  title: string
  body: string
  createdAt: string
}

type InAppNotificationListResponse = {
  items: InAppNotification[]
  nextCursor: string | null
}

function isInAppNotificationListResponse(v: unknown): v is InAppNotificationListResponse {
  if (typeof v !== "object" || v === null) return false
  if (!("items" in v)) return false
  return Array.isArray((v as { items?: unknown }).items)
}

function dedupeNewestFirst(items: InAppNotification[]): InAppNotification[] {
  const seen = new Set<string>()
  const out: InAppNotification[] = []
  for (const n of items) {
    if (seen.has(n.id)) continue
    seen.add(n.id)
    out.push(n)
  }
  return out
}

async function fetchLatest(): Promise<InAppNotification[]> {
  const res = await fetch(IN_APP_NOTIFICATIONS_POLL_URL, {
    method: "GET",
    cache: "no-store",
  })
  if (!res.ok) return []
  const body: unknown = await res.json().catch(() => null)
  if (!isInAppNotificationListResponse(body)) return []
  return body.items
}

export function InAppNotificationToastListener() {
  const { pushToast } = useInAppToast()
  const knownIds = useRef<Set<string>>(new Set())
  const bootstrapped = useRef(false)

  const pollMs = useMemo(() => {
    const raw = process.env.NEXT_PUBLIC_NOTIFICATION_POLL_MS
    const v = raw ? Number(raw) : 10_000
    return Number.isFinite(v) && v >= 2_000 ? v : 10_000
  }, [])

  useEffect(() => {
    let alive = true

    async function tick() {
      const latest = await fetchLatest()
      if (!alive) return

      const sorted = dedupeNewestFirst(latest).slice().sort((a, b) => {
        const at = new Date(a.createdAt).getTime()
        const bt = new Date(b.createdAt).getTime()
        return bt - at
      })

      if (!bootstrapped.current) {
        for (const n of sorted) knownIds.current.add(n.id)
        bootstrapped.current = true
        return
      }

      const unseenUnread = sorted.filter((n) => !n.readAt && !knownIds.current.has(n.id))
      for (const n of unseenUnread.slice(0, 3)) {
        knownIds.current.add(n.id)
        pushToast({
          id: `notif:${n.id}`,
          title: n.title,
          body: n.body,
          ttlMs: 6000,
        })
      }

      for (const n of sorted) knownIds.current.add(n.id)
    }

    const start = async () => {
      await tick()
      const t = window.setInterval(() => void tick(), pollMs)
      return () => window.clearInterval(t)
    }

    let cleanup: (() => void) | null = null
    void start().then((c) => {
      cleanup = c
    })

    return () => {
      alive = false
      cleanup?.()
    }
  }, [pollMs, pushToast])

  return null
}
