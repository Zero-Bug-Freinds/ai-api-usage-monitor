import { describe, expect, it } from "vitest"
import { formatUsageLogTableCost } from "@/lib/usage/format"
import { formatTeamMemberLocalId } from "@/lib/usage/format-team-member-label"

describe("formatUsageLogTableCost", () => {
  it("returns 0 for failed requests", () => {
    expect(formatUsageLogTableCost(0.05, false)).toBe("0")
    expect(formatUsageLogTableCost("1.2345", false)).toBe("0")
  })

  it("shows floor hint below 0.001", () => {
    expect(formatUsageLogTableCost(0.0005, true)).toBe("< 0.001")
    expect(formatUsageLogTableCost(0.0000001, true)).toBe("< 0.001")
  })

  it("rounds to four decimal places for normal amounts", () => {
    expect(formatUsageLogTableCost(0.01234, true)).toBe("0.0123")
    expect(formatUsageLogTableCost(1.5, true)).toBe("1.5000")
    expect(formatUsageLogTableCost(0, true)).toBe("0")
  })
})

describe("formatTeamMemberLocalId", () => {
  it("slices email local part", () => {
    expect(formatTeamMemberLocalId("dpsk1515@naver.com")).toBe("dpsk1515")
  })

  it("returns dash when empty", () => {
    expect(formatTeamMemberLocalId(null)).toBe("—")
    expect(formatTeamMemberLocalId("")).toBe("—")
  })
})
