import { describe, expect, it } from "vitest"
import {
  buildNotificationOutboundHeaders,
  parseNotificationHttpUpstream,
  parsePlatformUserIdFromAccessTokenJwt,
  parseSubjectEmailFromAccessTokenJwt,
  resolveNotificationUpstreamTarget,
} from "./notification-bff-proxy"

describe("parseNotificationHttpUpstream", () => {
  it("defaults to direct when unset", () => {
    expect(parseNotificationHttpUpstream(undefined)).toBe("direct")
  })

  it("accepts gateway and direct case-insensitively", () => {
    expect(parseNotificationHttpUpstream("GATEWAY")).toBe("gateway")
    expect(parseNotificationHttpUpstream("Direct")).toBe("direct")
  })
})

describe("resolveNotificationUpstreamTarget", () => {
  it("gateway branch builds /api/notification/... under API_GATEWAY_URL", () => {
    const r = resolveNotificationUpstreamTarget({
      upstream: "gateway",
      pathSegments: ["in-app-notifications"],
      search: "?limit=1",
      apiGatewayUrl: "http://gw:8080/",
      notificationServiceUrl: undefined,
    })
    expect(r).toEqual({
      ok: true,
      targetUrl: "http://gw:8080/api/notification/in-app-notifications?limit=1",
    })
  })

  it("direct branch uses NOTIFICATION_SERVICE_URL", () => {
    const r = resolveNotificationUpstreamTarget({
      upstream: "direct",
      pathSegments: ["in-app-notifications"],
      search: "",
      apiGatewayUrl: undefined,
      notificationServiceUrl: "http://nest:8096/api",
    })
    expect(r).toEqual({
      ok: true,
      targetUrl: "http://nest:8096/api/in-app-notifications",
    })
  })

  it("returns missing_api_gateway_url when gateway and no base", () => {
    const r = resolveNotificationUpstreamTarget({
      upstream: "gateway",
      pathSegments: ["x"],
      search: "",
      apiGatewayUrl: undefined,
      notificationServiceUrl: "http://n:1",
    })
    expect(r).toEqual({ ok: false, error: "missing_api_gateway_url" })
  })

  it("returns missing_notification_service_url when direct and no base", () => {
    const r = resolveNotificationUpstreamTarget({
      upstream: "direct",
      pathSegments: ["x"],
      search: "",
      apiGatewayUrl: "http://g:1",
      notificationServiceUrl: undefined,
    })
    expect(r).toEqual({ ok: false, error: "missing_notification_service_url" })
  })
})

describe("parseSubjectEmailFromAccessTokenJwt", () => {
  it("reads string sub (email) claim from JWT payload", () => {
    const payload = Buffer.from(JSON.stringify({ sub: "test2@test.com" })).toString("base64url")
    const jwt = `a.${payload}.c`
    expect(parseSubjectEmailFromAccessTokenJwt(jwt)).toBe("test2@test.com")
  })

  it("returns null when sub is missing", () => {
    const payload = Buffer.from(JSON.stringify({ userId: "42" })).toString("base64url")
    const jwt = `a.${payload}.c`
    expect(parseSubjectEmailFromAccessTokenJwt(jwt)).toBeNull()
  })
})

describe("parsePlatformUserIdFromAccessTokenJwt", () => {
  it("reads userId claim from JWT payload as string", () => {
    const payload = Buffer.from(JSON.stringify({ userId: 42 })).toString("base64url")
    const jwt = `a.${payload}.c`
    expect(parsePlatformUserIdFromAccessTokenJwt(jwt)).toBe("42")
  })
})

describe("buildNotificationOutboundHeaders", () => {
  it("gateway mode sets Authorization only (no X-User-Id)", () => {
    const h = buildNotificationOutboundHeaders({
      upstream: "gateway",
      accessToken: "tok",
      inbound: new Headers({ accept: "application/json" }),
      directUserEmail: "should-not-appear@test.com",
      directPlatformUserId: "42",
    })
    expect(h.get("Authorization")).toBe("Bearer tok")
    expect(h.get("X-User-Id")).toBeNull()
    expect(h.get("X-Platform-User-Id")).toBeNull()
  })

  it("direct mode sets X-User-Id to JWT sub (email)", () => {
    const h = buildNotificationOutboundHeaders({
      upstream: "direct",
      accessToken: "tok",
      inbound: new Headers(),
      directUserEmail: "test2@test.com",
      directPlatformUserId: "42",
    })
    expect(h.get("Authorization")).toBe("Bearer tok")
    expect(h.get("X-User-Id")).toBe("test2@test.com")
    expect(h.get("X-Platform-User-Id")).toBe("42")
  })
})
