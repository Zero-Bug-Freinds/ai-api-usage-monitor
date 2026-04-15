import { NextResponse } from "next/server"

const ACCESS_TOKEN_COOKIE = "access_token"

function noStoreHeaders() {
  return { "Cache-Control": "no-store" }
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

function envIdentityBaseUrl() {
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

function clearAccessTokenCookie(request: Request, res: NextResponse) {
  res.cookies.set({
    name: ACCESS_TOKEN_COOKIE,
    value: "",
    httpOnly: true,
    secure: isSecureCookie(request),
    sameSite: "lax",
    path: "/",
    maxAge: 0,
    expires: new Date(0),
  })
}

export async function POST(request: Request) {
  const token = getCookieValue(request.headers.get("cookie"), ACCESS_TOKEN_COOKIE)
  const identityBaseUrl = envIdentityBaseUrl()

  if (identityBaseUrl) {
    try {
      const headers: Record<string, string> = { Accept: "application/json" }
      if (token) {
        headers.Authorization = `Bearer ${token}`
      }
      await fetch(`${identityBaseUrl}/api/auth/logout`, {
        method: "POST",
        headers,
      })
    } catch {
      // 업스트림 실패해도 쿠키는 반드시 삭제
    }
  }

  const response = NextResponse.json(
    { success: true, message: "로그아웃되었습니다", data: null },
    { status: 200, headers: noStoreHeaders() },
  )
  clearAccessTokenCookie(request, response)
  return response
}
