import { describe, expect, it } from "vitest"

import {
  DASHBOARD_ERRORS,
  DASHBOARD_HINTS,
  latencyInsightBannerText,
  LATENCY_INSIGHTS,
  resolveEmptyDashboardHint,
  toDashboardMainErrorMessage,
} from "./dashboard-messages"

describe("toDashboardMainErrorMessage", () => {
  it("keeps client validation copy", () => {
    expect(toDashboardMainErrorMessage(new Error(DASHBOARD_ERRORS.validation.endBeforeStart))).toBe(
      DASHBOARD_ERRORS.validation.endBeforeStart,
    )
    expect(toDashboardMainErrorMessage(new Error(DASHBOARD_ERRORS.validation.maxRangeDays))).toBe(
      DASHBOARD_ERRORS.validation.maxRangeDays,
    )
  })

  it("masks system and upstream errors", () => {
    expect(toDashboardMainErrorMessage(new Error("Date range too large"))).toBe(DASHBOARD_ERRORS.mainSystem)
    expect(toDashboardMainErrorMessage("network")).toBe(DASHBOARD_ERRORS.mainSystem)
  })
})

describe("resolveEmptyDashboardHint", () => {
  it("branches by team membership and api keys", () => {
    expect(resolveEmptyDashboardHint("TEAM_MEMBER_ONLY", false, 3)).toBe(DASHBOARD_HINTS.noTeams)
    expect(resolveEmptyDashboardHint("TEAM_MEMBER_ONLY", true, 0)).toBe(DASHBOARD_HINTS.noApiKeys)
    expect(resolveEmptyDashboardHint("PERSONAL", true, 0)).toBe(DASHBOARD_HINTS.noUsageData)
  })
})

describe("latencyInsightBannerText", () => {
  const fmt = (ms: number | null | undefined) => (ms == null ? "—" : `${ms}ms`)

  it("returns static copy when data is missing", () => {
    expect(latencyInsightBannerText(null, "전일 동기 대비", fmt)).toBe(LATENCY_INSIGHTS.noData)
    expect(
      latencyInsightBannerText({ currentAvgLatencyMs: 10, previousAvgLatencyMs: null }, "전일 동기 대비", fmt),
    ).toBe(LATENCY_INSIGHTS.noCompare)
  })
})

describe("member teams masked messages", () => {
  it("uses stable error codes", () => {
    expect(DASHBOARD_ERRORS.memberTeamsEnv).toContain("ERR_ENV_500")
    expect(DASHBOARD_ERRORS.memberTeamsList).toContain("ERR_TEAM_LIST")
  })
})
