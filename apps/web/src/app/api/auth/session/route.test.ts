import { afterEach, describe, expect, it, vi } from "vitest"

import { GET } from "./route"

afterEach(() => {
  vi.restoreAllMocks()
  delete process.env.IDENTITY_SERVICE_URL
})

describe("GET /api/auth/session (route handler)", () => {
  it("returns 401 when access_token cookie is missing", async () => {
    const req = new Request("http://localhost/api/auth/session", { method: "GET" })

    const res = await GET(req)
    expect(res.status).toBe(401)
    const json = (await res.json()) as { success: boolean; data: null }
    expect(json.success).toBe(false)
    expect(json.data).toBeNull()
    expect(res.headers.get("cache-control")).toBe("no-store")
  })

  it("returns 500 when IDENTITY_SERVICE_URL is missing", async () => {
    const req = new Request("http://localhost/api/auth/session", {
      method: "GET",
      headers: { cookie: "access_token=test-token-value" },
    })

    const res = await GET(req)
    expect(res.status).toBe(500)
    const json = (await res.json()) as { success: boolean }
    expect(json.success).toBe(false)
  })

  it("proxies to Identity with Bearer token and returns session data", async () => {
    process.env.IDENTITY_SERVICE_URL = "http://localhost:8080"
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      expect(url).toBe("http://localhost:8080/api/auth/session")
      expect(init?.method).toBe("GET")
      expect(init?.headers).toMatchObject({
        Authorization: "Bearer test-token-value",
        Accept: "application/json",
      })
      return new Response(
        JSON.stringify({
          success: true,
          message: "세션이 유효합니다",
          data: { email: "u@test.com", role: "USER", authenticated: true },
        }),
        { status: 200, headers: { "Content-Type": "application/json" } }
      )
    })
    vi.stubGlobal("fetch", fetchMock)

    const req = new Request("http://localhost/api/auth/session", {
      method: "GET",
      headers: { cookie: "access_token=test-token-value" },
    })

    const res = await GET(req)
    expect(res.status).toBe(200)
    const json = (await res.json()) as {
      success: boolean
      data: { email: string; role: string; authenticated: boolean }
    }
    expect(json.success).toBe(true)
    expect(json.data.email).toBe("u@test.com")
    expect(json.data.role).toBe("USER")
    expect(json.data.authenticated).toBe(true)
    expect(res.headers.get("cache-control")).toBe("no-store")
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it("returns 401 when Identity rejects the token", async () => {
    process.env.IDENTITY_SERVICE_URL = "http://localhost:8080"
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        return new Response(
          JSON.stringify({
            success: false,
            message: "인증이 필요합니다",
            data: null,
          }),
          { status: 401, headers: { "Content-Type": "application/json" } }
        )
      })
    )

    const req = new Request("http://localhost/api/auth/session", {
      method: "GET",
      headers: { cookie: "access_token=bad" },
    })

    const res = await GET(req)
    expect(res.status).toBe(401)
    const json = (await res.json()) as { success: boolean; message: string }
    expect(json.success).toBe(false)
    expect(json.message).toContain("인증")
  })

  it("returns 502 when upstream session shape is invalid", async () => {
    process.env.IDENTITY_SERVICE_URL = "http://localhost:8080"
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        return new Response(
          JSON.stringify({
            success: true,
            message: "세션이 유효합니다",
            data: { authenticated: true },
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        )
      })
    )

    const req = new Request("http://localhost/api/auth/session", {
      method: "GET",
      headers: { cookie: "access_token=t" },
    })

    const res = await GET(req)
    expect(res.status).toBe(502)
  })

  it("returns 502 when session role is not USER or ADMIN", async () => {
    process.env.IDENTITY_SERVICE_URL = "http://localhost:8080"
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        return new Response(
          JSON.stringify({
            success: true,
            message: "세션이 유효합니다",
            data: { email: "u@test.com", role: "GUEST", authenticated: true },
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        )
      })
    )

    const req = new Request("http://localhost/api/auth/session", {
      method: "GET",
      headers: { cookie: "access_token=t" },
    })

    const res = await GET(req)
    expect(res.status).toBe(502)
  })

  it("returns 502 when upstream returns 200 but success is false", async () => {
    process.env.IDENTITY_SERVICE_URL = "http://localhost:8080"
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        return new Response(
          JSON.stringify({
            success: false,
            message: "unexpected",
            data: { email: "u@test.com", role: "USER", authenticated: true },
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        )
      })
    )

    const req = new Request("http://localhost/api/auth/session", {
      method: "GET",
      headers: { cookie: "access_token=t" },
    })

    const res = await GET(req)
    expect(res.status).toBe(502)
  })

  it("strips trailing slashes from IDENTITY_SERVICE_URL when proxying", async () => {
    process.env.IDENTITY_SERVICE_URL = "http://localhost:8080/"
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      expect(url).toBe("http://localhost:8080/api/auth/session")
      expect(init?.headers).toMatchObject({ Authorization: "Bearer t" })
      return new Response(
        JSON.stringify({
          success: true,
          message: "ok",
          data: { email: "u@test.com", role: "ADMIN", authenticated: true },
        }),
        { status: 200, headers: { "Content-Type": "application/json" } }
      )
    })
    vi.stubGlobal("fetch", fetchMock)

    const req = new Request("http://localhost/api/auth/session", {
      method: "GET",
      headers: { cookie: "access_token=t" },
    })

    const res = await GET(req)
    expect(res.status).toBe(200)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it("returns 502 when Identity is unreachable", async () => {
    process.env.IDENTITY_SERVICE_URL = "http://localhost:8080"
    vi.stubGlobal("fetch", vi.fn(async () => Promise.reject(new Error("ECONNREFUSED"))))

    const req = new Request("http://localhost/api/auth/session", {
      method: "GET",
      headers: { cookie: "access_token=t" },
    })

    const res = await GET(req)
    expect(res.status).toBe(502)
  })
})
