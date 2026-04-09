import { describe, expect, it } from "vitest"
import { NextRequest } from "next/server"

import { config, middleware } from "./middleware"

function makeRequest(pathname: string, cookieHeader?: string) {
  const url = `http://localhost:3000${pathname}`
  return new NextRequest(url, {
    headers: cookieHeader ? { cookie: cookieHeader } : {},
  })
}

describe("middleware (auth-required gate)", () => {
  it("continues when access_token cookie is present", () => {
    const res = middleware(makeRequest("/settings", "access_token=test-cookie-present"))
    expect(res.status).toBe(200)
  })

  it("redirects to /login with next= when cookie is missing", () => {
    const res = middleware(makeRequest("/settings/profile"))
    expect(res.status).toBe(307)
    const location = res.headers.get("location") ?? ""
    expect(location).toMatch(/\/login/)
    expect(location).toContain("next=")
    expect(location).toContain(encodeURIComponent("/settings/profile"))
  })

  it("redirects when access_token is empty (malformed cookie edge)", () => {
    const res = middleware(makeRequest("/settings", "access_token="))
    expect(res.status).toBe(307)
    expect(res.headers.get("location")).toMatch(/\/login/)
  })

  it("applies to organizations prefix", () => {
    const org = middleware(makeRequest("/organizations/acme/billing"))
    expect(org.status).toBe(307)
    expect(org.headers.get("location")).toContain(encodeURIComponent("/organizations/acme/billing"))
  })

  it("applies to teams prefix", () => {
    const team = middleware(makeRequest("/teams"))
    expect(team.status).toBe(307)
    expect(team.headers.get("location")).toContain(encodeURIComponent("/teams"))
  })
})

describe("middleware config.matcher", () => {
  it("lists auth-required App Router prefixes (keep in sync with app/*/[[...path]])", () => {
    expect(config.matcher).toEqual(["/settings/:path*", "/organizations/:path*", "/teams/:path*"])
  })
})
