import { afterEach, describe, expect, it, vi } from "vitest";

import { getUsageDashboardHref } from "./usage-dashboard-href";

describe("getUsageDashboardHref", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it("returns root /dashboard when neither usage nor identity origin is set", () => {
    vi.stubEnv("NEXT_PUBLIC_USAGE_WEB_ORIGIN", "");
    vi.stubEnv("NEXT_PUBLIC_IDENTITY_WEB_ORIGIN", "");
    vi.stubEnv("NEXT_PUBLIC_USAGE_BASE_PATH", "/dashboard");
    expect(getUsageDashboardHref()).toBe("/dashboard");
  });

  it("uses NEXT_PUBLIC_IDENTITY_WEB_ORIGIN + base path when usage origin is unset (shell on :3000)", () => {
    vi.stubEnv("NEXT_PUBLIC_USAGE_WEB_ORIGIN", "");
    vi.stubEnv("NEXT_PUBLIC_IDENTITY_WEB_ORIGIN", "http://localhost:3000");
    vi.stubEnv("NEXT_PUBLIC_USAGE_BASE_PATH", "/dashboard");
    expect(getUsageDashboardHref()).toBe("http://localhost:3000/dashboard");
  });

  it("prefers usage origin over identity when both are set", () => {
    vi.stubEnv("NEXT_PUBLIC_USAGE_WEB_ORIGIN", "http://localhost:3001");
    vi.stubEnv("NEXT_PUBLIC_IDENTITY_WEB_ORIGIN", "http://localhost:3000");
    vi.stubEnv("NEXT_PUBLIC_USAGE_BASE_PATH", "/dashboard");
    expect(getUsageDashboardHref()).toBe("http://localhost:3001/dashboard");
  });

  it("does not put dashboard under /billing when usage URL mistakenly includes /billing", () => {
    vi.stubEnv("NEXT_PUBLIC_USAGE_WEB_ORIGIN", "http://localhost:3001/billing");
    vi.stubEnv("NEXT_PUBLIC_IDENTITY_WEB_ORIGIN", "http://localhost:3000");
    vi.stubEnv("NEXT_PUBLIC_USAGE_BASE_PATH", "/dashboard");
    expect(getUsageDashboardHref()).toBe("http://localhost:3001/dashboard");
  });

  it("respects NEXT_PUBLIC_USAGE_BASE_PATH with identity origin", () => {
    vi.stubEnv("NEXT_PUBLIC_USAGE_WEB_ORIGIN", "");
    vi.stubEnv("NEXT_PUBLIC_IDENTITY_WEB_ORIGIN", "http://localhost:3000");
    vi.stubEnv("NEXT_PUBLIC_USAGE_BASE_PATH", "/usage");
    expect(getUsageDashboardHref()).toBe("http://localhost:3000/usage");
  });

  it("does not put dashboard under /billing when identity URL mistakenly includes /billing", () => {
    vi.stubEnv("NEXT_PUBLIC_USAGE_WEB_ORIGIN", "");
    vi.stubEnv("NEXT_PUBLIC_IDENTITY_WEB_ORIGIN", "http://localhost:3000/billing");
    vi.stubEnv("NEXT_PUBLIC_USAGE_BASE_PATH", "/dashboard");
    expect(getUsageDashboardHref()).toBe("http://localhost:3000/dashboard");
  });

  it("joins origin and base path when usage runs on another origin", () => {
    vi.stubEnv("NEXT_PUBLIC_USAGE_WEB_ORIGIN", "http://localhost:3001");
    vi.stubEnv("NEXT_PUBLIC_USAGE_BASE_PATH", "/dashboard");
    expect(getUsageDashboardHref()).toBe("http://localhost:3001/dashboard");
  });

  it("strips trailing slashes from origin and path", () => {
    vi.stubEnv("NEXT_PUBLIC_USAGE_WEB_ORIGIN", "http://localhost:3001/");
    vi.stubEnv("NEXT_PUBLIC_USAGE_BASE_PATH", "/dashboard/");
    expect(getUsageDashboardHref()).toBe("http://localhost:3001/dashboard");
  });
});
