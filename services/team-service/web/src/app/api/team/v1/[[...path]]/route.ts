import { NextResponse } from "next/server"

const ACCESS_TOKEN_COOKIE = "access_token"

function noStoreHeaders(): HeadersInit {
  return { "Cache-Control": "no-store" }
}

/** Gateway base URL (no trailing slash). */
function envGatewayBaseUrl(): string | null {
  const raw = (process.env.GATEWAY_URL ?? "").trim()
  if (raw) return raw.replace(/\/+$/, "")

  if (process.env.NODE_ENV === "development") {
    console.warn(
      "[team-web] GATEWAY_URL is not set; using http://127.0.0.1:8888. " +
        "Set GATEWAY_URL in .env.local when gateway uses a different address.",
    )
    return "http://127.0.0.1:8888"
  }
  return null
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

function jsonError(status: number, message: string) {
  return NextResponse.json({ success: false, message, data: null }, { status, headers: noStoreHeaders() })
}

function resolveAuthorizationHeader(request: Request): string | null {
  const incomingAuth = request.headers.get("authorization")
  if (incomingAuth && incomingAuth.trim().length > 0) {
    return incomingAuth
  }
  const token = getCookieValue(request.headers.get("cookie"), ACCESS_TOKEN_COOKIE)
  if (!token) {
    return null
  }
  return `Bearer ${token}`
}

type RouteContext = { params: Promise<{ path?: string[] }> }

async function proxy(request: Request, context: RouteContext): Promise<Response> {
  const authorization = resolveAuthorizationHeader(request)
  if (!authorization) return jsonError(401, "로그인이 필요합니다")

  const gatewayBase = envGatewayBaseUrl()
  if (!gatewayBase) return jsonError(500, "서버 설정이 필요합니다 (GATEWAY_URL)")

  const { path } = await context.params
  const pathParts = path ?? []
  const targetPath = pathParts.map((s) => encodeURIComponent(s)).join("/")
  const url = new URL(request.url)
  const targetUrl = `${gatewayBase}/api/team/v1/${targetPath}${url.search}`

  const headers = new Headers()
  headers.set("Authorization", authorization)
  headers.set("Accept", request.headers.get("accept") ?? "application/json")

  const method = request.method.toUpperCase()
  const hasBody = method !== "GET" && method !== "HEAD"
  const init: RequestInit & { duplex?: "half" } = {
    method,
    headers,
    redirect: "manual",
  }
  if (hasBody) {
    const contentType = request.headers.get("content-type")
    if (contentType) headers.set("Content-Type", contentType)
    init.body = request.body
    init.duplex = "half"
  }

  let upstream: Response
  try {
    upstream = await fetch(targetUrl, init)
  } catch {
    return jsonError(502, "팀 서비스에 연결할 수 없습니다")
  }

  return new NextResponse(upstream.body, {
    status: upstream.status,
    statusText: upstream.statusText,
    headers: noStoreHeaders(),
  })
}

export async function GET(request: Request, context: RouteContext) {
  return proxy(request, context)
}

export async function POST(request: Request, context: RouteContext) {
  return proxy(request, context)
}

export async function PUT(request: Request, context: RouteContext) {
  return proxy(request, context)
}

export async function PATCH(request: Request, context: RouteContext) {
  return proxy(request, context)
}

export async function DELETE(request: Request, context: RouteContext) {
  return proxy(request, context)
}
