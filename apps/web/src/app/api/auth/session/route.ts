import { NextResponse } from "next/server"
import type { ApiResponse, SessionResponse } from "@/lib/api/identity/types"

const ACCESS_TOKEN_COOKIE = "access_token"

function noStoreHeaders() {
  return { "Cache-Control": "no-store" }
}

function json<T>(status: number, body: ApiResponse<T>) {
  return NextResponse.json(body, { status, headers: noStoreHeaders() })
}

function envIdentityBaseUrl() {
  const url = process.env.IDENTITY_SERVICE_URL
  if (!url) return null
  return url.replace(/\/+$/, "")
}

/** Cookie 헤더에서 지정 이름의 값을 추출한다 (첫 일치). */
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

function isSessionData(data: unknown): data is SessionResponse {
  if (typeof data !== "object" || data === null) return false
  const o = data as Record<string, unknown>
  return (
    typeof o.email === "string" &&
    typeof o.role === "string" &&
    (o.role === "USER" || o.role === "ADMIN") &&
    typeof o.authenticated === "boolean"
  )
}

export async function GET(request: Request) {
  const token = getCookieValue(request.headers.get("cookie"), ACCESS_TOKEN_COOKIE)
  if (!token) {
    return json(401, {
      success: false,
      message: "로그인이 필요합니다",
      data: null,
    })
  }

  const identityBaseUrl = envIdentityBaseUrl()
  if (!identityBaseUrl) {
    return json(500, {
      success: false,
      message: "서버 설정이 필요합니다 (IDENTITY_SERVICE_URL)",
      data: null,
    })
  }

  let upstream: Response
  try {
    upstream = await fetch(`${identityBaseUrl}/api/auth/session`, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: "application/json",
      },
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
    if (!body?.success || !isSessionData(body.data)) {
      return json(502, { success: false, message: "세션 응답 형식이 올바르지 않습니다", data: null })
    }
    return json<SessionResponse>(200, {
      success: true,
      message: typeof body.message === "string" ? body.message : "세션이 유효합니다",
      data: body.data,
    })
  }

  const message =
    typeof upstreamJson === "object" &&
    upstreamJson !== null &&
    "message" in upstreamJson &&
    typeof (upstreamJson as { message?: unknown }).message === "string"
      ? (upstreamJson as { message: string }).message
      : "요청 처리에 실패했습니다"

  return json(upstream.status >= 400 ? upstream.status : 502, {
    success: false,
    message,
    data: null,
  })
}
