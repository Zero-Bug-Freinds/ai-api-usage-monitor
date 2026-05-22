import { afterEach, beforeEach, describe, expect, it } from "vitest"
import {
  readTeamDashboardLastTeamId,
  TEAM_DASHBOARD_LAST_TEAM_ID_KEY,
  writeTeamDashboardLastTeamId,
} from "@/lib/usage/team-dashboard-last-team"

function installLocalStorageMock() {
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
  Object.defineProperty(globalThis, "localStorage", { value: api, configurable: true })
  Object.defineProperty(globalThis, "window", {
    value: { localStorage: api },
    configurable: true,
  })
  return store
}

describe("team-dashboard-last-team", () => {
  beforeEach(() => {
    installLocalStorageMock().clear()
  })

  afterEach(() => {
    Reflect.deleteProperty(globalThis, "localStorage")
    Reflect.deleteProperty(globalThis, "window")
  })

  it("writes and reads last team id", () => {
    writeTeamDashboardLastTeamId("team-42")
    expect(localStorage.getItem(TEAM_DASHBOARD_LAST_TEAM_ID_KEY)).toBe("team-42")
    expect(readTeamDashboardLastTeamId()).toBe("team-42")
  })

  it("ignores blank writes", () => {
    writeTeamDashboardLastTeamId("  ")
    expect(readTeamDashboardLastTeamId()).toBe("")
  })
})
