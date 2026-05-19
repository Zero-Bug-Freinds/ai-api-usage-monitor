import { describe, expect, it } from "vitest"

import {
  USAGELOG_MESSAGES,
  toUsageLogFetchErrorMessage,
} from "./usagelog-messages"

describe("USAGELOG_MESSAGES", () => {
  it("uses stable error codes", () => {
    expect(USAGELOG_MESSAGES.errors.logsFetch).toContain("ERR_LOG_500")
    expect(USAGELOG_MESSAGES.errors.memberTeamsFooter).toContain("ERR_LOG_TEAM")
    expect(USAGELOG_MESSAGES.errors.memberTeamsEnv).toContain("ERR_ENV_500")
  })
})

describe("toUsageLogFetchErrorMessage", () => {
  it("always masks upstream errors", () => {
    expect(toUsageLogFetchErrorMessage(new Error("upstream secret"))).toBe(
      USAGELOG_MESSAGES.errors.logsFetch,
    )
    expect(toUsageLogFetchErrorMessage("network")).toBe(USAGELOG_MESSAGES.errors.logsFetch)
  })
})
