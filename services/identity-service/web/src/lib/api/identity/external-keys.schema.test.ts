import { describe, expect, it } from "vitest"

import { createExternalKeyRequestSchema } from "./external-keys.schema"

describe("createExternalKeyRequestSchema", () => {
  it("accepts a valid payload", () => {
    const parsed = createExternalKeyRequestSchema.safeParse({
      provider: "OPENAI",
      externalKey: "sk-test",
      alias: "OpenAI 키 1",
    })

    expect(parsed.success).toBe(true)
  })

  it("rejects empty externalKey", () => {
    const parsed = createExternalKeyRequestSchema.safeParse({
      provider: "GEMINI",
      externalKey: "   ",
      alias: "Gemini 키 1",
    })

    expect(parsed.success).toBe(false)
  })

  it("rejects empty alias", () => {
    const parsed = createExternalKeyRequestSchema.safeParse({
      provider: "ANTHROPIC",
      externalKey: "test",
      alias: " ",
    })

    expect(parsed.success).toBe(false)
  })
})
