import { NextResponse } from "next/server"

export const ACCESS_TOKEN_COOKIE = "access_token"
export const LOGGED_IN_COOKIE = "is_logged_in"

export function isSecureCookie(request: Request): boolean {
  const configured = process.env.IDENTITY_WEB_SECURE_COOKIE?.trim().toLowerCase()
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

export function resolveCookieDomain(request: Request): string | undefined {
  const host = request.headers.get("x-forwarded-host") ?? request.headers.get("host")
  if (host) {
    const hostname = host.split(",")[0]?.trim().split(":")[0]?.toLowerCase()
    if (hostname === "localhost") return "localhost"
    return undefined
  }
  try {
    const hostname = new URL(request.url).hostname.toLowerCase()
    return hostname === "localhost" ? "localhost" : undefined
  } catch {
    return undefined
  }
}

/** 로그아웃·회원 탈퇴 등 세션 종료 시 BFF 쿠키를 모두 삭제한다. */
export function clearAuthCookies(request: Request, response: NextResponse): void {
  const cookieDomain = resolveCookieDomain(request)
  const secure = isSecureCookie(request)
  const expired = { maxAge: 0, expires: new Date(0) }

  response.cookies.set({
    name: ACCESS_TOKEN_COOKIE,
    value: "",
    httpOnly: true,
    secure,
    sameSite: "lax",
    path: "/",
    domain: cookieDomain,
    ...expired,
  })
  response.cookies.set({
    name: LOGGED_IN_COOKIE,
    value: "",
    httpOnly: false,
    secure,
    sameSite: "lax",
    path: "/",
    domain: cookieDomain,
    ...expired,
  })
}
