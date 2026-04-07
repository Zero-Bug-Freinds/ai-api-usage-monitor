import { describe, expect, it } from "vitest"

import { createExternalKeyRequestSchema, updateExternalKeyRequestSchema } from "./external-keys.schema"

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

describe("updateExternalKeyRequestSchema", () => {
  it("accepts alias-only payload", () => {
    const parsed = updateExternalKeyRequestSchema.safeParse({
      alias: "새 별칭",
    })

    expect(parsed.success).toBe(true)
  })

  it("rejects empty alias", () => {
    const parsed = updateExternalKeyRequestSchema.safeParse({
      alias: " ",
    })

    expect(parsed.success).toBe(false)
  })

  it("rejects externalKey update without provider", () => {
    const parsed = updateExternalKeyRequestSchema.safeParse({
      alias: "OpenAI 키 1",
      externalKey: "sk-live",
    })

    expect(parsed.success).toBe(false)
  })
})
