import type { NextRequest } from "next/server"
import { NextResponse } from "next/server"

/**
 * 보호 페이지 진입 시 access_token 쿠키를 검사한다.
 * Route Handler(BFF)는 자체 세션 검증하므로 `/api/**` 는 통과시킨다.
 */
export function middleware(request: NextRequest) {
  const pathname = request.nextUrl.pathname
  if (pathname.includes("/api/") || pathname.endsWith("/api")) {
    return NextResponse.next()
  }

  const token = request.cookies.get("access_token")?.value
  const isLoggedIn = request.cookies.get("is_logged_in")?.value === "true"
  if (token || isLoggedIn) {
    return NextResponse.next()
  }

  const idOrigin = (process.env.NEXT_PUBLIC_IDENTITY_WEB_ORIGIN ?? "").replace(/\/$/, "")
  const loginUrl = idOrigin
    ? new URL(`${idOrigin}/login`)
    : new URL("/login", request.url)
  const nextPath = pathname + request.nextUrl.search
  loginUrl.searchParams.set("next", nextPath)
  return NextResponse.redirect(loginUrl)
}

/**
 * basePath=/dashboard 이므로 정적 청크는 /dashboard/_next/* 이다.
 * (?!_next/) 만 쓰면 첫 세그먼트가 dashboard 라서 미들웨어가 정적 요청까지 가로챈다.
 */
export const config = {
  matcher: [
    "/dashboard",
    "/dashboard/",
    "/dashboard/((?!_next/|api/).+)",
  ],
}
