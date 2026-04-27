import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import { DELETE, PUT } from "./route"

const context = { params: Promise.resolve({ id: "123" }) }
const originalGatewayUrl = process.env.GATEWAY_URL

function putRequest(body: unknown, cookie?: string) {
  return new Request("http://localhost/api/auth/external-keys/123", {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
      ...(cookie ? { cookie } : {}),
    },
    body: JSON.stringify(body),
  })
}

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

describe("DELETE /api/auth/external-keys/[id] (route handler)", () => {
  it("forwards query string to upstream", async () => {
    const fetchMock = vi.fn(async (url: string) => {
      expect(url).toBe("http://localhost:8888/api/identity/auth/external-keys/123?gracePeriodDays=14&retainLogs=false")
      return new Response(JSON.stringify({ success: true, message: "ok", data: null }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    })
    vi.stubGlobal("fetch", fetchMock)

    const req = new Request("http://localhost/api/auth/external-keys/123?gracePeriodDays=14&retainLogs=false", {
      method: "DELETE",
      headers: { cookie: "access_token=t", Accept: "application/json" },
    })
    const res = await DELETE(req, context)
    expect(res.status).toBe(200)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it("forwards gracePeriodDays=0 as immediate deletion", async () => {
    const fetchMock = vi.fn(async (url: string) => {
      expect(url).toBe("http://localhost:8888/api/identity/auth/external-keys/123?gracePeriodDays=0")
      return new Response(JSON.stringify({ success: true, message: "ok", data: null }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    })
    vi.stubGlobal("fetch", fetchMock)

    const req = new Request("http://localhost/api/auth/external-keys/123?gracePeriodDays=0", {
      method: "DELETE",
      headers: { cookie: "access_token=t", Accept: "application/json" },
    })
    const res = await DELETE(req, context)
    expect(res.status).toBe(200)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it("forwards retainLogs with immediate deletion query", async () => {
    const fetchMock = vi.fn(async (url: string) => {
      expect(url).toBe(
        "http://localhost:8888/api/identity/auth/external-keys/123?gracePeriodDays=0&retainLogs=false"
      )
      return new Response(JSON.stringify({ success: true, message: "ok", data: null }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    })
    vi.stubGlobal("fetch", fetchMock)

    const req = new Request(
      "http://localhost/api/auth/external-keys/123?gracePeriodDays=0&retainLogs=false",
      {
        method: "DELETE",
        headers: { cookie: "access_token=t", Accept: "application/json" },
      }
    )
    const res = await DELETE(req, context)
    expect(res.status).toBe(200)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})

describe("PUT /api/auth/external-keys/[id] (route handler)", () => {
  it("returns 401 when access_token cookie is missing", async () => {
    const res = await PUT(putRequest({ alias: "새 별칭", monthlyBudgetUsd: 10 }), context)
    expect(res.status).toBe(401)
  })

  it("returns 400 for invalid payload", async () => {
    const res = await PUT(putRequest({ alias: " ", externalKey: "sk-live" }, "access_token=test-token"), context)
    expect(res.status).toBe(400)
  })

  it("passes through upstream success response", async () => {
    const fetchMock = vi.fn(async () => {
      return new Response(
        JSON.stringify({
          success: true,
          message: "외부 API 키가 수정되었습니다",
          data: {
            id: 123,
            provider: "OPENAI",
            alias: "새 별칭",
            createdAt: "2026-01-01T00:00:00Z",
            monthlyBudgetUsd: 40,
          },
        }),
        { status: 200, headers: { "Content-Type": "application/json" } }
      )
    })
    vi.stubGlobal("fetch", fetchMock)

    const res = await PUT(putRequest({ alias: "새 별칭", monthlyBudgetUsd: 40 }, "access_token=test-token"), context)
    expect(res.status).toBe(200)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it("passes update payload with alias and budget", async () => {
    const fetchMock = vi.fn(async (_url: string, init?: RequestInit) => {
      expect(_url).toBe("http://localhost:8888/api/identity/auth/external-keys/123")
      expect(init?.body).toBe(JSON.stringify({ alias: "새 별칭", monthlyBudgetUsd: 40 }))
      return new Response(
        JSON.stringify({
          success: true,
          message: "외부 API 키가 수정되었습니다",
          data: { id: 123, provider: "OPENAI", alias: "새 별칭", createdAt: "2026-01-01T00:00:00Z", monthlyBudgetUsd: 40 },
        }),
        { status: 200, headers: { "Content-Type": "application/json" } }
      )
    })
    vi.stubGlobal("fetch", fetchMock)

    const res = await PUT(
      putRequest({ alias: "새 별칭", monthlyBudgetUsd: 40 }, "access_token=test-token"),
      context
    )
    expect(res.status).toBe(200)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})
