import { describe, expect, it } from "vitest"

import { GET } from "./route"

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

  it("returns 200 when access_token cookie exists", async () => {
    const req = new Request("http://localhost/api/auth/session", {
      method: "GET",
      headers: { cookie: "access_token=test-token-value" },
    })

    const res = await GET(req)
    expect(res.status).toBe(200)
    const json = (await res.json()) as { success: boolean; data: { authenticated: boolean } }
    expect(json.success).toBe(true)
    expect(json.data.authenticated).toBe(true)
    expect(res.headers.get("cache-control")).toBe("no-store")
  })
})
