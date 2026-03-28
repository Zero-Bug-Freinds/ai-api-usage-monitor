import { NextResponse } from "next/server"
import type { ApiResponse } from "@/lib/api/identity/types"

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

function clearAccessTokenCookie(res: NextResponse) {
  res.cookies.set({
    name: ACCESS_TOKEN_COOKIE,
    value: "",
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: "/",
    maxAge: 0,
    expires: new Date(0),
  })
}

/**
 * httpOnly `access_token` 쿠키를 삭제한다. Identity는 stateless이므로 선택적으로 `/api/auth/logout`을 호출한다.
 */
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
      // 업스트림 실패해도 클라이언트 쿠키는 반드시 삭제한다.
    }
  }

  const response = json<null>(200, {
    success: true,
    message: "로그아웃되었습니다",
    data: null,
  })
  clearAccessTokenCookie(response)
  return response
}
