import * as React from "react"
import { DASHBOARD_API_KEY_ALL, DASHBOARD_API_KEY_NONE } from "@/lib/usage/dashboard-api-key-constants"
import { DASHBOARD_PROVIDER_ALL } from "@/lib/usage/dashboard-provider-api-keys"
import {
  defaultPeriod,
  parseStoredPeriod,
  presetRangeForMode,
  type PeriodMode,
  type StoredDashboardPeriod,
} from "@/lib/usage/usage-filter-period"
import { formatKstIsoDate } from "@/lib/usage/kst-dates"

export type UsageFilterScreen = "dashboard" | "team" | "usagelog"

export type UsageFilterMode =
  | "personal-keys"
  | "team-my-usage"
  | "team-dashboard"
  | "team-member"
  | "log-personal"
  | "log-team"

export type UsageFilterSettings = {
  provider: string
  period: StoredDashboardPeriod
  apiKeyId: string
  /** e.g. usagelog team tab — persisted team picker */
  teamId?: string
}

const STORAGE_VERSION = "v1"

/** Legacy keys from usage-dashboard.tsx (pre–namespaced storage). */
const LEGACY_DASHBOARD_PROVIDER_KEY = "usage-dashboard:provider:v1"
const LEGACY_DASHBOARD_PERIOD_KEY = "usage-dashboard:period:v1"
const LEGACY_PERSONAL_API_KEY_KEY = "PERSONAL_DASHBOARD_SELECTED_API_KEY_ID"
const LEGACY_TEAM_MY_USAGE_PROVIDER_KEY = "TEAM_MY_USAGE_PROVIDER"
const LEGACY_TEAM_MY_USAGE_PERIOD_KEY = "TEAM_MY_USAGE_PERIOD"
const LEGACY_TEAM_MY_USAGE_API_KEY_KEY = "TEAM_MY_USAGE_SELECTED_API_KEY_ID"

export function buildUsageFilterStorageKey(screen: UsageFilterScreen, mode: UsageFilterMode): string {
  return `usage:${STORAGE_VERSION}:${screen}:${mode}:filterSettings`
}

/** Aligns with usage-log-panel `LOG_PROVIDER_ALL` / personal API key sentinel. */
export const USAGE_LOG_FILTER_PROVIDER_ALL = "__all__"
export const USAGE_LOG_FILTER_API_KEY_ALL_PERSONAL = "__all__"

export function defaultUsageFilterSettings(todayKst?: string): UsageFilterSettings {
  const t = todayKst ?? formatKstIsoDate()
  return {
    provider: DASHBOARD_PROVIDER_ALL,
    period: defaultPeriod(t),
    apiKeyId: DASHBOARD_API_KEY_ALL,
  }
}

export function defaultSettingsFor(
  screen: UsageFilterScreen,
  mode: UsageFilterMode,
  todayKst?: string
): UsageFilterSettings {
  const t = todayKst ?? formatKstIsoDate()
  if (screen === "team" && mode === "team-member") {
    const r = presetRangeForMode("7d", t)
    return {
      provider: DASHBOARD_PROVIDER_ALL,
      period: { mode: "7d", from: r.from, to: r.to },
      apiKeyId: DASHBOARD_API_KEY_ALL,
    }
  }
  if (screen === "usagelog") {
    if (mode === "log-personal") {
      return {
        provider: USAGE_LOG_FILTER_PROVIDER_ALL,
        period: defaultPeriod(t),
        apiKeyId: USAGE_LOG_FILTER_API_KEY_ALL_PERSONAL,
      }
    }
    if (mode === "log-team") {
      return {
        provider: USAGE_LOG_FILTER_PROVIDER_ALL,
        period: defaultPeriod(t),
        apiKeyId: DASHBOARD_API_KEY_ALL,
      }
    }
  }
  return defaultUsageFilterSettings(t)
}

function readPersonalApiKeyFromLegacy(): string {
  if (typeof sessionStorage === "undefined") return DASHBOARD_API_KEY_ALL
  try {
    const raw = sessionStorage.getItem(LEGACY_PERSONAL_API_KEY_KEY)
    if (raw == null) return DASHBOARD_API_KEY_ALL
    const trimmed = raw.trim()
    if (trimmed.length === 0 || trimmed === DASHBOARD_API_KEY_ALL) return DASHBOARD_API_KEY_ALL
    if (trimmed === DASHBOARD_API_KEY_NONE) return DASHBOARD_API_KEY_NONE
    return trimmed
  } catch {
    return DASHBOARD_API_KEY_ALL
  }
}

