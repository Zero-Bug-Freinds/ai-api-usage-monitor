import { describe, expect, it } from "vitest"

import { createExternalKeyRequestSchema, updateExternalKeyRequestSchema } from "./external-keys.schema"

describe("createExternalKeyRequestSchema", () => {
  it("accepts a valid payload", () => {
    const parsed = createExternalKeyRequestSchema.safeParse({
      provider: "OPENAI",
      externalKey: "sk-test",
      alias: "OpenAI 키 1",
      monthlyBudgetUsd: 20,
    })

    expect(parsed.success).toBe(true)
  })

  it("rejects empty externalKey", () => {
    const parsed = createExternalKeyRequestSchema.safeParse({
      provider: "GEMINI",
      externalKey: "   ",
      alias: "Gemini 키 1",
      monthlyBudgetUsd: 10,
    })

    expect(parsed.success).toBe(false)
  })

  it("rejects empty alias", () => {
    const parsed = createExternalKeyRequestSchema.safeParse({
      provider: "ANTHROPIC",
      externalKey: "test",
      alias: " ",
      monthlyBudgetUsd: 10,
    })

    expect(parsed.success).toBe(false)
  })

  it("accepts monthly budget", () => {
    const parsed = createExternalKeyRequestSchema.safeParse({
      provider: "OPENAI",
      externalKey: "sk-test",
      alias: "OpenAI 키 1",
      monthlyBudgetUsd: 25.5,
    })

    expect(parsed.success).toBe(true)
  })

  it("rejects negative monthly budget", () => {
    const parsed = createExternalKeyRequestSchema.safeParse({
      provider: "OPENAI",
      externalKey: "sk-test",
      alias: "OpenAI 키 1",
      monthlyBudgetUsd: -1,
    })

    expect(parsed.success).toBe(false)
  })
})

describe("updateExternalKeyRequestSchema", () => {
  it("rejects alias-only payload when budget is missing", () => {
    const parsed = updateExternalKeyRequestSchema.safeParse({
      alias: "새 별칭",
    })

    expect(parsed.success).toBe(false)
  })

  it("rejects empty alias", () => {
    const parsed = updateExternalKeyRequestSchema.safeParse({
      alias: " ",
      monthlyBudgetUsd: 10,
    })

    expect(parsed.success).toBe(false)
  })

  it("rejects externalKey update without provider", () => {
    const parsed = updateExternalKeyRequestSchema.safeParse({
      alias: "OpenAI 키 1",
      externalKey: "sk-live",
      monthlyBudgetUsd: 10,
    })

    expect(parsed.success).toBe(false)
  })

  it("accepts alias with monthly budget", () => {
    const parsed = updateExternalKeyRequestSchema.safeParse({
      alias: "새 별칭",
      monthlyBudgetUsd: 12.34,
    })

    expect(parsed.success).toBe(true)
  })

  it("rejects null monthly budget", () => {
    const parsed = updateExternalKeyRequestSchema.safeParse({
      alias: "새 별칭",
      monthlyBudgetUsd: null,
    })

    expect(parsed.success).toBe(false)
  })
})
