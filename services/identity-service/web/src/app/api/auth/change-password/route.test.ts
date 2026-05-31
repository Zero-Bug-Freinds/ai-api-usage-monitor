import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

const cookieStore = {
  get: vi.fn(),
}

vi.mock("next/headers", () => ({
  cookies: vi.fn(async () => cookieStore),
}))

import { POST } from "./route"

const originalGatewayUrl = process.env.GATEWAY_URL
const originalWebGatewayUrl = process.env.WEB_GATEWAY_URL

function jsonRequest(body: unknown, cookie?: string) {
  return new Request("http://localhost/api/auth/change-password", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(cookie ? { cookie } : {}),
    },
    body: JSON.stringify(body),
  })
}

beforeEach(() => {
  process.env.GATEWAY_URL = "http://localhost:8888"
  delete process.env.WEB_GATEWAY_URL
  cookieStore.get.mockReset()
  cookieStore.get.mockReturnValue(undefined)
})

afterEach(() => {
  vi.restoreAllMocks()
  if (originalGatewayUrl === undefined) {
    delete process.env.GATEWAY_URL
  } else {
    process.env.GATEWAY_URL = originalGatewayUrl
  }
  if (originalWebGatewayUrl === undefined) {
    delete process.env.WEB_GATEWAY_URL
  } else {
    process.env.WEB_GATEWAY_URL = originalWebGatewayUrl
  }
})

describe("POST /api/auth/change-password (route handler)", () => {
  it("returns 401 when access token is missing", async () => {
    const res = await POST(jsonRequest({ currentPassword: "abc123!@", newPassword: "next123!@", newPasswordConfirm: "next123!@" }))

    expect(res.status).toBe(401)
    const json = (await res.json()) as { success: boolean; data: null }
    expect(json.success).toBe(false)
    expect(json.data).toBeNull()
  })

  it("returns 400 for invalid payload", async () => {
    const res = await POST(jsonRequest({ currentPassword: "", newPassword: "bad", newPasswordConfirm: "nope" }, "access_token=t"))

    expect(res.status).toBe(400)
    const json = (await res.json()) as { success: boolean; message: string }
    expect(json.success).toBe(false)
    expect(json.message.length).toBeGreaterThan(0)
  })

  it("proxies validated payload to gateway and returns success", async () => {
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      expect(url).toBe("http://localhost:8888/api/identity/auth/change-password")
      expect(init?.method).toBe("POST")
      expect(init?.headers).toMatchObject({
        Authorization: "Bearer test-token",
        "Content-Type": "application/json",
        Accept: "application/json",
      })
      expect(init?.body).toBe(
        JSON.stringify({
          currentPassword: "abc123!@",
          newPassword: "next123!@",
          newPasswordConfirm: "next123!@",
        })
      )
      return new Response(
        JSON.stringify({
          success: true,
          message: "비밀번호가 변경되었습니다",
          data: null,
        }),
        { status: 200, headers: { "Content-Type": "application/json" } }
      )
    })
    vi.stubGlobal("fetch", fetchMock)

    const res = await POST(
      jsonRequest(
        {
          currentPassword: "abc123!@",
          newPassword: "next123!@",
          newPasswordConfirm: "next123!@",
        },
        "access_token=test-token"
      )
    )

    expect(res.status).toBe(200)
    expect(res.headers.get("cache-control")).toBe("no-store")
    const json = (await res.json()) as { success: boolean; message: string; data: null }
    expect(json.success).toBe(true)
    expect(json.message).toBe("비밀번호가 변경되었습니다")
    expect(json.data).toBeNull()
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it("returns 502 when upstream 200 body is not a success response", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        return new Response(
          JSON.stringify({
            success: false,
            message: "unexpected",
            data: null,
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        )
      })
    )

    const res = await POST(
      jsonRequest(
        {
          currentPassword: "abc123!@",
          newPassword: "next123!@",
          newPasswordConfirm: "next123!@",
        },
        "access_token=test-token"
      )
    )

    expect(res.status).toBe(502)
    const json = (await res.json()) as { success: boolean }
    expect(json.success).toBe(false)
  })
})
