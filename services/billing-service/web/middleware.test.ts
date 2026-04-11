import { describe, expect, it } from "vitest";
import { NextRequest } from "next/server";

import { config, middleware } from "./middleware";

function makeRequest(pathname: string, cookieHeader?: string) {
  const url = `http://localhost:3003${pathname}`;
  return new NextRequest(url, {
    headers: cookieHeader ? { cookie: cookieHeader } : {},
  });
}

describe("middleware (Billing dashboard gate)", () => {
  it("continues when access_token cookie is present", () => {
    const res = middleware(makeRequest("/billing", "access_token=test-cookie-present"));
    expect(res.status).toBe(200);
  });

  it("redirects to /login with next= when cookie is missing", () => {
    const res = middleware(makeRequest("/billing/expenditure"));
    expect(res.status).toBe(307);
    const location = res.headers.get("location") ?? "";
    expect(location).toMatch(/\/login/);
    expect(location).toContain("next=");
    expect(location).toContain(encodeURIComponent("/billing/expenditure"));
  });

  it("redirects when access_token is empty", () => {
    const res = middleware(makeRequest("/billing", "access_token="));
    expect(res.status).toBe(307);
    expect(res.headers.get("location")).toMatch(/\/login/);
  });

  it("does not gate BFF /api/expenditure paths", () => {
    const res = middleware(makeRequest("/billing/api/expenditure/summary"));
    expect(res.status).toBe(200);
  });
});

describe("middleware config.matcher", () => {
  it("matches only basePath UI routes (excludes /billing/_next/* and /billing/api/*)", () => {
    expect(config.matcher).toEqual(["/billing", "/billing/", "/billing/((?!_next/|api/).+)"]);
  });
});
