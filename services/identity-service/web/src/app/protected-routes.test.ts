import { describe, expect, it } from "vitest"

/**
 * matcher(`middleware.ts`)와 대응하는 optional catch-all 페이지 모듈이 존재하고
 * default export가 있는지 확인한다. 라우트 파일을 옮기거나 삭제하면 여기서 실패한다.
 */
describe("protected app routes (match middleware prefixes)", () => {
  it.each([
    ["settings", () => import("@/app/settings/[[...path]]/page")],
    ["organizations", () => import("@/app/organizations/[[...path]]/page")],
    ["teams", () => import("@/app/teams/[[...path]]/page")],
  ])("%s [[...path]] page loads", async (_, load) => {
    const mod = await load()
    expect(mod.default).toBeTypeOf("function")
  })
})
