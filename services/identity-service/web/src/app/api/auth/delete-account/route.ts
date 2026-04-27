import { NextResponse } from "next/server"
import { cookies } from "next/headers"
import type { ApiResponse } from "@/lib/api/identity/types"

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

function isSecureCookie(request: Request): boolean {
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
  const token = await resolveAccessToken(request)
  if (!token) {
    return json(401, { success: false, message: "Login required", data: null })
  }

  const gatewayBaseUrl = envGatewayBaseUrl()
  if (!gatewayBaseUrl) {
    return json(500, {
      success: false,
      message: "Server misconfiguration (GATEWAY_URL or WEB_GATEWAY_URL)",
      data: null,
    })
  }

  let payload: unknown
  try {
    payload = await request.json()
  } catch {
    return json(400, { success: false, message: "Invalid JSON body", data: null })
  }
  if (
    typeof payload !== "object" ||
    payload === null ||
    typeof (payload as { password?: unknown }).password !== "string"
  ) {
    return json(400, { success: false, message: "Password is required", data: null })
  }
  const password = (payload as { password: string }).password
  if (!password.trim()) {
    return json(400, { success: false, message: "Password is required", data: null })
  }

  let upstream: Response
  try {
    upstream = await fetch(`${gatewayBaseUrl}/api/identity/auth/delete-account`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
        Accept: "application/json",
      },
      body: JSON.stringify({ password }),
    })
  } catch {
    return json(502, { success: false, message: "Cannot reach identity service", data: null })
  }

  let upstreamJson: unknown = null
  try {
    upstreamJson = await upstream.json()
  } catch {
    upstreamJson = null
  }

  const body = upstreamJson as ApiResponse<null> | null
  if (upstream.ok && body?.success) {
    const res = json(200, body)
    clearAccessTokenCookie(request, res)
    return res
  }

  const message =
    body?.message ??
    "Account deletion failed. Check your password and try again."

  const status = upstream.status >= 400 && upstream.status < 600 ? upstream.status : 500
  return json(status, {
    success: false,
    message,
    data: null,
  })
}
