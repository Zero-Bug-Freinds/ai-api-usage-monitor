import { afterEach, describe, expect, it, vi } from "vitest"

import { POST } from "./route"

/** Identity SignupRequest 비밀번호 정책에 맞는 예시 */
const validPassword = "abc123!@"

function jsonRequest(body: unknown) {
  return new Request("http://localhost/api/auth/signup", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  })
}

afterEach(() => {
  vi.restoreAllMocks()
  delete process.env.IDENTITY_SERVICE_URL
})

describe("POST /api/auth/signup (route handler)", () => {
  it("returns 500 if IDENTITY_SERVICE_URL is missing", async () => {
    const res = await POST(jsonRequest({}))
    expect(res.status).toBe(500)
    const json = (await res.json()) as { success: boolean; message: string }
    expect(json.success).toBe(false)
    expect(json.message).toContain("IDENTITY_SERVICE_URL")
  })

  it("returns 400 for invalid JSON body", async () => {
    process.env.IDENTITY_SERVICE_URL = "http://localhost:8080"
    const req = new Request("http://localhost/api/auth/signup", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: "{not-json",
    })

    const res = await POST(req)
    expect(res.status).toBe(400)
  })

  it("returns 400 when validation fails", async () => {
    process.env.IDENTITY_SERVICE_URL = "http://localhost:8080"
    const res = await POST(
      jsonRequest({
        email: "bad",
        password: "123",
        passwordConfirm: "123",
        name: "",
        role: "USER",
      })
    )
    expect(res.status).toBe(400)
  })

  it("passes through 201 success payload from identity", async () => {
    process.env.IDENTITY_SERVICE_URL = "http://localhost:8080"

    const fetchMock = vi.fn(async () => {
      return new Response(
        JSON.stringify({
          success: true,
          message: "회원가입이 완료되었습니다",
          data: { userId: 1, email: "happy@test.com", name: "testDemo", role: "USER" },
        }),
        { status: 201, headers: { "Content-Type": "application/json" } }
      )
    })
    vi.stubGlobal("fetch", fetchMock)

    const body = {
      email: "happy@test.com",
      password: validPassword,
      passwordConfirm: validPassword,
      name: "testDemo",
      role: "USER",
    }

    const res = await POST(jsonRequest(body))

    expect(res.status).toBe(201)
    const json = (await res.json()) as { success: boolean; data: { userId: number } }
    expect(json.success).toBe(true)
    expect(json.data.userId).toBe(1)

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/auth/signup",
      expect.objectContaining({
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      })
    )
  })

  it("returns safe failure body when identity returns 409", async () => {
    process.env.IDENTITY_SERVICE_URL = "http://localhost:8080"

    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        return new Response(
          JSON.stringify({ success: false, message: "이미 사용 중인 이메일입니다", data: null }),
          { status: 409, headers: { "Content-Type": "application/json" } }
        )
      })
    )

    const res = await POST(
      jsonRequest({
        email: "happy@test.com",
        password: validPassword,
        passwordConfirm: validPassword,
        name: "testDemo",
        role: "USER",
      })
    )

    expect(res.status).toBe(409)
    const json = (await res.json()) as { success: boolean; message: string; data: null }
    expect(json.success).toBe(false)
    expect(json.data).toBeNull()
    expect(json.message).toContain("이메일")
  })
})
