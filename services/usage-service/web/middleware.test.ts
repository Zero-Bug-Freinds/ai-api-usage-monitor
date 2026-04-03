import { describe, expect, it } from "vitest"
import { NextRequest } from "next/server"

import { config, middleware } from "./middleware"

function makeRequest(pathname: string, cookieHeader?: string) {
  const url = `http://localhost:3001${pathname}`
  return new NextRequest(url, {
    headers: cookieHeader ? { cookie: cookieHeader } : {},
  })
}

describe("middleware (Usage dashboard gate)", () => {
  it("continues when access_token cookie is present", () => {
    const res = middleware(makeRequest("/dashboard", "access_token=test-cookie-present"))
    expect(res.status).toBe(200)
  })

  it("redirects to /login with next= when cookie is missing", () => {
    const res = middleware(makeRequest("/dashboard/usage"))
    expect(res.status).toBe(307)
    const location = res.headers.get("location") ?? ""
    expect(location).toMatch(/\/login/)
    expect(location).toContain("next=")
    expect(location).toContain(encodeURIComponent("/dashboard/usage"))
  })

  it("redirects when access_token is empty (malformed cookie edge)", () => {
    const res = middleware(makeRequest("/dashboard", "access_token="))
    expect(res.status).toBe(307)
    expect(res.headers.get("location")).toMatch(/\/login/)
  })

  it("does not gate BFF /api/usage paths", () => {
    const res = middleware(makeRequest("/dashboard/api/usage/logs"))
    expect(res.status).toBe(200)
  })
})

describe("middleware config.matcher", () => {
  it("matches dashboard HTML paths (excluding _next static)", () => {
    expect(config.matcher).toEqual(["/((?!_next/static|_next/image|favicon.ico|.*\\..*).*)"])
  })
})
