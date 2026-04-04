import { describe, expect, it, vi } from "vitest"

import { apiFetch } from "./client-fetch"

describe("apiFetch", () => {
  it("returns response/json for normal success", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        return new Response(
          JSON.stringify({
            success: true,
            message: "ok",
            data: null,
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        )
      })
    )

    const result = await apiFetch<null>("/api/sample", { method: "GET" })
    expect(result.response.status).toBe(200)
    expect(result.json?.success).toBe(true)
  })

  it("does not trigger redirect callback for 401 when authRequired is false", async () => {
    const onUnauthorized = vi.fn()
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        return new Response(
          JSON.stringify({
            success: false,
            message: "unauthorized",
            data: null,
          }),
          { status: 401, headers: { "Content-Type": "application/json" } }
        )
      })
    )

    const result = await apiFetch<null>("/api/public", { method: "GET" }, { onUnauthorized })
    expect(result.response.status).toBe(401)
    expect(onUnauthorized).not.toHaveBeenCalled()
  })

  it("triggers redirect callback for 401 when authRequired is true", async () => {
    const onUnauthorized = vi.fn()
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        return new Response(
          JSON.stringify({
            success: false,
            message: "unauthorized",
            data: null,
          }),
          { status: 401, headers: { "Content-Type": "application/json" } }
        )
      })
    )

    const result = await apiFetch<null>("/api/protected", { method: "GET" }, { authRequired: true, onUnauthorized })
    expect(result.response.status).toBe(401)
    expect(onUnauthorized).toHaveBeenCalledTimes(1)
  })
})
