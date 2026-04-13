import { NextResponse } from "next/server"

const ACCESS_TOKEN_COOKIE = "access_token"

function noStoreHeaders(): HeadersInit {
  return { "Cache-Control": "no-store" }
}

function jsonError(status: number, message: string) {
  return NextResponse.json({ message }, { status, headers: noStoreHeaders() })
}

function getCookieValue(cookieHeader: string | null, name: string): string | null {
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

function envNotificationServiceBaseUrl(): string | null {
  const url = process.env.NOTIFICATION_SERVICE_URL
  if (!url) return null
  return url.replace(/\/+$/, "")
}

function envIdentityBaseUrl(): string | null {
  const url = process.env.IDENTITY_SERVICE_URL
  if (!url) return null
  return url.replace(/\/+$/, "")
}

async function fetchSessionEmail(identityBaseUrl: string, token: string): Promise<string | null> {
  let res: Response
  try {
    res = await fetch(`${identityBaseUrl}/api/auth/session`, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: "application/json",
      },
      cache: "no-store",
    })
  } catch {
    return null
  }

  let body: unknown
  try {
    body = await res.json()
  } catch {
    return null
  }
  if (!res.ok) return null
  if (typeof body !== "object" || body === null) return null
  const data = (body as { data?: unknown }).data
  if (typeof data !== "object" || data === null) return null
  const email = (data as { email?: unknown }).email
  return typeof email === "string" && email.length > 0 ? email : null
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
    ].map((s) => s.toLowerCase())
  )
  upstream.headers.forEach((value, key) => {
    if (skip.has(key.toLowerCase())) return
    out.append(key, value)
  })
  return out
}

type RouteContext = { params: Promise<{ path?: string[] }> }

async function proxyNotification(request: Request, context: RouteContext): Promise<Response> {
  const token = getCookieValue(request.headers.get("cookie"), ACCESS_TOKEN_COOKIE)
  if (!token) {
    return jsonError(401, "로그인이 필요합니다")
  }

  const notificationBase = envNotificationServiceBaseUrl()
  if (!notificationBase) {
    return jsonError(500, "서버 설정이 필요합니다 (NOTIFICATION_SERVICE_URL)")
  }

  const identityBase = envIdentityBaseUrl()
  if (!identityBase) {
    return jsonError(500, "서버 설정이 필요합니다 (IDENTITY_SERVICE_URL)")
  }

  const userId = await fetchSessionEmail(identityBase, token)
  if (!userId) {
    return jsonError(401, "세션 확인에 실패했습니다")
  }

  const { path: segments } = await context.params
  const pathParts = segments ?? []
  if (pathParts.length === 0) {
    return jsonError(404, "알림 API 경로가 필요합니다")
  }

  const proxiedPath = pathParts.map((s) => encodeURIComponent(s)).join("/")
  const url = new URL(request.url)
  const targetUrl = `${notificationBase}/${proxiedPath}${url.search}`

  const method = request.method.toUpperCase()
  const outbound = new Headers()

  const accept = request.headers.get("accept")
  outbound.set("Accept", accept && accept.length > 0 ? accept : "application/json")

  const contentType = request.headers.get("content-type")
  if (contentType) outbound.set("Content-Type", contentType)

  const correlation = request.headers.get("x-correlation-id")
  if (correlation && correlation.length > 0) {
    outbound.set("X-Correlation-Id", correlation)
  }

  outbound.set("X-User-Id", userId)

  const internalSecret = process.env.NOTIFICATION_INTERNAL_SECRET?.trim()
  if (internalSecret && internalSecret.length > 0) {
    outbound.set("X-Notification-Internal-Secret", internalSecret)
  }

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

  let upstream: Response
  try {
    upstream = await fetch(targetUrl, init)
  } catch {
    return jsonError(502, "알림 서비스에 연결할 수 없습니다")
  }

  const resHeaders = filterUpstreamResponseHeaders(upstream)
  resHeaders.set("Cache-Control", "no-store")

  return new NextResponse(upstream.body, {
    status: upstream.status,
    statusText: upstream.statusText,
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

