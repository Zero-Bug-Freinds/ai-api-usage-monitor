import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import { GET, POST } from "./route"

const originalGatewayUrl = process.env.GATEWAY_URL

beforeEach(() => {
  process.env.GATEWAY_URL = "http://localhost:8888"
})

afterEach(() => {
  vi.restoreAllMocks()
  if (originalGatewayUrl === undefined) {
    delete process.env.GATEWAY_URL
  } else {
    process.env.GATEWAY_URL = originalGatewayUrl
  }
})

function ctx(path?: string[]) {
  return { params: Promise.resolve({ path }) }
}

describe("GET /api/identity/[[...path]] (Identity management BFF)", () => {
  it("returns 401 when access_token cookie is missing", async () => {
    const req = new Request("http://localhost/api/identity/v1/me/profile")

    const res = await GET(req, ctx(["v1", "me", "profile"]))
    expect(res.status).toBe(401)
    const json = (await res.json()) as { message: string }
    expect(json.message).toContain("로그인")
    expect(res.headers.get("cache-control")).toBe("no-store")
  })

  it("returns 500 when GATEWAY_URL and WEB_GATEWAY_URL are missing", async () => {
    delete process.env.GATEWAY_URL
    delete process.env.WEB_GATEWAY_URL
    const req = new Request("http://localhost/api/identity/v1/me/profile", {
      headers: { cookie: "access_token=tok" },
    })

    const res = await GET(req, ctx(["v1", "me", "profile"]))
    expect(res.status).toBe(500)
    const json = (await res.json()) as { message: string }
    expect(json.message).toContain("GATEWAY_URL")
  })

  it("returns 404 when path does not start with v1", async () => {
    const req = new Request("http://localhost/api/identity/other", {
      headers: { cookie: "access_token=tok" },
    })

    const res = await GET(req, ctx(["other"]))
    expect(res.status).toBe(404)
  })

  it("returns 404 when path segments are empty", async () => {
    const req = new Request("http://localhost/api/identity", {
      headers: { cookie: "access_token=tok" },
    })

    const res = await GET(req, ctx(undefined))
    expect(res.status).toBe(404)
  })

  it("proxies to Gateway /api/identity/v1/... with Bearer", async () => {
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      expect(url).toBe("http://localhost:8888/api/identity/v1/me/profile")
      expect(init?.method).toBe("GET")
      const h = init?.headers as Headers
      expect(h.get("Authorization")).toBe("Bearer test-token-value")
      expect(h.get("Accept")).toBe("application/json")
      return new Response(JSON.stringify({ success: true, message: "ok", data: [] }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    })
    vi.stubGlobal("fetch", fetchMock)

    const req = new Request("http://localhost/api/identity/v1/me/profile", {
      headers: { cookie: "access_token=test-token-value" },
    })

    const res = await GET(req, ctx(["v1", "me", "profile"]))
    expect(res.status).toBe(200)
    const json = (await res.json()) as { success: boolean; data: unknown[] }
    expect(json.success).toBe(true)
    expect(Array.isArray(json.data)).toBe(true)
    expect(res.headers.get("cache-control")).toBe("no-store")
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it("forwards query string to upstream", async () => {
    const fetchMock = vi.fn(async (url: string) => {
      expect(url).toBe("http://localhost:8888/api/identity/v1/me/teams?page=0&size=10")
      return new Response("{}", { status: 200, headers: { "Content-Type": "application/json" } })
    })
    vi.stubGlobal("fetch", fetchMock)

    const req = new Request("http://localhost/api/identity/v1/me/teams?page=0&size=10", {
      headers: { cookie: "access_token=t" },
    })

    const res = await GET(req, ctx(["v1", "me", "teams"]))
    expect(res.status).toBe(200)
  })
})

describe("POST /api/identity/[[...path]]", () => {
  it("proxies JSON body to upstream", async () => {
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      expect(url).toBe("http://localhost:8888/api/identity/v1/account/profile")
      expect(init?.method).toBe("POST")
      const h = init?.headers as Headers
      expect(h.get("Content-Type")).toBe("application/json")
      expect(h.get("Authorization")).toBe("Bearer tok")
      return new Response(JSON.stringify({ success: true, message: "created", data: { id: "1" } }), {
        status: 201,
        headers: { "Content-Type": "application/json" },
      })
    })
    vi.stubGlobal("fetch", fetchMock)

    const req = new Request("http://localhost/api/identity/v1/account/profile", {
      method: "POST",
      headers: {
        cookie: "access_token=tok",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ displayName: "Acme" }),
    })

    const res = await POST(req, ctx(["v1", "account", "profile"]))
    expect(res.status).toBe(201)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})
