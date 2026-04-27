import { describe, expect, it } from "vitest"
import {
  buildNotificationOutboundHeaders,
  parseNotificationHttpUpstream,
  parseUserIdFromAccessTokenJwt,
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

describe("parseUserIdFromAccessTokenJwt", () => {
  it("reads string userId claim from JWT payload", () => {
    const payload = Buffer.from(JSON.stringify({ userId: "42" })).toString("base64url")
    const jwt = `a.${payload}.c`
    expect(parseUserIdFromAccessTokenJwt(jwt)).toBe("42")
  })

  it("coerces numeric userId to string", () => {
    const payload = Buffer.from(JSON.stringify({ userId: 7 })).toString("base64url")
    const jwt = `a.${payload}.c`
    expect(parseUserIdFromAccessTokenJwt(jwt)).toBe("7")
  })
})

describe("buildNotificationOutboundHeaders", () => {
  it("gateway mode sets Authorization only (no X-User-Id)", () => {
    const h = buildNotificationOutboundHeaders({
      upstream: "gateway",
      accessToken: "tok",
      inbound: new Headers({ accept: "application/json" }),
      directUserId: "should-not-appear",
    })
    expect(h.get("Authorization")).toBe("Bearer tok")
    expect(h.get("X-User-Id")).toBeNull()
  })

  it("direct mode sets X-User-Id from parsed user id", () => {
    const h = buildNotificationOutboundHeaders({
      upstream: "direct",
      accessToken: "tok",
      inbound: new Headers(),
      directUserId: "uid-1",
    })
    expect(h.get("Authorization")).toBe("Bearer tok")
    expect(h.get("X-User-Id")).toBe("uid-1")
  })
})
