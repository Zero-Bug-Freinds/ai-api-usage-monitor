import { describe, expect, it } from "vitest"

/**
 * matcher(`middleware.ts`)와 대응하는 optional catch-all 페이지 모듈이 존재하고
 * default export가 있는지 확인한다.
 */
describe("protected app routes (Usage dashboard)", () => {
  it.each([["dashboard", () => import("@/app/dashboard/[[...path]]/page")]])(
    "%s [[...path]] page loads",
    async (_, load) => {
      const mod = await load()
      expect(mod.default).toBeTypeOf("function")
    }
  )
})
