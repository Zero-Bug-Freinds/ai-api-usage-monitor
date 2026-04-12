import type { PeriodMode } from "@/lib/usage/types"

/**
 * 사용량 대시보드(`UsageDashboard`) 전용. 다른 화면에서는 사용하지 않는다.
 */
export const DASHBOARD_FILTERS_STORAGE_KEY = "eevee.usageWeb.dashboardFilters.v1"

export interface DashboardFilterSnapshot {
  provider: string
  periodMode: PeriodMode
  customFrom: string
  customTo: string
}

const PERIOD_MODES = new Set<PeriodMode>(["today", "7d", "30d", "custom"])

const PROVIDER_VALUES = new Set<string>(["__all__", "OPENAI", "ANTHROPIC", "GOOGLE"])

function isKstIsoDate(s: string): boolean {
  return /^\d{4}-\d{2}-\d{2}$/.test(s)
}

function parseSnapshot(raw: unknown): DashboardFilterSnapshot | null {
  if (raw === null || typeof raw !== "object") return null
  const o = raw as Record<string, unknown>
  const provider = o.provider
  const periodMode = o.periodMode
  const customFrom = o.customFrom
  const customTo = o.customTo

  if (typeof provider !== "string" || !PROVIDER_VALUES.has(provider)) return null
  if (typeof periodMode !== "string" || !PERIOD_MODES.has(periodMode as PeriodMode)) return null
  if (typeof customFrom !== "string" || typeof customTo !== "string") return null
  if (!isKstIsoDate(customFrom) || !isKstIsoDate(customTo)) return null

  return {
    provider,
    periodMode: periodMode as PeriodMode,
    customFrom,
    customTo,
  }
}

export function loadDashboardFilters(): DashboardFilterSnapshot | null {
  if (typeof window === "undefined") return null
  try {
    const raw = window.localStorage.getItem(DASHBOARD_FILTERS_STORAGE_KEY)
    if (!raw) return null
    return parseSnapshot(JSON.parse(raw))
  } catch {
    return null
  }
}

export function saveDashboardFilters(snapshot: DashboardFilterSnapshot): void {
  if (typeof window === "undefined") return
  try {
    window.localStorage.setItem(DASHBOARD_FILTERS_STORAGE_KEY, JSON.stringify(snapshot))
  } catch {
    /* storage full or disabled */
  }
}

export function clearDashboardFilters(): void {
  if (typeof window === "undefined") return
  try {
    window.localStorage.removeItem(DASHBOARD_FILTERS_STORAGE_KEY)
  } catch {
    /* ignore */
  }
}
