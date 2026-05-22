import { describe, expect, it } from "vitest"

import { COMMON_MESSAGES } from "./common-messages"

describe("COMMON_MESSAGES", () => {
  it("exposes filter bar and shell copy", () => {
    expect(COMMON_MESSAGES.filterBar.teamSelect).toBe("팀 선택")
    expect(COMMON_MESSAGES.filterBar.apiKeyAll).toBe("전체")
    expect(COMMON_MESSAGES.shell.loading).toBe("불러오는 중…")
    expect(COMMON_MESSAGES.shell.routeNotReady).toContain("준비 중")
    expect(COMMON_MESSAGES.memberAnalytics.noRequestData).toContain("요청 데이터")
  })
})
