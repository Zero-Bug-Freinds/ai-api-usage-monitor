import { afterEach, describe, expect, it, vi } from "vitest"

import { POST } from "./route"

function jsonRequest(body: unknown, cookie?: string) {
  return new Request("http://localhost/api/auth/external-keys", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(cookie ? { cookie } : {}),
    },
    body: JSON.stringify(body),
  })
}

afterEach(() => {
  vi.restoreAllMocks()
  delete process.env.IDENTITY_SERVICE_URL
})

describe("POST /api/auth/external-keys (route handler)", () => {
  it("returns 401 when access_token cookie is missing", async () => {
    const res = await POST(jsonRequest({ provider: "OPENAI", externalKey: "k", alias: "a" }))
    expect(res.status).toBe(401)
    const json = (await res.json()) as { success: boolean; data: null }
    expect(json.success).toBe(false)
    expect(json.data).toBeNull()
    expect(res.headers.get("cache-control")).toBe("no-store")
  })

  it("returns 500 when IDENTITY_SERVICE_URL is missing", async () => {
    const res = await POST(
      jsonRequest(
        { provider: "OPENAI", externalKey: "k", alias: "a" },
        "access_token=test-token-value"
      )
    )
    expect(res.status).toBe(500)
    const json = (await res.json()) as { success: boolean; message: string }
    expect(json.success).toBe(false)
    expect(json.message).toContain("IDENTITY_SERVICE_URL")
    expect(res.headers.get("cache-control")).toBe("no-store")
  })

  it("returns 400 for invalid JSON body", async () => {
    process.env.IDENTITY_SERVICE_URL = "http://localhost:8080"
    const req = new Request("http://localhost/api/auth/external-keys", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        cookie: "access_token=test-token-value",
      },
      body: "{not-json",
    })

    const res = await POST(req)
    expect(res.status).toBe(400)
    const json = (await res.json()) as { success: boolean; data: null }
    expect(json.success).toBe(false)
    expect(json.data).toBeNull()
    expect(res.headers.get("cache-control")).toBe("no-store")
  })

  it("passes through 201 success payload from identity", async () => {
    process.env.IDENTITY_SERVICE_URL = "http://localhost:8080"

    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      expect(url).toBe("http://localhost:8080/api/auth/external-keys")
      expect(init?.method).toBe("POST")
      expect(init?.headers).toMatchObject({
        Authorization: "Bearer test-token-value",
        Accept: "application/json",
        "Content-Type": "application/json",
      })
      expect(init?.body).toBe(
        JSON.stringify({ provider: "OPENAI", externalKey: "sk-live", alias: "OpenAI 키 1" })
      )

      return new Response(
        JSON.stringify({
          success: true,
          message: "등록되었습니다",
          data: {
            id: 123,
            provider: "OPENAI",
            alias: "OpenAI 키 1",
            createdAt: "2026-03-30T00:00:00Z",
          },
        }),
        { status: 201, headers: { "Content-Type": "application/json" } }
      )
    })
    vi.stubGlobal("fetch", fetchMock)

    const res = await POST(
      jsonRequest(
        { provider: "OPENAI", externalKey: "sk-live", alias: "OpenAI 키 1" },
        "access_token=test-token-value"
      )
    )

    expect(res.status).toBe(201)
    const json = (await res.json()) as { success: boolean; data: { id: number } }
    expect(json.success).toBe(true)
    expect(json.data.id).toBe(123)
    expect(res.headers.get("cache-control")).toBe("no-store")
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it("passes through upstream 400 and safe message", async () => {
    process.env.IDENTITY_SERVICE_URL = "http://localhost:8080"
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        return new Response(
          JSON.stringify({ success: false, message: "잘못된 요청입니다", data: null }),
          { status: 400, headers: { "Content-Type": "application/json" } }
        )
      })
    )

    const res = await POST(
      jsonRequest(
        { provider: "OPENAI", externalKey: "sk", alias: "a" },
        "access_token=test-token-value"
      )
    )
    expect(res.status).toBe(400)
    const json = (await res.json()) as { success: boolean; message: string; data: null }
    expect(json.success).toBe(false)
    expect(json.data).toBeNull()
    expect(json.message).toContain("요청")
    expect(res.headers.get("cache-control")).toBe("no-store")
  })

  it("passes through upstream 409 and safe message", async () => {
    process.env.IDENTITY_SERVICE_URL = "http://localhost:8080"
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        return new Response(
          JSON.stringify({ success: false, message: "이미 등록된 키입니다", data: null }),
          { status: 409, headers: { "Content-Type": "application/json" } }
        )
      })
    )

    const res = await POST(
      jsonRequest(
        { provider: "OPENAI", externalKey: "sk", alias: "a" },
        "access_token=test-token-value"
      )
    )
    expect(res.status).toBe(409)
    const json = (await res.json()) as { success: boolean; message: string; data: null }
    expect(json.success).toBe(false)
    expect(json.data).toBeNull()
    expect(json.message).toContain("등록")
    expect(res.headers.get("cache-control")).toBe("no-store")
  })

  it("returns 502 when Identity is unreachable", async () => {
    process.env.IDENTITY_SERVICE_URL = "http://localhost:8080"
    vi.stubGlobal("fetch", vi.fn(async () => Promise.reject(new Error("ECONNREFUSED"))))

    const res = await POST(
      jsonRequest(
        { provider: "OPENAI", externalKey: "sk", alias: "a" },
        "access_token=test-token-value"
      )
    )
    expect(res.status).toBe(502)
    const json = (await res.json()) as { success: boolean; data: null }
    expect(json.success).toBe(false)
    expect(json.data).toBeNull()
    expect(res.headers.get("cache-control")).toBe("no-store")
  })
})

