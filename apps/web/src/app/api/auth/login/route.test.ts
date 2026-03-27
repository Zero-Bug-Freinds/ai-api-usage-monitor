import { afterEach, describe, expect, it, vi } from "vitest"

import { POST } from "./route"

function jsonRequest(body: unknown) {
  return new Request("http://localhost/api/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  })
}

afterEach(() => {
  vi.restoreAllMocks()
  delete process.env.IDENTITY_SERVICE_URL
})

describe("POST /api/auth/login (route handler)", () => {
  it("returns 500 if IDENTITY_SERVICE_URL is missing", async () => {
    const res = await POST(jsonRequest({}))
    expect(res.status).toBe(500)
    const json = (await res.json()) as { success: boolean }
    expect(json.success).toBe(false)
  })

  it("returns 400 for invalid payload", async () => {
    process.env.IDENTITY_SERVICE_URL = "http://localhost:8080"
    const res = await POST(jsonRequest({ email: "bad", password: "123" }))
    expect(res.status).toBe(400)
  })

  it("sets httpOnly cookie on successful login", async () => {
    process.env.IDENTITY_SERVICE_URL = "http://localhost:8080"
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        return new Response(
          JSON.stringify({
            success: true,
            message: "로그인 성공",
            data: {
              accessToken: "access-token-value",
              tokenType: "Bearer",
              expiresInSeconds: 3600,
            },
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        )
      })
    )

    const res = await POST(jsonRequest({ email: "happy@test.com", password: "abc123!@" }))
    expect(res.status).toBe(200)

    const setCookie = res.headers.get("set-cookie") ?? ""
    expect(setCookie).toContain("access_token=access-token-value")
    expect(setCookie).toContain("HttpOnly")
    expect(setCookie).toContain("Path=/")
    expect(setCookie).toContain("Max-Age=3600")

    const cacheControl = res.headers.get("cache-control")
    expect(cacheControl).toBe("no-store")
  })

  it("returns 502 when tokenType is not Bearer", async () => {
    process.env.IDENTITY_SERVICE_URL = "http://localhost:8080"
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        return new Response(
          JSON.stringify({
            success: true,
            message: "로그인 성공",
            data: {
              accessToken: "access-token-value",
              tokenType: "Basic",
              expiresInSeconds: 3600,
            },
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        )
      })
    )

    const res = await POST(jsonRequest({ email: "happy@test.com", password: "abc123!@" }))
    expect(res.status).toBe(502)
    const json = (await res.json()) as { success: boolean }
    expect(json.success).toBe(false)
    expect(res.headers.get("set-cookie")).toBeNull()
  })

  it("passes through 401 and safe message", async () => {
    process.env.IDENTITY_SERVICE_URL = "http://localhost:8080"
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        return new Response(
          JSON.stringify({
            success: false,
            message: "이메일 또는 비밀번호가 올바르지 않습니다",
            data: null,
          }),
          { status: 401, headers: { "Content-Type": "application/json" } }
        )
      })
    )

    const res = await POST(jsonRequest({ email: "happy@test.com", password: "abc123!@" }))
    expect(res.status).toBe(401)
    const json = (await res.json()) as { success: boolean; message: string }
    expect(json.success).toBe(false)
    expect(json.message).toContain("비밀번호")
  })
})
