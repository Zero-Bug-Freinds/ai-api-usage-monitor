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
  return new Request("http://localhost/api/auth/delete-account", {
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

describe("POST /api/auth/delete-account (route handler)", () => {
  it("returns 401 when access token is missing", async () => {
    const res = await POST(jsonRequest({ password: "abc123!@" }))

    expect(res.status).toBe(401)
    const json = (await res.json()) as { success: boolean; data: null }
    expect(json.success).toBe(false)
    expect(json.data).toBeNull()
  })

  it("returns 400 when password is missing", async () => {
    const res = await POST(jsonRequest({ password: "" }, "access_token=t"))

    expect(res.status).toBe(400)
    const json = (await res.json()) as { success: boolean; message: string }
    expect(json.success).toBe(false)
    expect(json.message.length).toBeGreaterThan(0)
  })

  it("proxies to gateway, clears cookie on upstream 200 success", async () => {
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      expect(url).toBe("http://localhost:8888/api/identity/auth/delete-account")
      expect(init?.method).toBe("POST")
      expect(init?.headers).toMatchObject({
        Authorization: "Bearer test-token",
        "Content-Type": "application/json",
        Accept: "application/json",
      })
      expect(init?.body).toBe(JSON.stringify({ password: "abc123!@" }))
      return new Response(
        JSON.stringify({
          success: true,
          message: "회원 탈퇴가 완료되었습니다. 계정에 다시 로그인할 수 없습니다.",
          data: null,
        }),
        { status: 200, headers: { "Content-Type": "application/json" } }
      )
    })
    vi.stubGlobal("fetch", fetchMock)

    const res = await POST(jsonRequest({ password: "abc123!@" }, "access_token=test-token"))

    expect(res.status).toBe(200)
    expect(res.headers.get("cache-control")).toBe("no-store")
    const setCookie = res.headers.get("set-cookie") ?? ""
    expect(setCookie.toLowerCase()).toContain("access_token=")
    const json = (await res.json()) as { success: boolean; message: string; data: null }
    expect(json.success).toBe(true)
    expect(json.message).toBe("회원 탈퇴가 완료되었습니다. 계정에 다시 로그인할 수 없습니다.")
    expect(json.data).toBeNull()
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it("forwards upstream error status when password is wrong", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        return new Response(
          JSON.stringify({
            success: false,
            message: "Invalid password",
            data: null,
          }),
          { status: 400, headers: { "Content-Type": "application/json" } }
        )
      })
    )

    const res = await POST(jsonRequest({ password: "wrong" }, "access_token=test-token"))

    expect(res.status).toBe(400)
    const json = (await res.json()) as { success: boolean; message: string }
    expect(json.success).toBe(false)
    expect(json.message).toBe("Invalid password")
  })
})
