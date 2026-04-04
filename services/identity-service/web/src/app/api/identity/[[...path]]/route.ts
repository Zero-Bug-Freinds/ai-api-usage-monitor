import { NextResponse } from "next/server"

const ACCESS_TOKEN_COOKIE = "access_token"

function noStoreHeaders(): HeadersInit {
  return { "Cache-Control": "no-store" }
}

function envIdentityBaseUrl(): string | null {
  const url = process.env.IDENTITY_SERVICE_URL
  if (!url) return null
  return url.replace(/\/+$/, "")
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

function filterUpstreamResponseHeaders(upstream: Response): Headers {
  const out = new Headers()
  const skip = new Set(
    ["connection", "content-encoding", "keep-alive", "proxy-authenticate", "proxy-authorization", "te", "trailers", "transfer-encoding", "upgrade"].map((s) => s.toLowerCase())
  )
  upstream.headers.forEach((value, key) => {
    if (skip.has(key.toLowerCase())) return
    out.append(key, value)
  })
  return out
}

function jsonError(status: number, message: string) {
  return NextResponse.json({ message }, { status, headers: noStoreHeaders() })
}

type RouteContext = { params: Promise<{ path?: string[] }> }

/**
 * Identity 관리 API용 BFF. 브라우저는 동일 오리진 `/api/identity/v1/...` 만 호출하고,
 * BFF가 `IDENTITY_SERVICE_URL/api/v1/...` 로 Bearer 프록시한다.
 * `/api/auth/*` 는 전용 라우트를 사용하므로 여기서는 다루지 않는다.
 */
async function proxyIdentityManagement(request: Request, context: RouteContext): Promise<Response> {
  const token = getCookieValue(request.headers.get("cookie"), ACCESS_TOKEN_COOKIE)
  if (!token) {
    return jsonError(401, "로그인이 필요합니다")
  }

  const identityBase = envIdentityBaseUrl()
  if (!identityBase) {
    return jsonError(500, "서버 설정이 필요합니다 (IDENTITY_SERVICE_URL)")
  }

  const { path: segments } = await context.params
  const pathParts = segments ?? []
  if (pathParts.length === 0 || pathParts[0] !== "v1") {
    return jsonError(
      404,
      "Identity 관리 API는 /api/identity/v1/... 경로만 지원합니다"
    )
  }

  const subPath = pathParts.map((s) => encodeURIComponent(s)).join("/")
  const url = new URL(request.url)
  const targetUrl = `${identityBase}/api/${subPath}${url.search}`

  const method = request.method.toUpperCase()
  const outbound = new Headers()
  outbound.set("Authorization", `Bearer ${token}`)

  const accept = request.headers.get("accept")
  outbound.set("Accept", accept && accept.length > 0 ? accept : "application/json")

  const correlation = request.headers.get("x-correlation-id")
  if (correlation && correlation.length > 0) {
    outbound.set("X-Correlation-Id", correlation)
  }

  const hasBody = method !== "GET" && method !== "HEAD"
  const init: RequestInit & { duplex?: "half" } = {
    method,
    headers: outbound,
    redirect: "manual",
  }

  if (hasBody) {
    const contentType = request.headers.get("content-type")
    if (contentType) {
      outbound.set("Content-Type", contentType)
    }
    init.body = request.body
    init.duplex = "half"
  }

  let upstream: Response
  try {
    upstream = await fetch(targetUrl, init)
  } catch {
    return jsonError(502, "인증 서비스에 연결할 수 없습니다")
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
  return proxyIdentityManagement(request, context)
}

export async function HEAD(request: Request, context: RouteContext) {
  return proxyIdentityManagement(request, context)
}

export async function POST(request: Request, context: RouteContext) {
  return proxyIdentityManagement(request, context)
}

export async function PUT(request: Request, context: RouteContext) {
  return proxyIdentityManagement(request, context)
}

export async function PATCH(request: Request, context: RouteContext) {
  return proxyIdentityManagement(request, context)
}

export async function DELETE(request: Request, context: RouteContext) {
  return proxyIdentityManagement(request, context)
}
