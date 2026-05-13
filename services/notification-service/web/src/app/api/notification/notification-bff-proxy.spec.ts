import { describe, expect, it } from "vitest"
import {
  buildNotificationOutboundHeaders,
  parseNotificationHttpUpstream,
  parsePlatformUserIdFromAccessTokenJwt,
  parseSubjectEmailFromAccessTokenJwt,
  resolveNotificationUpstreamTarget,
} from "./notification-bff-proxy"

describe("notification BFF proxy", () => {
  it("parses upstream mode and resolves gateway vs direct targets", () => {
    expect(parseNotificationHttpUpstream(undefined)).toBe("direct")
    expect(parseNotificationHttpUpstream("GATEWAY")).toBe("gateway")
    expect(parseNotificationHttpUpstream("Direct")).toBe("direct")

    expect(
      resolveNotificationUpstreamTarget({
        upstream: "gateway",
        pathSegments: ["in-app-notifications"],
        search: "?limit=1",
        apiGatewayUrl: "http://gw:8080/",
        notificationServiceUrl: undefined,
      }),
    ).toEqual({
      ok: true,
      targetUrl: "http://gw:8080/api/notification/in-app-notifications?limit=1",
    })

    expect(
      resolveNotificationUpstreamTarget({
        upstream: "direct",
        pathSegments: ["in-app-notifications"],
        search: "",
        apiGatewayUrl: undefined,
        notificationServiceUrl: "http://nest:8096/api",
      }),
    ).toEqual({
      ok: true,
      targetUrl: "http://nest:8096/api/in-app-notifications",
    })

    expect(
      resolveNotificationUpstreamTarget({
        upstream: "gateway",
        pathSegments: ["x"],
        search: "",
        apiGatewayUrl: undefined,
        notificationServiceUrl: "http://n:1",
      }),
    ).toEqual({ ok: false, error: "missing_api_gateway_url" })

    expect(
      resolveNotificationUpstreamTarget({
        upstream: "direct",
        pathSegments: ["x"],
        search: "",
        apiGatewayUrl: "http://g:1",
        notificationServiceUrl: undefined,
      }),
    ).toEqual({ ok: false, error: "missing_notification_service_url" })
  })

  it("reads JWT payload claims used for direct-mode forwarding", () => {
    const emailPayload = Buffer.from(JSON.stringify({ sub: "test2@test.com" })).toString("base64url")
    expect(parseSubjectEmailFromAccessTokenJwt(`a.${emailPayload}.c`)).toBe("test2@test.com")

    const noSubPayload = Buffer.from(JSON.stringify({ userId: "42" })).toString("base64url")
    expect(parseSubjectEmailFromAccessTokenJwt(`a.${noSubPayload}.c`)).toBeNull()

    const userIdPayload = Buffer.from(JSON.stringify({ userId: 42 })).toString("base64url")
    expect(parsePlatformUserIdFromAccessTokenJwt(`a.${userIdPayload}.c`)).toBe("42")
  })

  it("builds outbound headers for gateway vs direct upstream", () => {
    const gatewayHeaders = buildNotificationOutboundHeaders({
      upstream: "gateway",
      accessToken: "tok",
      inbound: new Headers({ accept: "application/json" }),
      directUserEmail: "should-not-appear@test.com",
      directPlatformUserId: "42",
    })
    expect(gatewayHeaders.get("Authorization")).toBe("Bearer tok")
    expect(gatewayHeaders.get("X-User-Id")).toBeNull()
    expect(gatewayHeaders.get("X-Platform-User-Id")).toBeNull()

    const directHeaders = buildNotificationOutboundHeaders({
      upstream: "direct",
      accessToken: "tok",
      inbound: new Headers(),
      directUserEmail: "test2@test.com",
      directPlatformUserId: "42",
    })
    expect(directHeaders.get("Authorization")).toBe("Bearer tok")
    expect(directHeaders.get("X-User-Id")).toBe("test2@test.com")
    expect(directHeaders.get("X-Platform-User-Id")).toBe("42")
  })
})
