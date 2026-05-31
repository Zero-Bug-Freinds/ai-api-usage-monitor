import type { NextRequest } from "next/server"
import { NextResponse } from "next/server"

/**
 * auth-required 페이지 진입 시 access_token 쿠키를 검사한다.
 * 쿠키가 없으면 로그인 페이지로 이동한다.
 */
export function middleware(request: NextRequest) {
  const token = request.cookies.get("access_token")?.value?.trim()
  if (token) {
    return NextResponse.next()
  }

  return NextResponse.redirect(new URL("/login", request.url))
}

export const config = {
  matcher: ["/settings/:path*"],
}
