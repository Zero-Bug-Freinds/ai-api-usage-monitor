import { describe, expect, it } from "vitest"

/**
 * matcher(`middleware.ts`)와 대응하는 페이지 모듈이 존재하고
 * default export가 있는지 확인한다.
 */
describe("protected app routes (Usage dashboard)", () => {
  it("(shell) index page loads", async () => {
    const mod = await import("@/app/(shell)/page")
    expect(mod.default).toBeTypeOf("function")
  })

  it("(shell)/[...path] page loads", async () => {
    const mod = await import("@/app/(shell)/[...path]/page")
    expect(mod.default).toBeTypeOf("function")
  })
})
