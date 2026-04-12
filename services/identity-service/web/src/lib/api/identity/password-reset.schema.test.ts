import { describe, expect, it } from "vitest"

import { forgotPasswordSchema, resetPasswordSchema } from "./password-reset.schema"

describe("forgotPasswordSchema", () => {
  it("accepts a valid email", () => {
    const parsed = forgotPasswordSchema.safeParse({ email: "u@example.com" })
    expect(parsed.success).toBe(true)
  })
})

describe("resetPasswordSchema", () => {
  it("accepts matching passwords", () => {
    const parsed = resetPasswordSchema.safeParse({
      token: "a".repeat(64),
      password: "abc123!@",
      passwordConfirm: "abc123!@",
    })
    expect(parsed.success).toBe(true)
  })

  it("rejects mismatched passwords", () => {
    const parsed = resetPasswordSchema.safeParse({
      token: "a".repeat(64),
      password: "abc123!@",
      passwordConfirm: "abc123!@x",
    })
    expect(parsed.success).toBe(false)
  })
})
