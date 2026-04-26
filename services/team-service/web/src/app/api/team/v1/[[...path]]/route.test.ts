import { afterEach, describe, expect, it, vi } from "vitest"

import { GET, POST } from "./route"

afterEach(() => {
  vi.restoreAllMocks()
  delete process.env.GATEWAY_URL
})

function ctx(path?: string[]) {
  return { params: Promise.resolve({ path }) }
}

describe("team-web BFF route", () => {
  it("returns 401 when cookie is missing", async () => {
    process.env.GATEWAY_URL = "http://localhost:8888"
    const req = new Request("http://localhost/api/team/v1/me/teams", { method: "GET" })
    const res = await GET(req, ctx(["me", "teams"]))
    expect(res.status).toBe(401)
  })

  it("proxies GET to team-service", async () => {
    process.env.GATEWAY_URL = "http://localhost:8888"
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      expect(url).toBe("http://localhost:8888/api/team/v1/me/teams")
      expect(init?.method).toBe("GET")
      const headers = new Headers(init?.headers)
      expect(headers.get("authorization")).toBe("Bearer t")
      return new Response(JSON.stringify({ success: true, message: "ok", data: [] }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    })
    vi.stubGlobal("fetch", fetchMock)
    const req = new Request("http://localhost/api/team/v1/me/teams", {
      method: "GET",
      headers: { cookie: "access_token=t" },
    })
    const res = await GET(req, ctx(["me", "teams"]))
    expect(res.status).toBe(200)
  })

  it("proxies POST body to team-service", async () => {
    process.env.GATEWAY_URL = "http://localhost:8888"
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      expect(url).toBe("http://localhost:8888/api/team/v1/teams")
      expect(init?.method).toBe("POST")
      return new Response(JSON.stringify({ success: true, message: "created", data: { id: "1", name: "A" } }), {
        status: 201,
        headers: { "Content-Type": "application/json" },
      })
    })
    vi.stubGlobal("fetch", fetchMock)
    const req = new Request("http://localhost/api/team/v1/teams", {
      method: "POST",
      headers: {
        cookie: "access_token=t",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ name: "A" }),
    })
    const res = await POST(req, ctx(["teams"]))
    expect(res.status).toBe(201)
  })

  it("forwards incoming Authorization header as-is", async () => {
    process.env.GATEWAY_URL = "http://localhost:8888"
    const fetchMock = vi.fn(async (_url: string, init?: RequestInit) => {
      const headers = new Headers(init?.headers)
      expect(headers.get("authorization")).toBe("Bearer forwarded-token")
      return new Response(JSON.stringify({ success: true, message: "ok", data: [] }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    })
    vi.stubGlobal("fetch", fetchMock)
    const req = new Request("http://localhost/api/team/v1/me/teams", {
      method: "GET",
      headers: { authorization: "Bearer forwarded-token" },
    })
    const res = await GET(req, ctx(["me", "teams"]))
    expect(res.status).toBe(200)
  })
})
