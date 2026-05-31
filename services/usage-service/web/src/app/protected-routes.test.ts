import { readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { fileURLToPath } from "node:url"
import { describe, expect, it } from "vitest"

const WEB_ROOT = join(dirname(fileURLToPath(import.meta.url)), "../..")

/**
 * matcher(`middleware.ts`)와 대응하는 페이지 모듈이 존재하고
 * default export가 있는지 확인한다. (Vite 8 + jsx:preserve 환경에서는 .tsx 동적 import 대신 소스 검증)
 */
const protectedPages = [
  ["(shell) index", "src/app/(shell)/page.tsx"],
  ["(shell)/[...path]", "src/app/(shell)/[...path]/page.tsx"],
  ["(shell)/usagelog", "src/app/(shell)/usagelog/page.tsx"],
] as const

describe("protected app routes (Usage dashboard)", () => {
  it.each(protectedPages)("%s page module exists with default export", (_label, relPath) => {
    const src = readFileSync(join(WEB_ROOT, relPath), "utf8")
    expect(src).toMatch(/export\s+default\s+(async\s+)?function/)
  })
})
