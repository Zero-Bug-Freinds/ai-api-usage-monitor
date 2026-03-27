import { NextResponse } from "next/server"
import type { ApiResponse, SessionResponse } from "@/lib/api/identity/types"

const ACCESS_TOKEN_COOKIE = "access_token"

function noStoreHeaders() {
  return { "Cache-Control": "no-store" }
}

function json<T>(status: number, body: ApiResponse<T>) {
  return NextResponse.json(body, { status, headers: noStoreHeaders() })
}

function hasCookieValue(cookieHeader: string | null, name: string) {
  if (!cookieHeader) return false
  return cookieHeader
    .split(";")
    .map((part) => part.trim())
    .some((part) => part.startsWith(`${name}=`) && part.slice(name.length + 1).length > 0)
}

export async function GET(request: Request) {
  const cookieHeader = request.headers.get("cookie")
  const hasToken = hasCookieValue(cookieHeader, ACCESS_TOKEN_COOKIE)

  if (!hasToken) {
    return json(401, {
      success: false,
      message: "로그인이 필요합니다",
      data: null,
    })
  }

  return json<SessionResponse>(200, {
    success: true,
    message: "세션이 유효합니다",
    data: { authenticated: true },
  })
}
