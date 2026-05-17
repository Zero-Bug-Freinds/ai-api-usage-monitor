import { describe, expect, it, beforeEach } from "vitest"
import {
  readTeamDashboardLastTeamId,
  TEAM_DASHBOARD_LAST_TEAM_ID_KEY,
  writeTeamDashboardLastTeamId,
} from "@/lib/usage/team-dashboard-last-team"

describe("team-dashboard-last-team", () => {
  beforeEach(() => {
    localStorage.clear()
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
