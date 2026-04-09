import { NextResponse } from "next/server"

const ACCESS_TOKEN_COOKIE = "access_token"

function noStoreHeaders(): HeadersInit {
  return { "Cache-Control": "no-store" }
}

function envTeamBaseUrl(): string | null {
  const url = process.env.TEAM_SERVICE_URL
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

function jsonError(status: number, message: string) {
  return NextResponse.json({ success: false, message, data: null }, { status, headers: noStoreHeaders() })
}

type RouteContext = { params: Promise<{ path?: string[] }> }

async function proxy(request: Request, context: RouteContext): Promise<Response> {
  const token = getCookieValue(request.headers.get("cookie"), ACCESS_TOKEN_COOKIE)
  if (!token) return jsonError(401, "로그인이 필요합니다")

  const teamBase = envTeamBaseUrl()
  if (!teamBase) return jsonError(500, "서버 설정이 필요합니다 (TEAM_SERVICE_URL)")

  const { path } = await context.params
  const pathParts = path ?? []
  const targetPath = pathParts.map((s) => encodeURIComponent(s)).join("/")
  const url = new URL(request.url)
  const targetUrl = `${teamBase}/api/v1/${targetPath}${url.search}`

  const headers = new Headers()
  headers.set("Authorization", `Bearer ${token}`)
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
