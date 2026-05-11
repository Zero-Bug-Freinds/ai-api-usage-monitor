import { NextResponse } from "next/server"
import { cookies } from "next/headers"
import type { ApiResponse, ProfileUpdateResponse } from "@/lib/api/identity/types"

const ACCESS_TOKEN_COOKIE = "access_token"

function noStoreHeaders() {
  return { "Cache-Control": "no-store" }
}

function json<T>(status: number, body: ApiResponse<T>) {
  return NextResponse.json(body, { status, headers: noStoreHeaders() })
}

function envGatewayBaseUrl() {
  const url = process.env.GATEWAY_URL ?? process.env.WEB_GATEWAY_URL
  if (url) return url.replace(/\/+$/, "")
  if (process.env.NODE_ENV === "development") {
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

async function resolveAccessToken(request: Request): Promise<string | null> {
  const cookieStore = await cookies()
  const tokenFromStore = cookieStore.get(ACCESS_TOKEN_COOKIE)?.value
  if (tokenFromStore && tokenFromStore.length > 0) return tokenFromStore
  return getCookieValue(request.headers.get("cookie"), ACCESS_TOKEN_COOKIE)
}

function isProfileUpdateData(data: unknown): data is ProfileUpdateResponse {
  if (typeof data !== "object" || data === null) return false
  const o = data as Record<string, unknown>
  return (
    typeof o.userId === "number" &&
    typeof o.email === "string" &&
    typeof o.name === "string" &&
    (o.role === "USER" || o.role === "ADMIN")
  )
}

export async function PUT(request: Request) {
  const token = await resolveAccessToken(request)
  if (!token) {
    return json(401, {
      success: false,
      message: "로그인이 필요합니다",
      data: null,
    })
  }

  const gatewayBaseUrl = envGatewayBaseUrl()
  if (!gatewayBaseUrl) {
    return json(500, {
      success: false,
      message: "서버 설정이 필요합니다 (GATEWAY_URL 또는 WEB_GATEWAY_URL)",
      data: null,
    })
  }

  let payload: unknown
  try {
    payload = await request.json()
  } catch {
    return json(400, { success: false, message: "JSON 형식이 올바르지 않습니다", data: null })
  }
  if (typeof payload !== "object" || payload === null) {
    return json(400, { success: false, message: "요청 본문이 올바르지 않습니다", data: null })
  }

  let upstream: Response
  try {
    upstream = await fetch(`${gatewayBaseUrl}/api/identity/auth/profile`, {
      method: "PUT",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
        Accept: "application/json",
      },
      body: JSON.stringify(payload),
    })
  } catch {
    return json(502, { success: false, message: "인증 서비스에 연결할 수 없습니다", data: null })
  }

  let upstreamJson: unknown = null
  try {
    upstreamJson = await upstream.json()
  } catch {
    upstreamJson = null
  }

  if (upstream.ok) {
    const body = upstreamJson as ApiResponse<unknown>
    if (!body?.success || !isProfileUpdateData(body.data)) {
      return json(502, { success: false, message: "프로필 응답 형식이 올바르지 않습니다", data: null })
    }
    return json<ProfileUpdateResponse>(200, {
      success: true,
      message: typeof body.message === "string" ? body.message : "프로필이 갱신되었습니다",
      data: body.data,
    })
  }

  const message =
    typeof upstreamJson === "object" &&
    upstreamJson !== null &&
    "message" in upstreamJson &&
    typeof (upstreamJson as { message?: unknown }).message === "string"
      ? (upstreamJson as { message: string }).message
      : upstream.status === 401 || upstream.status === 403
        ? "로그인이 필요합니다"
        : "요청 처리에 실패했습니다"

  return json(upstream.status >= 400 ? upstream.status : 502, {
    success: false,
    message,
    data: null,
  })
}