function readTeamMyUsageApiKeyFromLegacy(): string {
  if (typeof sessionStorage === "undefined") return DASHBOARD_API_KEY_ALL
  try {
    const raw = sessionStorage.getItem(LEGACY_TEAM_MY_USAGE_API_KEY_KEY)
    if (raw == null) return DASHBOARD_API_KEY_ALL
    const trimmed = raw.trim()
    if (trimmed.length === 0 || trimmed === DASHBOARD_API_KEY_ALL) return DASHBOARD_API_KEY_ALL
    return trimmed
  } catch {
    return DASHBOARD_API_KEY_ALL
  }
}

function clearLegacyPersonalDashboardKeys() {
  if (typeof sessionStorage === "undefined") return
  try {
    sessionStorage.removeItem(LEGACY_DASHBOARD_PROVIDER_KEY)
    sessionStorage.removeItem(LEGACY_DASHBOARD_PERIOD_KEY)
    sessionStorage.removeItem(LEGACY_PERSONAL_API_KEY_KEY)
  } catch {
    /* ignore */
  }
}

function clearLegacyTeamMyUsageKeys() {
  if (typeof sessionStorage === "undefined") return
  try {
    sessionStorage.removeItem(LEGACY_TEAM_MY_USAGE_PROVIDER_KEY)
    sessionStorage.removeItem(LEGACY_TEAM_MY_USAGE_PERIOD_KEY)
    sessionStorage.removeItem(LEGACY_TEAM_MY_USAGE_API_KEY_KEY)
  } catch {
    /* ignore */
  }
}

function migrateDashboardLegacyIfNeeded(
  mode: UsageFilterMode,
  key: string,
  todayKst: string
): UsageFilterSettings | null {
  if (mode !== "personal-keys" && mode !== "team-my-usage") return null
  if (typeof sessionStorage === "undefined") return null
  try {
    if (sessionStorage.getItem(key)) return null
    let provider = DASHBOARD_PROVIDER_ALL
    let periodJson: string | null = null
    let apiKeyId = DASHBOARD_API_KEY_ALL

    if (mode === "personal-keys") {
      const p = sessionStorage.getItem(LEGACY_DASHBOARD_PROVIDER_KEY)
      if (p) provider = p
      periodJson = sessionStorage.getItem(LEGACY_DASHBOARD_PERIOD_KEY)
      apiKeyId = readPersonalApiKeyFromLegacy()
    } else {
      const p = sessionStorage.getItem(LEGACY_TEAM_MY_USAGE_PROVIDER_KEY)
      if (p) provider = p
      periodJson = sessionStorage.getItem(LEGACY_TEAM_MY_USAGE_PERIOD_KEY)
      apiKeyId = readTeamMyUsageApiKeyFromLegacy()
    }

    const period = parseStoredPeriod(periodJson, todayKst)
    const migrated: UsageFilterSettings = { provider, period, apiKeyId }
    sessionStorage.setItem(key, JSON.stringify(migrated))
    if (mode === "personal-keys") clearLegacyPersonalDashboardKeys()
    else clearLegacyTeamMyUsageKeys()
    return migrated
  } catch {
    return null
  }
}

function parseFilterSettingsJson(
  raw: string | null,
  screen: UsageFilterScreen,
  mode: UsageFilterMode,
  todayKst: string
): UsageFilterSettings | null {
  if (!raw) return null
  const base = defaultSettingsFor(screen, mode, todayKst)
  try {
    const o = JSON.parse(raw) as Partial<UsageFilterSettings>
    const provider = typeof o.provider === "string" && o.provider.length > 0 ? o.provider : base.provider
    const period =
      o.period && typeof o.period === "object"
        ? parseStoredPeriod(JSON.stringify(o.period), todayKst)
        : base.period
    const apiKeyId =
      typeof o.apiKeyId === "string" && o.apiKeyId.length > 0 ? o.apiKeyId : base.apiKeyId
    const teamId = typeof o.teamId === "string" && o.teamId.length > 0 ? o.teamId : undefined
    return teamId !== undefined ? { provider, period, apiKeyId, teamId } : { provider, period, apiKeyId }
  } catch {
    return null
  }
}

