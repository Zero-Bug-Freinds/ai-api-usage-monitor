import { afterEach, beforeEach, describe, expect, it } from "vitest"
import { DASHBOARD_API_KEY_ALL } from "@/lib/usage/dashboard-api-key-constants"
import { DASHBOARD_PROVIDER_ALL } from "@/lib/usage/dashboard-provider-api-keys"
import {
  buildUsageFilterStorageKey,
  clearUsageFilterSessionKeys,
  defaultSettingsFor,
  readUsageFilterSettings,
  writeUsageFilterSettings,
  type UsageFilterSettings,
} from "@/lib/usage/use-filter-storage"

function installSessionStorageMock() {
  const store = new Map<string, string>()
  const api: Storage = {
    get length() {
      return store.size
    },
    clear() {
      store.clear()
    },
    getItem(key: string) {
      return store.has(key) ? store.get(key)! : null
    },
    key(index: number) {
      return [...store.keys()][index] ?? null
    },
    removeItem(key: string) {
      store.delete(key)
    },
    setItem(key: string, value: string) {
      store.set(key, value)
    },
  }
  Object.defineProperty(globalThis, "sessionStorage", { value: api, configurable: true })
  return store
}

const LEGACY_DASHBOARD_PROVIDER_KEY = "usage-dashboard:provider:v1"
const LEGACY_DASHBOARD_PERIOD_KEY = "usage-dashboard:period:v1"
const LEGACY_PERSONAL_API_KEY_KEY = "PERSONAL_DASHBOARD_SELECTED_API_KEY_ID"
const LEGACY_TEAM_MY_USAGE_PROVIDER_KEY = "TEAM_MY_USAGE_PROVIDER"
const LEGACY_TEAM_MY_USAGE_PERIOD_KEY = "TEAM_MY_USAGE_PERIOD"
const LEGACY_TEAM_MY_USAGE_API_KEY_KEY = "TEAM_MY_USAGE_SELECTED_API_KEY_ID"

describe("use-filter-storage helpers", () => {
  let store: Map<string, string>

  beforeEach(() => {
    store = installSessionStorageMock()
  })

  afterEach(() => {
    Reflect.deleteProperty(globalThis, "sessionStorage")
  })

  it("uses distinct session keys per screen/mode", () => {
    const a = buildUsageFilterStorageKey("dashboard", "personal-keys")
    const b = buildUsageFilterStorageKey("dashboard", "team-my-usage")
    expect(a).not.toBe(b)
    expect(a).toBe("usage:v1:dashboard:personal-keys:filterSettings")
    expect(b).toBe("usage:v1:dashboard:team-my-usage:filterSettings")
  })

  it("round-trips JSON settings", () => {
    const today = "2026-05-01"
    const written: UsageFilterSettings = {
      provider: "OPENAI",
      period: { mode: "7d", from: "2026-04-25", to: today },
      apiKeyId: "key-1",
    }
    writeUsageFilterSettings("team", "team-dashboard", written)
    const read = readUsageFilterSettings("team", "team-dashboard", today)
    expect(read).toEqual(written)
    expect(store.size).toBe(1)
  })

  it("isolates values between modes", () => {
    const today = "2026-05-01"
    writeUsageFilterSettings("usagelog", "log-personal", {
      provider: "__all__",
      period: { mode: "today", from: today, to: today },
      apiKeyId: "__all__",
    })
    writeUsageFilterSettings("usagelog", "log-team", {
      provider: "GOOGLE",
      period: { mode: "30d", from: "2026-04-02", to: today },
      apiKeyId: DASHBOARD_API_KEY_ALL,
      teamId: "team-99",
    })
    const p = readUsageFilterSettings("usagelog", "log-personal", today)
    const t = readUsageFilterSettings("usagelog", "log-team", today)
    expect(p.provider).toBe("__all__")
    expect(t.provider).toBe("GOOGLE")
    expect(t.teamId).toBe("team-99")
    expect(p.teamId).toBeUndefined()
  })

  it("migrates legacy personal dashboard keys once and removes legacy entries", () => {
    const today = "2026-05-12"
    sessionStorage.setItem(LEGACY_DASHBOARD_PROVIDER_KEY, "ANTHROPIC")
    sessionStorage.setItem(
      LEGACY_DASHBOARD_PERIOD_KEY,
      JSON.stringify({ mode: "30d", from: "2026-04-13", to: today }),
    )
    sessionStorage.setItem(LEGACY_PERSONAL_API_KEY_KEY, "legacy-key")

    const migrated = readUsageFilterSettings("dashboard", "personal-keys", today)
    expect(migrated.provider).toBe("ANTHROPIC")
    expect(migrated.period.mode).toBe("30d")
    expect(migrated.apiKeyId).toBe("legacy-key")

    expect(sessionStorage.getItem(LEGACY_DASHBOARD_PROVIDER_KEY)).toBeNull()
    expect(sessionStorage.getItem(LEGACY_DASHBOARD_PERIOD_KEY)).toBeNull()
    expect(sessionStorage.getItem(LEGACY_PERSONAL_API_KEY_KEY)).toBeNull()

    // Team-my legacy keys must remain until team-my migration runs
    sessionStorage.setItem(LEGACY_TEAM_MY_USAGE_PROVIDER_KEY, "GOOGLE")
    sessionStorage.setItem(
      LEGACY_TEAM_MY_USAGE_PERIOD_KEY,
      JSON.stringify({ mode: "7d", from: "2026-05-06", to: today }),
    )
    sessionStorage.setItem(LEGACY_TEAM_MY_USAGE_API_KEY_KEY, "team-key")

    const teamMy = readUsageFilterSettings("dashboard", "team-my-usage", today)
    expect(teamMy.provider).toBe("GOOGLE")
    expect(teamMy.period.mode).toBe("7d")
    expect(teamMy.apiKeyId).toBe("team-key")
    expect(sessionStorage.getItem(LEGACY_TEAM_MY_USAGE_PROVIDER_KEY)).toBeNull()
  })

  it("clearUsageFilterSessionKeys removes only usage:v1:*:filterSettings keys", () => {
    sessionStorage.setItem("usage:v1:dashboard:personal-keys:filterSettings", "{}")
    sessionStorage.setItem("other:app:key", "keep")
    clearUsageFilterSessionKeys()
    expect(sessionStorage.getItem("usage:v1:dashboard:personal-keys:filterSettings")).toBeNull()
    expect(sessionStorage.getItem("other:app:key")).toBe("keep")
  })

  it("defaultSettingsFor usagelog uses log sentinels", () => {
    const today = "2026-01-15"
    const p = defaultSettingsFor("usagelog", "log-personal", today)
    expect(p.provider).toBe("__all__")
    expect(p.apiKeyId).toBe("__all__")
    const t = defaultSettingsFor("usagelog", "log-team", today)
    expect(t.apiKeyId).toBe(DASHBOARD_API_KEY_ALL)
    expect(t.provider).toBe("__all__")
    const d = defaultSettingsFor("dashboard", "personal-keys", today)
    expect(d.provider).toBe(DASHBOARD_PROVIDER_ALL)
  })
})

/** Browser QA: 상세·고급 필터 블록은 리팩터 대상 외 — 배포 전 수동 확인. */
describe.skip("verify-usage-log-advanced-filters (manual)", () => {
  it("상세 필터 패널·추론/성공·모델·OpenAI 드로어·페이지네이션·쿼리 연동", () => {
    /* noop — Task55-3 checklist */
  })
})
