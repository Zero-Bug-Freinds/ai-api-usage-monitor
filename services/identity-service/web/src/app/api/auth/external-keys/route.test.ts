import { afterEach, describe, expect, it, vi } from "vitest"

import { GET, POST } from "./route"

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

function getRequest(cookie?: string) {
  return new Request("http://localhost/api/auth/external-keys", {
    method: "GET",
    headers: {
      Accept: "application/json",
      ...(cookie ? { cookie } : {}),
    },
  })
}

afterEach(() => {
  vi.restoreAllMocks()
  delete process.env.IDENTITY_SERVICE_URL
})

describe("GET /api/auth/external-keys (route handler)", () => {
  it("returns 401 when access_token cookie is missing", async () => {
    const res = await GET(getRequest())
    expect(res.status).toBe(401)
    const json = (await res.json()) as { success: boolean; data: null }
    expect(json.success).toBe(false)
    expect(json.data).toBeNull()
    expect(res.headers.get("cache-control")).toBe("no-store")
  })

  it("returns 500 when IDENTITY_SERVICE_URL is missing", async () => {
    const res = await GET(getRequest("access_token=test-token-value"))
    expect(res.status).toBe(500)
    const json = (await res.json()) as { success: boolean; message: string }
    expect(json.success).toBe(false)
    expect(json.message).toContain("IDENTITY_SERVICE_URL")
    expect(res.headers.get("cache-control")).toBe("no-store")
  })

  it("returns 200 and passes through valid list data from identity", async () => {
    process.env.IDENTITY_SERVICE_URL = "http://localhost:8080"

    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      expect(url).toBe("http://localhost:8080/api/auth/external-keys")
      expect(init?.method).toBe("GET")
      expect(init?.headers).toMatchObject({
        Authorization: "Bearer test-token-value",
        Accept: "application/json",
      })

      return new Response(
        JSON.stringify({
          success: true,
          message: "조회되었습니다",
          data: [
            {
              id: 1,
              provider: "OPENAI",
              alias: "키 1",
              createdAt: "2026-03-30T00:00:00Z",
            },
          ],
        }),
        { status: 200, headers: { "Content-Type": "application/json" } }
      )
    })
    vi.stubGlobal("fetch", fetchMock)

    const res = await GET(getRequest("access_token=test-token-value"))

    expect(res.status).toBe(200)
    const json = (await res.json()) as {
      success: boolean
      data: Array<{ id: number; provider: string }>
    }
    expect(json.success).toBe(true)
    expect(json.data).toHaveLength(1)
    expect(json.data[0].id).toBe(1)
    expect(res.headers.get("cache-control")).toBe("no-store")
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it("passes through upstream 401 JSON body", async () => {
    process.env.IDENTITY_SERVICE_URL = "http://localhost:8080"
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        return new Response(
          JSON.stringify({ success: false, message: "세션이 만료되었습니다", data: null }),
          { status: 401, headers: { "Content-Type": "application/json" } }
        )
      })
    )

    const res = await GET(getRequest("access_token=test-token-value"))
    expect(res.status).toBe(401)
    const json = (await res.json()) as { success: boolean; message: string; data: null }
    expect(json.success).toBe(false)
    expect(json.message).toContain("만료")
    expect(json.data).toBeNull()
    expect(res.headers.get("cache-control")).toBe("no-store")
  })

  it("passes through upstream non-401 error JSON body", async () => {
    process.env.IDENTITY_SERVICE_URL = "http://localhost:8080"
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        return new Response(
          JSON.stringify({ error: "forbidden", detail: "권한 없음" }),
          { status: 403, headers: { "Content-Type": "application/json" } }
        )
      })
    )

    const res = await GET(getRequest("access_token=test-token-value"))
    expect(res.status).toBe(403)
    const json = (await res.json()) as { error: string }
    expect(json.error).toBe("forbidden")
    expect(res.headers.get("cache-control")).toBe("no-store")
  })

  it("returns 502 when upstream error has no parseable JSON body", async () => {
    process.env.IDENTITY_SERVICE_URL = "http://localhost:8080"
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        return new Response("Internal Server Error", {
          status: 500,
          headers: { "Content-Type": "text/plain" },
        })
      })
    )

    const res = await GET(getRequest("access_token=test-token-value"))
    expect(res.status).toBe(502)
    const json = (await res.json()) as { success: boolean; message: string; data: null }
    expect(json.success).toBe(false)
    expect(json.data).toBeNull()
    expect(res.headers.get("cache-control")).toBe("no-store")
  })

  it("returns 502 when Identity is unreachable", async () => {
    process.env.IDENTITY_SERVICE_URL = "http://localhost:8080"
    vi.stubGlobal("fetch", vi.fn(async () => Promise.reject(new Error("ECONNREFUSED"))))

    const res = await GET(getRequest("access_token=test-token-value"))
    expect(res.status).toBe(502)
    const json = (await res.json()) as { success: boolean; data: null }
    expect(json.success).toBe(false)
    expect(json.data).toBeNull()
    expect(res.headers.get("cache-control")).toBe("no-store")
  })

  it("returns 502 when success response data shape is invalid", async () => {
    process.env.IDENTITY_SERVICE_URL = "http://localhost:8080"
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        return new Response(
          JSON.stringify({
            success: true,
            message: "ok",
            data: [{ id: "not-a-number", provider: "OPENAI", alias: "a", createdAt: "2026-01-01T00:00:00Z" }],
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        )
      })
    )

    const res = await GET(getRequest("access_token=test-token-value"))
    expect(res.status).toBe(502)
    const json = (await res.json()) as { success: boolean; message: string; data: null }
    expect(json.success).toBe(false)
    expect(json.message).toContain("형식")
    expect(json.data).toBeNull()
    expect(res.headers.get("cache-control")).toBe("no-store")
  })
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
        JSON.stringify({
          provider: "OPENAI",
          externalKey: "sk-live",
          alias: "OpenAI 키 1",
          monthlyBudgetUsd: 30.25,
        })
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
            monthlyBudgetUsd: 30.25,
          },
        }),
        { status: 201, headers: { "Content-Type": "application/json" } }
      )
    })
    vi.stubGlobal("fetch", fetchMock)

    const res = await POST(
      jsonRequest(
        { provider: "OPENAI", externalKey: "sk-live", alias: "OpenAI 키 1", monthlyBudgetUsd: 30.25 },
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