export function readUsageFilterSettings(
  screen: UsageFilterScreen,
  mode: UsageFilterMode,
  todayKst?: string
): UsageFilterSettings {
  const t = todayKst ?? formatKstIsoDate()
  const key = buildUsageFilterStorageKey(screen, mode)
  if (typeof sessionStorage === "undefined") {
    return defaultSettingsFor(screen, mode, t)
  }
  try {
    const migrated = migrateDashboardLegacyIfNeeded(mode, key, t)
    if (migrated) return migrated
    const raw = sessionStorage.getItem(key)
    const parsed = parseFilterSettingsJson(raw, screen, mode, t)
    return parsed ?? defaultSettingsFor(screen, mode, t)
  } catch {
    return defaultSettingsFor(screen, mode, t)
  }
}

export function writeUsageFilterSettings(
  screen: UsageFilterScreen,
  mode: UsageFilterMode,
  settings: UsageFilterSettings
): void {
  if (typeof sessionStorage === "undefined") return
  try {
    const key = buildUsageFilterStorageKey(screen, mode)
    sessionStorage.setItem(key, JSON.stringify(settings))
  } catch {
    /* quota / private mode */
  }
}

export type UseFilterStorageOptions = {
  /** After client mount; skip read/write until true to align with SSR */
  clientReady: boolean
  /** Optional today override (tests) */
  todayKst?: string
}

/**
 * Session-scoped filter settings with key `usage:v1:{screen}:{mode}:filterSettings`.
 * Persists on each patch; initial read runs in layout after clientReady.
 */
export function useFilterStorage(
  screen: UsageFilterScreen,
  mode: UsageFilterMode,
  options: UseFilterStorageOptions
): {
  settings: UsageFilterSettings
  patch: (partial: Partial<UsageFilterSettings>) => void
  replace: (next: UsageFilterSettings) => void
} {
  const { clientReady, todayKst: todayOverride } = options
  const todayKst = todayOverride ?? formatKstIsoDate()
  const key = React.useMemo(() => buildUsageFilterStorageKey(screen, mode), [screen, mode])

  const [settings, setSettings] = React.useState<UsageFilterSettings>(() => defaultSettingsFor(screen, mode, todayKst))

  React.useLayoutEffect(() => {
    if (!clientReady || typeof sessionStorage === "undefined") return
    const t = todayOverride ?? formatKstIsoDate()
    setSettings(readUsageFilterSettings(screen, mode, t))
  }, [clientReady, screen, mode, key, todayOverride])

  const patch = React.useCallback(
    (partial: Partial<UsageFilterSettings>) => {
      setSettings((prev) => {
        const next: UsageFilterSettings = {
          ...prev,
          ...partial,
          period: partial.period ? { ...prev.period, ...partial.period } : prev.period,
        }
        writeUsageFilterSettings(screen, mode, next)
        return next
      })
    },
    [screen, mode]
  )

  const replace = React.useCallback(
    (next: UsageFilterSettings) => {
      setSettings(next)
      writeUsageFilterSettings(screen, mode, next)
    },
    [screen, mode]
  )

  return { settings, patch, replace }
}

export function patchPeriodMode(
  nextMode: PeriodMode,
  todayKst: string,
  prev: StoredDashboardPeriod
): StoredDashboardPeriod {
  if (nextMode === "custom") {
    return { mode: "custom", from: prev.from, to: prev.to }
  }
  const { from, to } = presetRangeForMode(nextMode, todayKst)
  return { mode: nextMode, from, to }
}

const USAGE_FILTER_SESSION_KEY_PREFIX = `usage:${STORAGE_VERSION}:`

/** Clears all `usage:v1:*:filterSettings` keys (optional complement to full `sessionStorage.clear()` on logout). */
export function clearUsageFilterSessionKeys(): void {
  if (typeof sessionStorage === "undefined") return
  try {
    const toRemove: string[] = []
    for (let i = 0; i < sessionStorage.length; i++) {
      const k = sessionStorage.key(i)
      if (k && k.startsWith(USAGE_FILTER_SESSION_KEY_PREFIX) && k.endsWith(":filterSettings")) {
        toRemove.push(k)
      }
    }
    for (const k of toRemove) {
      sessionStorage.removeItem(k)
    }
  } catch {
    /* ignore */
  }
}
