/**
 * Pure helpers for the notification BFF route handler (unit-tested without Next.js runtime).
 */

export type NotificationHttpUpstream = "gateway" | "direct"

const ACCESS_TOKEN_COOKIE = "access_token"

export function getCookieValue(cookieHeader: string | null, name: string): string | null {
  if (!cookieHeader) return null
  const prefix = `${name}=`
  for (const part of cookieHeader.split(";")) {
    const trimmed = part.trim()
    if (trimmed.startsWith(prefix)) {
      const value = trimmed.slice(prefix.length)
      return value.length > 0 ? value : null
    }
  }
  return null
}

export function getAccessTokenFromRequestCookie(request: Request): string | null {
  return getCookieValue(request.headers.get("cookie"), ACCESS_TOKEN_COOKIE)
}

/**
 * Parses JWT access token payload and returns the platform `userId` claim (Identity JWT).
 * Does not verify the signature; use only for **direct** BFF→Nest hops where the Gateway
 * would normally inject `X-User-Id` after verifying the same token.
 */
export function parseUserIdFromAccessTokenJwt(accessToken: string): string | null {
  const parts = accessToken.split(".")
  if (parts.length < 2) return null
  const segment = parts[1].replace(/-/g, "+").replace(/_/g, "/")
  const pad = segment.length % 4
  const padded = pad ? segment + "=".repeat(4 - pad) : segment
  try {
    const json = Buffer.from(padded, "base64").toString("utf8")
    const payload = JSON.parse(json) as { userId?: unknown }
    const id = payload.userId
    if (typeof id === "string" && id.trim().length > 0) return id.trim()
    if (typeof id === "number" && Number.isFinite(id)) return String(Math.trunc(id))
    return null
  } catch {
    return null
  }
}

export function parseNotificationHttpUpstream(raw: string | undefined): NotificationHttpUpstream {
  const v = raw?.trim().toLowerCase()
  if (v === "gateway") return "gateway"
  if (v === "direct") return "direct"
  return "direct"
}

export function trimTrailingSlashes(url: string): string {
  return url.replace(/\/+$/, "")
}

export type ResolveNotificationTargetInput = {
  upstream: NotificationHttpUpstream
  pathSegments: string[]
  search: string
  apiGatewayUrl: string | undefined
  notificationServiceUrl: string | undefined
}

export type ResolveNotificationTargetResult =
  | { ok: true; targetUrl: string }
  | { ok: false; error: "missing_api_gateway_url" | "missing_notification_service_url" | "empty_path" }

/**
 * Builds the upstream absolute URL for the notification API segments (no leading slash on segments).
 */
export function resolveNotificationUpstreamTarget(input: ResolveNotificationTargetInput): ResolveNotificationTargetResult {
  const { upstream, pathSegments, search, apiGatewayUrl, notificationServiceUrl } = input
  if (pathSegments.length === 0) {
    return { ok: false, error: "empty_path" }
  }
  const proxiedPath = pathSegments.map((s) => encodeURIComponent(s)).join("/")
  if (upstream === "gateway") {
    const base = apiGatewayUrl?.trim()
    if (!base) return { ok: false, error: "missing_api_gateway_url" }
    const targetUrl = `${trimTrailingSlashes(base)}/api/notification/${proxiedPath}${search}`
    return { ok: true, targetUrl }
  }
  const base = notificationServiceUrl?.trim()
  if (!base) return { ok: false, error: "missing_notification_service_url" }
  const targetUrl = `${trimTrailingSlashes(base)}/${proxiedPath}${search}`
  return { ok: true, targetUrl }
}

export type BuildNotificationOutboundHeadersInput = {
  upstream: NotificationHttpUpstream
  accessToken: string
  inbound: Headers
  directUserId: string | null
}

/**
 * Outbound headers to Nest/Gateway. Never forwards client `X-User-Id` / internal secret.
 */
export function buildNotificationOutboundHeaders(input: BuildNotificationOutboundHeadersInput): Headers {
  const { upstream, accessToken, inbound, directUserId } = input
  const outbound = new Headers()

  const accept = inbound.get("accept")
  outbound.set("Accept", accept && accept.length > 0 ? accept : "application/json")

  const contentType = inbound.get("content-type")
  if (contentType) outbound.set("Content-Type", contentType)

  const correlation = inbound.get("x-correlation-id")
  if (correlation && correlation.length > 0) {
    outbound.set("X-Correlation-Id", correlation)
  }

  outbound.set("Authorization", `Bearer ${accessToken}`)

  if (upstream === "direct") {
    const uid = directUserId?.trim()
    if (uid) outbound.set("X-User-Id", uid)
  }

  return outbound
}
