import { afterEach, describe, expect, it, vi } from "vitest"

import { PUT } from "./route"

const context = { params: Promise.resolve({ id: "123" }) }

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

afterEach(() => {
  vi.restoreAllMocks()
  delete process.env.IDENTITY_SERVICE_URL
})

describe("PUT /api/auth/external-keys/[id] (route handler)", () => {
  it("returns 401 when access_token cookie is missing", async () => {
    const res = await PUT(putRequest({ alias: "새 별칭" }), context)
    expect(res.status).toBe(401)
  })

  it("returns 400 for invalid payload", async () => {
    process.env.IDENTITY_SERVICE_URL = "http://localhost:8080"
    const res = await PUT(putRequest({ alias: " ", externalKey: "sk-live" }, "access_token=test-token"), context)
    expect(res.status).toBe(400)
  })

  it("passes through upstream success response", async () => {
    process.env.IDENTITY_SERVICE_URL = "http://localhost:8080"
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

    const res = await PUT(putRequest({ alias: "새 별칭" }, "access_token=test-token"), context)
    expect(res.status).toBe(200)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it("passes update payload with alias and budget", async () => {
    process.env.IDENTITY_SERVICE_URL = "http://localhost:8080"
    const fetchMock = vi.fn(async (_url: string, init?: RequestInit) => {
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
