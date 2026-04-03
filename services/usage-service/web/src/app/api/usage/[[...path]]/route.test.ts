import { afterEach, describe, expect, it, vi } from "vitest"

import { GET } from "./route"

afterEach(() => {
  vi.restoreAllMocks()
  delete process.env.API_GATEWAY_URL
  delete process.env.IDENTITY_SERVICE_URL
  delete process.env.GATEWAY_DEV_MODE
})

function ctx(path?: string[]) {
  return { params: Promise.resolve({ path }) }
}

describe("GET /api/usage/[[...path]] (BFF proxy)", () => {
  it("returns 401 when access_token cookie is missing", async () => {
    const req = new Request("http://localhost/api/usage/dashboard/summary?from=2025-01-01&to=2025-01-31")

    const res = await GET(req, ctx(["dashboard", "summary"]))
    expect(res.status).toBe(401)
    const json = (await res.json()) as { message: string }
    expect(json.message).toContain("로그인")
    expect(res.headers.get("cache-control")).toBe("no-store")
  })

  it("returns 500 when API_GATEWAY_URL is missing", async () => {
    const req = new Request("http://localhost/api/usage/dashboard/summary", {
      headers: { cookie: "access_token=tok" },
    })

    const res = await GET(req, ctx(["dashboard", "summary"]))
    expect(res.status).toBe(500)
    const json = (await res.json()) as { message: string }
    expect(json.message).toContain("API_GATEWAY_URL")
  })

  it("returns 404 when path segments are empty", async () => {
    process.env.API_GATEWAY_URL = "http://localhost:8080"
    const req = new Request("http://localhost/api/usage", {
      headers: { cookie: "access_token=tok" },
    })

    const res = await GET(req, ctx(undefined))
    expect(res.status).toBe(404)
  })

  it("proxies to gateway /api/v1/usage/... with Bearer (JWT 게이트웨이 모드)", async () => {
    process.env.API_GATEWAY_URL = "http://localhost:8080"
    process.env.GATEWAY_DEV_MODE = "false"
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      expect(url).toBe(
        "http://localhost:8080/api/v1/usage/dashboard/summary?from=2025-01-01&to=2025-01-31"
      )
      expect(init?.method).toBe("GET")
      const h = init?.headers as Headers
      expect(h.get("Authorization")).toBe("Bearer test-token-value")
      expect(h.get("Accept")).toBe("application/json")
      expect(h.has("X-User-Id")).toBe(false)
      return new Response(JSON.stringify({ totalTokens: 1 }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    })
    vi.stubGlobal("fetch", fetchMock)

    const req = new Request(
      "http://localhost/api/usage/dashboard/summary?from=2025-01-01&to=2025-01-31",
      { headers: { cookie: "access_token=test-token-value" } }
    )

    const res = await GET(req, ctx(["dashboard", "summary"]))
    expect(res.status).toBe(200)
    const json = (await res.json()) as { totalTokens: number }
    expect(json.totalTokens).toBe(1)
    expect(res.headers.get("cache-control")).toBe("no-store")
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it("GATEWAY_DEV_MODE=true일 때 Identity 세션 후 X-User-Id를 붙여 프록시한다", async () => {
    process.env.API_GATEWAY_URL = "http://localhost:8080"
    process.env.IDENTITY_SERVICE_URL = "http://localhost:8099"
    process.env.GATEWAY_DEV_MODE = "true"

    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (url === "http://localhost:8099/api/auth/session") {
        expect(init?.headers).toMatchObject({
          Authorization: "Bearer dev-token",
          Accept: "application/json",
        })
        return new Response(
          JSON.stringify({
            success: true,
            message: "ok",
            data: { email: "user@test.com", role: "USER", authenticated: true },
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        )
      }
      if (url === "http://localhost:8080/api/v1/usage/dashboard/summary") {
        const h = init?.headers as Headers
        expect(h.get("Authorization")).toBe("Bearer dev-token")
        expect(h.get("X-User-Id")).toBe("user@test.com")
        return new Response(JSON.stringify({ ok: true }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      }
      throw new Error(`unexpected url ${url}`)
    })
    vi.stubGlobal("fetch", fetchMock)

    const req = new Request("http://localhost/api/usage/dashboard/summary", {
      headers: { cookie: "access_token=dev-token" },
    })

    const res = await GET(req, ctx(["dashboard", "summary"]))
    expect(res.status).toBe(200)
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it("GATEWAY_DEV_MODE=true이고 IDENTITY_SERVICE_URL이 없으면 500", async () => {
    process.env.API_GATEWAY_URL = "http://localhost:8080"
    process.env.GATEWAY_DEV_MODE = "true"

    const req = new Request("http://localhost/api/usage/x", {
      headers: { cookie: "access_token=t" },
    })

    const res = await GET(req, ctx(["x"]))
    expect(res.status).toBe(500)
  })

  it("strips trailing slashes from API_GATEWAY_URL", async () => {
    process.env.API_GATEWAY_URL = "http://localhost:8080/"
    process.env.GATEWAY_DEV_MODE = "false"
    const fetchMock = vi.fn(async (url: string) => {
      expect(url).toBe("http://localhost:8080/api/v1/usage/logs")
      return new Response("[]", { status: 200, headers: { "Content-Type": "application/json" } })
    })
    vi.stubGlobal("fetch", fetchMock)

    const req = new Request("http://localhost/api/usage/logs", {
      headers: { cookie: "access_token=t" },
    })

    const res = await GET(req, ctx(["logs"]))
    expect(res.status).toBe(200)
  })

  it("returns 502 when gateway is unreachable", async () => {
    process.env.API_GATEWAY_URL = "http://localhost:8080"
    process.env.GATEWAY_DEV_MODE = "false"
    vi.stubGlobal("fetch", vi.fn(async () => Promise.reject(new Error("ECONNREFUSED"))))

    const req = new Request("http://localhost/api/usage/a", {
      headers: { cookie: "access_token=t" },
    })

    const res = await GET(req, ctx(["a"]))
    expect(res.status).toBe(502)
  })
})
