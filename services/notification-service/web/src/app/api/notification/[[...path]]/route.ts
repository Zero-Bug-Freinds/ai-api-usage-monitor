import { NextResponse } from "next/server"

import {
  buildNotificationOutboundHeaders,
  getAccessTokenFromRequestCookie,
  parseNotificationHttpUpstream,
  parseUserIdFromAccessTokenJwt,
  resolveNotificationUpstreamTarget,
} from "../notification-bff-proxy"

function noStoreHeaders(): HeadersInit {
  return { "Cache-Control": "no-store" }
}

function jsonError(status: number, message: string) {
  return NextResponse.json({ message }, { status, headers: noStoreHeaders() })
}

function filterUpstreamResponseHeaders(upstream: Response): Headers {
  const out = new Headers()
  const skip = new Set(
    [
      "connection",
      "content-encoding",
      "keep-alive",
      "proxy-authenticate",
      "proxy-authorization",
      "te",
      "trailers",
      "transfer-encoding",
      "upgrade",
    ].map((s) => s.toLowerCase()),
  )
  upstream.headers.forEach((value, key) => {
    if (skip.has(key.toLowerCase())) return
    out.append(key, value)
  })
  return out
}

type RouteContext = { params: Promise<{ path?: string[] }> }

async function proxyNotification(request: Request, context: RouteContext): Promise<Response> {
  const token = getAccessTokenFromRequestCookie(request)
  if (!token) {
    return jsonError(401, "로그인이 필요합니다")
  }

  const upstream = parseNotificationHttpUpstream(process.env.NOTIFICATION_HTTP_UPSTREAM)
  const apiGatewayUrl = process.env.API_GATEWAY_URL
  const notificationServiceUrl = process.env.NOTIFICATION_SERVICE_URL

  const { path: segments } = await context.params
  const pathParts = segments ?? []
  if (pathParts.length === 0) {
    return jsonError(404, "알림 API 경로가 필요합니다")
  }

  const url = new URL(request.url)
  const resolved = resolveNotificationUpstreamTarget({
    upstream,
    pathSegments: pathParts,
    search: url.search,
    apiGatewayUrl,
    notificationServiceUrl,
  })

  if (!resolved.ok) {
    if (resolved.error === "missing_api_gateway_url") {
      return jsonError(500, "서버 설정이 필요합니다 (API_GATEWAY_URL)")
    }
    if (resolved.error === "missing_notification_service_url") {
      return jsonError(500, "서버 설정이 필요합니다 (NOTIFICATION_SERVICE_URL)")
    }
    return jsonError(404, "알림 API 경로가 필요합니다")
  }

  const directUserId =
    upstream === "direct" ? parseUserIdFromAccessTokenJwt(token) : null
  if (upstream === "direct" && !directUserId) {
    return jsonError(401, "토큰에서 사용자 식별자를 확인할 수 없습니다")
  }

  const method = request.method.toUpperCase()
  const outbound = buildNotificationOutboundHeaders({
    upstream,
    accessToken: token,
    inbound: request.headers,
    directUserId,
  })

  const hasBody = method !== "GET" && method !== "HEAD"
  const init: RequestInit & { duplex?: "half" } = {
    method,
    headers: outbound,
    redirect: "manual",
  }

  if (hasBody) {
    init.body = request.body
    init.duplex = "half"
  }

  let upstreamRes: Response
  try {
    upstreamRes = await fetch(resolved.targetUrl, init)
  } catch {
    return jsonError(502, "알림 서비스에 연결할 수 없습니다")
  }

  const resHeaders = filterUpstreamResponseHeaders(upstreamRes)
  resHeaders.set("Cache-Control", "no-store")

  return new NextResponse(upstreamRes.body, {
    status: upstreamRes.status,
    statusText: upstreamRes.statusText,
    headers: resHeaders,
  })
}

export async function GET(request: Request, context: RouteContext) {
  return proxyNotification(request, context)
}

export async function HEAD(request: Request, context: RouteContext) {
  return proxyNotification(request, context)
}

export async function POST(request: Request, context: RouteContext) {
  return proxyNotification(request, context)
}

export async function PUT(request: Request, context: RouteContext) {
  return proxyNotification(request, context)
}

export async function PATCH(request: Request, context: RouteContext) {
  return proxyNotification(request, context)
}

export async function DELETE(request: Request, context: RouteContext) {
  return proxyNotification(request, context)
}
