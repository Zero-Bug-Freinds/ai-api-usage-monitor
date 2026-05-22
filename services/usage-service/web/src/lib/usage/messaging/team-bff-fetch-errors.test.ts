import { describe, expect, it } from "vitest"

import { memberUsageFetchError, usageFetchErrorMessage } from "./team-bff-fetch-errors"

describe("usageFetchErrorMessage", () => {
  it("maps status codes to masked team dashboard copy", () => {
    expect(usageFetchErrorMessage(400)).toContain("ERR_TEAM_400")
    expect(usageFetchErrorMessage(401)).not.toContain("ERR_TEAM_")
    expect(usageFetchErrorMessage(404)).toContain("ERR_TEAM_404")
    expect(usageFetchErrorMessage(500)).toContain("ERR_TEAM_500")
    expect(usageFetchErrorMessage(418)).toBe(
      "사용량 데이터를 불러오지 못했습니다. (Code: ERR_TEAM_418)",
    )
  })
})

describe("memberUsageFetchError", () => {
  it("maps status codes to masked member detail copy", () => {
    expect(memberUsageFetchError(400)).toContain("ERR_MBR_400")
    expect(memberUsageFetchError(403)).not.toContain("ERR_MBR_")
    expect(memberUsageFetchError(404)).toContain("ERR_MBR_404")
    expect(memberUsageFetchError(502)).toContain("ERR_MBR_500")
    expect(memberUsageFetchError(418)).toBe("멤버 정보를 불러오지 못했습니다. (Code: ERR_MBR_418)")
  })
})
