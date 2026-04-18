import type { UsageSummaryResponse } from "@/lib/usage/types"

const STORAGE_PREFIX = "usage-dashboard-summary:v1:"
/** Short TTL to trim repeat 집계 요청 without persisting rates server-side */
const TTL_MS = 120_000

type CachedEnvelope = {
  savedAt: number
  data: UsageSummaryResponse
}

function cacheKey(from: string, to: string, providerSegment: string): string {
  return `${STORAGE_PREFIX}${from}:${to}:${providerSegment}`
}

function safeParse(raw: string | null): CachedEnvelope | null {
  if (!raw) return null
  try {
    const v = JSON.parse(raw) as CachedEnvelope
    if (
      typeof v !== "object" ||
      v === null ||
      typeof v.savedAt !== "number" ||
      typeof v.data !== "object" ||
      v.data === null
    ) {
      return null
    }
    return v
  } catch {
    return null
  }
}

/**
 * Ephemeral client-side cache (sessionStorage) for dashboard summary responses.
 * 증감률은 저장하지 않고, 동일 기간 재조회 시 네트워크만 줄입니다.
 */
export function loadDashboardSummaryCache(
  from: string,
  to: string,
  providerKey: string
): UsageSummaryResponse | null {
  if (typeof sessionStorage === "undefined") return null
  try {
    const env = safeParse(sessionStorage.getItem(cacheKey(from, to, providerKey)))
    if (!env) return null
    if (Date.now() - env.savedAt > TTL_MS) {
      sessionStorage.removeItem(cacheKey(from, to, providerKey))
      return null
    }
    return env.data
  } catch {
    return null
  }
}

export function saveDashboardSummaryCache(
  from: string,
  to: string,
  providerKey: string,
  data: UsageSummaryResponse
): void {
  if (typeof sessionStorage === "undefined") return
  try {
    const payload: CachedEnvelope = { savedAt: Date.now(), data }
    sessionStorage.setItem(cacheKey(from, to, providerKey), JSON.stringify(payload))
  } catch {
    /* quota / private mode */
  }
}
