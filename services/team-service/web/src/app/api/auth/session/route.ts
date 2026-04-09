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

export async function GET(request: Request) {
  const token = getCookieValue(request.headers.get("cookie"), ACCESS_TOKEN_COOKIE)
  if (!token) {
    return NextResponse.json({ success: false, message: "로그인이 필요합니다", data: null }, { status: 401, headers: noStoreHeaders() })
  }
  const identityBase = envIdentityBaseUrl()
  if (!identityBase) {
    return NextResponse.json(
      { success: false, message: "서버 설정이 필요합니다 (IDENTITY_SERVICE_URL)", data: null },
      { status: 500, headers: noStoreHeaders() }
    )
  }

  let upstream: Response
  try {
    upstream = await fetch(`${identityBase}/api/auth/session`, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: "application/json",
      },
    })
  } catch {
    return NextResponse.json(
      { success: false, message: "인증 서비스에 연결할 수 없습니다", data: null },
      { status: 502, headers: noStoreHeaders() }
    )
  }

  let body: unknown = null
  try {
    body = await upstream.json()
  } catch {
    body = null
  }
  if (body && typeof body === "object") {
    return NextResponse.json(body, { status: upstream.status, headers: noStoreHeaders() })
  }
  return NextResponse.json(
    { success: false, message: "요청 처리에 실패했습니다", data: null },
    { status: 502, headers: noStoreHeaders() }
  )
}
