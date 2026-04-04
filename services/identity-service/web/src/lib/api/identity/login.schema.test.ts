import { describe, expect, it } from "vitest"

import { loginRequestSchema } from "./login.schema"

describe("loginRequestSchema", () => {
  it("accepts a valid payload", () => {
    const parsed = loginRequestSchema.safeParse({
      email: "happy@test.com",
      password: "abc123!@",
    })

    expect(parsed.success).toBe(true)
  })

  it("rejects an invalid email", () => {
    const parsed = loginRequestSchema.safeParse({
      email: "not-an-email",
      password: "abc123!@",
    })

    expect(parsed.success).toBe(false)
  })

  it("rejects a short password", () => {
    const parsed = loginRequestSchema.safeParse({
      email: "happy@test.com",
      password: "123",
    })

    expect(parsed.success).toBe(false)
  })
})
