import { describe, expect, it } from "vitest"

import { signupRequestSchema } from "./signup.schema"

describe("signupRequestSchema", () => {
  it("accepts a valid payload", () => {
    const parsed = signupRequestSchema.safeParse({
      email: "happy@test.com",
      password: "password0000",
      name: "testDemo",
      role: "USER",
    })

    expect(parsed.success).toBe(true)
  })

  it("rejects an invalid email", () => {
    const parsed = signupRequestSchema.safeParse({
      email: "not-an-email",
      password: "password0000",
      name: "testDemo",
      role: "USER",
    })

    expect(parsed.success).toBe(false)
  })

  it("rejects a short password", () => {
    const parsed = signupRequestSchema.safeParse({
      email: "happy@test.com",
      password: "123",
      name: "testDemo",
      role: "USER",
    })

    expect(parsed.success).toBe(false)
  })
})

