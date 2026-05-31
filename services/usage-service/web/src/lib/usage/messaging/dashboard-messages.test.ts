import { describe, expect, it, vi } from "vitest"

import { memberUsageFetchError } from "./team-bff-fetch-errors"
import {
  MEMBER_DETAIL_MESSAGES,
  PERSONAL_DASHBOARD_MESSAGES,
  TEAM_DASHBOARD_MESSAGES,
  latencyInsightBannerText,
  resolveEmptyDashboardHint,
  toDashboardMainErrorMessage,
  warnTeamPartialEnrichment,
} from "./dashboard-messages"

describe("toDashboardMainErrorMessage", () => {
  it("keeps client validation copy", () => {
    expect(
      toDashboardMainErrorMessage(
        new Error(PERSONAL_DASHBOARD_MESSAGES.errors.validation.endBeforeStart),
      ),
    ).toBe(PERSONAL_DASHBOARD_MESSAGES.errors.validation.endBeforeStart)
    expect(
      toDashboardMainErrorMessage(new Error(PERSONAL_DASHBOARD_MESSAGES.errors.validation.maxRangeDays)),
    ).toBe(PERSONAL_DASHBOARD_MESSAGES.errors.validation.maxRangeDays)
  })

  it("masks system and upstream errors", () => {
    expect(toDashboardMainErrorMessage(new Error("Date range too large"))).toBe(
      PERSONAL_DASHBOARD_MESSAGES.errors.mainSystem,
    )
    expect(toDashboardMainErrorMessage("network")).toBe(PERSONAL_DASHBOARD_MESSAGES.errors.mainSystem)
  })
})

describe("resolveEmptyDashboardHint", () => {
  it("branches by team membership and api keys", () => {
    expect(resolveEmptyDashboardHint("TEAM_MEMBER_ONLY", false, 3)).toBe(
      PERSONAL_DASHBOARD_MESSAGES.hints.noTeams,
    )
    expect(resolveEmptyDashboardHint("TEAM_MEMBER_ONLY", true, 0)).toBe(
      PERSONAL_DASHBOARD_MESSAGES.hints.noApiKeys,
    )
    expect(resolveEmptyDashboardHint("PERSONAL", true, 0)).toBe(PERSONAL_DASHBOARD_MESSAGES.hints.noUsageData)
  })
})

describe("latencyInsightBannerText", () => {
  const fmt = (ms: number | null | undefined) => (ms == null ? "—" : `${ms}ms`)

  it("returns static copy when data is missing", () => {
    expect(latencyInsightBannerText(null, "전일 동기 대비", fmt)).toBe(
      PERSONAL_DASHBOARD_MESSAGES.latency.noData,
    )
    expect(
      latencyInsightBannerText(
        { currentAvgLatencyMs: 10, previousAvgLatencyMs: null, changePercent: null },
        "전일 동기 대비",
        fmt,
      ),
    ).toBe(PERSONAL_DASHBOARD_MESSAGES.latency.noCompare)
  })
})

describe("PERSONAL_DASHBOARD_MESSAGES member teams errors", () => {
  it("uses stable error codes", () => {
    expect(PERSONAL_DASHBOARD_MESSAGES.errors.memberTeamsEnv).toContain("ERR_ENV_500")
    expect(PERSONAL_DASHBOARD_MESSAGES.errors.memberTeamsList).toContain("ERR_TEAM_LIST")
  })
})

describe("TEAM_DASHBOARD_MESSAGES", () => {
  it("uses stable error codes and hints", () => {
    expect(TEAM_DASHBOARD_MESSAGES.errors.teamsEnv).toContain("ERR_ENV_500")
    expect(TEAM_DASHBOARD_MESSAGES.errors.teamsList).toContain("ERR_TEAM_LIST")
    expect(TEAM_DASHBOARD_MESSAGES.warnings.partialEnrichment).toContain("ERR_TEAM_PARTIAL")
    expect(TEAM_DASHBOARD_MESSAGES.hints.noModelUsage).toContain("멤버 모델 사용 데이터")
  })
})

describe("MEMBER_DETAIL_MESSAGES", () => {
  it("shares empty model copy with team dashboard and aligns bad request with BFF", () => {
    expect(MEMBER_DETAIL_MESSAGES.hints.noModelUsage).toBe(TEAM_DASHBOARD_MESSAGES.hints.noModelUsage)
    expect(MEMBER_DETAIL_MESSAGES.errors.badRequest).toBe(memberUsageFetchError(400))
    expect(MEMBER_DETAIL_MESSAGES.errors.env).toContain("ERR_ENV_500")
  })
})

describe("warnTeamPartialEnrichment", () => {
  it("logs raw warning codes without exposing them in return value", () => {
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {})
    warnTeamPartialEnrichment(["TEAM_NAME_UNAVAILABLE", "TEAM_MEMBERS_UNAVAILABLE"])
    expect(warn).toHaveBeenCalledWith(
      `[${TEAM_DASHBOARD_MESSAGES.logTags.partialWarning}]`,
      { rawCodes: ["TEAM_NAME_UNAVAILABLE", "TEAM_MEMBERS_UNAVAILABLE"] },
    )
    warn.mockRestore()
  })
})
