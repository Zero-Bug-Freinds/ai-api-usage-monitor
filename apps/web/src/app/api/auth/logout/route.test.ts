import { afterEach, describe, expect, it, vi } from "vitest"

import { POST } from "./route"

function postRequest(cookie?: string) {
  return new Request("http://localhost/api/auth/logout", {
    method: "POST",
    headers: cookie ? { cookie } : {},
  })
}

afterEach(() => {
  vi.restoreAllMocks()
  delete process.env.IDENTITY_SERVICE_URL
})

describe("POST /api/auth/logout (route handler)", () => {
  it("returns 200 and clears access_token cookie without calling Identity when URL is unset", async () => {
    const res = await POST(postRequest("access_token=abc"))
    expect(res.status).toBe(200)
    const json = (await res.json()) as { success: boolean; message: string; data: null }
    expect(json.success).toBe(true)
    expect(json.data).toBeNull()
    expect(json.message).toContain("로그아웃")

    const setCookie = res.headers.get("set-cookie") ?? ""
    expect(setCookie).toMatch(/access_token=/)
    expect(setCookie).toMatch(/Max-Age=0|expires=Thu, 01 Jan 1970/i)
    expect(res.headers.get("cache-control")).toBe("no-store")
  })

  it("POSTs to Identity logout with Bearer when cookie and IDENTITY_SERVICE_URL are present", async () => {
    process.env.IDENTITY_SERVICE_URL = "http://localhost:8080"
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      expect(url).toBe("http://localhost:8080/api/auth/logout")
      expect(init?.method).toBe("POST")
      expect(init?.headers).toMatchObject({
        Authorization: "Bearer my-jwt",
        Accept: "application/json",
      })
      return new Response(JSON.stringify({ success: true, message: "ok", data: null }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    })
    vi.stubGlobal("fetch", fetchMock)

    const res = await POST(postRequest("access_token=my-jwt"))
    expect(res.status).toBe(200)
    expect(fetchMock).toHaveBeenCalledTimes(1)

    const setCookie = res.headers.get("set-cookie") ?? ""
    expect(setCookie).toMatch(/access_token=/)
  })

  it("calls Identity logout without Authorization when cookie is missing", async () => {
    process.env.IDENTITY_SERVICE_URL = "http://localhost:8080"
    const fetchMock = vi.fn(async (_url: string, init?: RequestInit) => {
      const headers = init?.headers as Record<string, string> | undefined
      expect(headers?.Authorization).toBeUndefined()
      expect(headers?.Accept).toBe("application/json")
      return new Response(null, { status: 200 })
    })
    vi.stubGlobal("fetch", fetchMock)

    const res = await POST(postRequest())
    expect(res.status).toBe(200)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(res.headers.get("set-cookie")).toMatch(/access_token=/)
  })

  it("still clears cookie when Identity logout fetch fails", async () => {
    process.env.IDENTITY_SERVICE_URL = "http://localhost:8080"
    vi.stubGlobal("fetch", vi.fn(async () => Promise.reject(new Error("ECONNREFUSED"))))

    const res = await POST(postRequest("access_token=t"))
    expect(res.status).toBe(200)
    expect(res.headers.get("set-cookie")).toMatch(/access_token=/)
  })

  it("normalizes IDENTITY_SERVICE_URL trailing slashes", async () => {
    process.env.IDENTITY_SERVICE_URL = "http://localhost:8080///"
    const fetchMock = vi.fn(async (url: string) => {
      expect(url).toBe("http://localhost:8080/api/auth/logout")
      return new Response(null, { status: 200 })
    })
    vi.stubGlobal("fetch", fetchMock)

    await POST(postRequest())
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})
