import { NextResponse } from "next/server"
import { cookies } from "next/headers"

const ACCESS_TOKEN_COOKIE = "access_token"

type ApiResponse<T> = {
  success: boolean
  message: string
  data: T | null
}

type TokenResponse = {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
}

function noStoreHeaders() {
  return { "Cache-Control": "no-store" }
}

function trimBaseUrl(url: string | undefined): string | null {
  const normalized = (url ?? "").trim()
  if (!normalized) return null
  return normalized.replace(/\/+$/, "")
}

function resolveSwitchTeamUpstreamUrls(): string[] {
  const urls: string[] = []
  const gatewayBase = trimBaseUrl(process.env.GATEWAY_URL) ?? trimBaseUrl(process.env.WEB_GATEWAY_URL)
  if (gatewayBase) {
    urls.push(`${gatewayBase}/api/identity/auth/token/switch-team`)
  }
  const identityBase = trimBaseUrl(process.env.IDENTITY_SERVICE_URL)
  if (identityBase) {
    urls.push(`${identityBase}/api/auth/token/switch-team`)
  }
  return urls
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

function isSecureCookie(request: Request): boolean {
  const configured = process.env.TEAM_WEB_SECURE_COOKIE?.trim().toLowerCase()
  if (configured === "true") return true
  if (configured === "false") return false
  const forwardedProto = request.headers.get("x-forwarded-proto")
  if (forwardedProto) {
    return forwardedProto.split(",")[0]?.trim().toLowerCase() === "https"
  }
  try {
    return new URL(request.url).protocol === "https:"
  } catch {
    return process.env.NODE_ENV === "production"
  }
}

function isTokenData(data: unknown): data is TokenResponse {
  return (
    typeof data === "object" &&
    data !== null &&
    typeof (data as { accessToken?: unknown }).accessToken === "string" &&
    typeof (data as { tokenType?: unknown }).tokenType === "string" &&
    typeof (data as { expiresInSeconds?: unknown }).expiresInSeconds === "number"
  )
}

function toMessage(upstreamJson: unknown, fallback: string): string {
  if (typeof upstreamJson === "object" && upstreamJson !== null) {
    const obj = upstreamJson as Record<string, unknown>
    if (typeof obj.message === "string" && obj.message.trim() !== "") return obj.message
    if (typeof obj.error === "string" && obj.error.trim() !== "") return obj.error
    if (typeof obj.detail === "string" && obj.detail.trim() !== "") return obj.detail
  }
  return fallback
}

export async function POST(request: Request) {
  let token = getCookieValue(request.headers.get("cookie"), ACCESS_TOKEN_COOKIE)
  if (!token) {
    try {
      const cookieStore = await cookies()
      token = cookieStore.get(ACCESS_TOKEN_COOKIE)?.value ?? null
    } catch {
      token = null
    }
  }
  if (!token) {
    return NextResponse.json({ success: false, message: "로그인이 필요합니다", data: null }, { status: 401, headers: noStoreHeaders() })
  }

  const upstreamUrls = resolveSwitchTeamUpstreamUrls()
  if (upstreamUrls.length === 0) {
    return NextResponse.json(
      { success: false, message: "서버 설정이 필요합니다 (GATEWAY_URL 또는 IDENTITY_SERVICE_URL)", data: null },
      { status: 500, headers: noStoreHeaders() },
    )
  }

  let payload: unknown
  try {
    payload = await request.json()
  } catch {
    return NextResponse.json({ success: false, message: "JSON 형식이 올바르지 않습니다", data: null }, { status: 400, headers: noStoreHeaders() })
  }

  const requestBody = JSON.stringify(payload)
  let upstream: Response | null = null
  let upstreamJson: unknown = null
  for (let i = 0; i < upstreamUrls.length; i += 1) {
    const upstreamUrl = upstreamUrls[i]
    try {
      upstream = await fetch(upstreamUrl, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        body: requestBody,
      })
    } catch {
      upstream = null
      upstreamJson = null
      continue
    }
    try {
      upstreamJson = await upstream.json()
    } catch {
      upstreamJson = null
    }
    const isRetriableFailure =
      !upstream.ok &&
      i < upstreamUrls.length - 1 &&
      [404, 502, 503, 504].includes(upstream.status)
    if (isRetriableFailure) {
      continue
    }
    break
  }

  if (!upstream) {
    return NextResponse.json(
      { success: false, message: "인증 서비스에 연결할 수 없습니다", data: null },
      { status: 502, headers: noStoreHeaders() },
    )
  }

  if (!upstream.ok) {
    const message = toMessage(upstreamJson, `요청 처리에 실패했습니다 (HTTP ${upstream.status})`)
    return NextResponse.json(
      { success: false, message, data: null },
      { status: upstream.status, headers: noStoreHeaders() },
    )
  }

  const body = upstreamJson as ApiResponse<TokenResponse>
  if (!body?.success || !isTokenData(body.data)) {
    return NextResponse.json(
      { success: false, message: "팀 전환 응답 형식이 올바르지 않습니다", data: null },
      { status: 502, headers: noStoreHeaders() },
    )
  }

  const response = NextResponse.json(
    { success: true, message: body.message || "팀 전환이 완료되었습니다", data: null },
    { status: 200, headers: noStoreHeaders() },
  )
  response.cookies.set({
    name: ACCESS_TOKEN_COOKIE,
    value: body.data.accessToken,
    httpOnly: true,
    secure: isSecureCookie(request),
    sameSite: "lax",
    path: "/",
    maxAge: body.data.expiresInSeconds,
  })
  return response
}
