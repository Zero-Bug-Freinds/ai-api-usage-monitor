import { describe, expect, it } from "vitest"

import { signupPasswordPolicyMessage, signupRequestSchema } from "./signup.schema"

const validBase = {
  email: "happy@test.com",
  password: "abc123!@",
  passwordConfirm: "abc123!@",
  name: "testDemo",
}

describe("signupRequestSchema", () => {
  it("accepts a valid payload", () => {
    const parsed = signupRequestSchema.safeParse(validBase)

    expect(parsed.success).toBe(true)
  })

  it("rejects an invalid email", () => {
    const parsed = signupRequestSchema.safeParse({
      ...validBase,
      email: "not-an-email",
    })

    expect(parsed.success).toBe(false)
  })

  it("rejects a short password", () => {
    const parsed = signupRequestSchema.safeParse({
      ...validBase,
      password: "123",
      passwordConfirm: "123",
    })

    expect(parsed.success).toBe(false)
  })

  it("rejects password without special character", () => {
    const parsed = signupRequestSchema.safeParse({
      ...validBase,
      password: "password00",
      passwordConfirm: "password00",
    })

    expect(parsed.success).toBe(false)
    if (!parsed.success) {
      expect(parsed.error.issues.some((i) => i.message === signupPasswordPolicyMessage)).toBe(true)
    }
  })

  it("rejects password with uppercase", () => {
    const parsed = signupRequestSchema.safeParse({
      ...validBase,
      password: "Abc123!@",
      passwordConfirm: "Abc123!@",
    })

    expect(parsed.success).toBe(false)
  })

  it("rejects password mismatch", () => {
    const parsed = signupRequestSchema.safeParse({
      ...validBase,
      passwordConfirm: "abc123!@x",
    })

    expect(parsed.success).toBe(false)
    if (!parsed.success) {
      const msg = parsed.error.issues.find((i) => i.path.includes("passwordConfirm"))?.message
      expect(msg).toBe("비밀번호가 일치하지 않습니다")
    }
  })

  it("rejects empty passwordConfirm", () => {
    const parsed = signupRequestSchema.safeParse({
      ...validBase,
      passwordConfirm: "",
    })

    expect(parsed.success).toBe(false)
  })
})
