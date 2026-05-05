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

describe("GET /api/usage/team/bff/[[...path]] (팀 BFF proxy)", () => {
  it("returns 401 when access_token cookie is missing", async () => {
    const req = new Request("http://localhost/dashboard/api/usage/team/bff/dashboard?mode=TEAM_TOTAL&teamId=1", {
      headers: { host: "localhost" },
    })

    const res = await GET(req, ctx(["dashboard"]))
    expect(res.status).toBe(401)
  })

  it("proxies to gateway /api/v1/usage/bff/dashboard?... with query preserved", async () => {
    process.env.API_GATEWAY_URL = "http://localhost:8080"
    process.env.GATEWAY_DEV_MODE = "false"
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      expect(url).toBe("http://localhost:8080/api/v1/usage/bff/dashboard?mode=TEAM_TOTAL&teamId=1")
      const h = init?.headers as Headers
      expect(h.get("Authorization")).toBe("Bearer t")
      return new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    })
    vi.stubGlobal("fetch", fetchMock)

    const req = new Request(
      "http://localhost/dashboard/api/usage/team/bff/dashboard?mode=TEAM_TOTAL&teamId=1",
      { headers: { cookie: "access_token=t" } }
    )

    const res = await GET(req, ctx(["dashboard"]))
    expect(res.status).toBe(200)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it("요청 URL에 /dashboard/dashboard/ 이중 접두가 없음", async () => {
    process.env.API_GATEWAY_URL = "http://localhost:8080"
    process.env.GATEWAY_DEV_MODE = "false"
    const fetchMock = vi.fn(async () => new Response("{}", { status: 200 }))
    vi.stubGlobal("fetch", fetchMock)

    const path =
      "http://localhost/dashboard/api/usage/team/bff/dashboard?teamId=x"
    expect(path).not.toContain("/dashboard/dashboard/")
    const req = new Request(path, { headers: { cookie: "access_token=t" } })
    await GET(req, ctx(["dashboard"]))
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/usage/bff/dashboard"),
      expect.any(Object)
    )
  })

  it("returns 404 when path segments are empty", async () => {
    process.env.API_GATEWAY_URL = "http://localhost:8080"
    const req = new Request("http://localhost/dashboard/api/usage/team/bff/", {
      headers: { cookie: "access_token=t" },
    })

    const res = await GET(req, ctx(undefined))
    expect(res.status).toBe(404)
  })
})
