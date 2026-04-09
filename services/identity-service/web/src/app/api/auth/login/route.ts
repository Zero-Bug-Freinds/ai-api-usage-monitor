import { NextResponse } from "next/server"
import { loginRequestSchema } from "@/lib/api/identity/login.schema"
import type { ApiResponse, LoginResponse } from "@/lib/api/identity/types"

const ACCESS_TOKEN_COOKIE = "access_token"

function noStoreHeaders() {
  return { "Cache-Control": "no-store" }
}

function isSecureCookie(): boolean {
  return process.env.NODE_ENV === "production"
}

function json<T>(status: number, body: ApiResponse<T>) {
  return NextResponse.json(body, { status, headers: noStoreHeaders() })
}

function envIdentityBaseUrl() {
  const url = process.env.IDENTITY_SERVICE_URL
  if (!url) return null
  return url.replace(/\/+$/, "")
}

function isLoginData(data: unknown): data is LoginResponse {
  return (
    typeof data === "object" &&
    data !== null &&
    "accessToken" in data &&
    typeof (data as { accessToken?: unknown }).accessToken === "string" &&
    "tokenType" in data &&
    typeof (data as { tokenType?: unknown }).tokenType === "string" &&
    "expiresInSeconds" in data &&
    typeof (data as { expiresInSeconds?: unknown }).expiresInSeconds === "number"
  )
}

export async function POST(request: Request) {
  const identityBaseUrl = envIdentityBaseUrl()
  if (!identityBaseUrl) {
    return json(500, {
      success: false,
      message: "서버 설정이 필요합니다 (IDENTITY_SERVICE_URL)",
      data: null,
    })
  }

  let payload: unknown
  try {
    payload = await request.json()
  } catch {
    return json(400, { success: false, message: "JSON 형식이 올바르지 않습니다", data: null })
  }

  const parsed = loginRequestSchema.safeParse(payload)
  if (!parsed.success) {
    const first = parsed.error.issues[0]
    return json(400, { success: false, message: first?.message ?? "입력값이 올바르지 않습니다", data: null })
  }

  let upstream: Response
  try {
    upstream = await fetch(`${identityBaseUrl}/api/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(parsed.data),
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
    const body = upstreamJson as ApiResponse<LoginResponse>
    if (!body?.success || !isLoginData(body.data)) {
      return json(502, { success: false, message: "로그인 응답 형식이 올바르지 않습니다", data: null })
    }
    if (body.data.tokenType !== "Bearer") {
      return json(502, { success: false, message: "로그인 토큰 타입이 올바르지 않습니다", data: null })
    }

    const response = json(200, {
      success: true,
      message: body.message || "로그인되었습니다",
      data: null,
    })
    response.cookies.set({
      name: ACCESS_TOKEN_COOKIE,
      value: body.data.accessToken,
      httpOnly: true,
      secure: isSecureCookie(),
      sameSite: "lax",
      path: "/",
      maxAge: body.data.expiresInSeconds,
    })
    return response
  }

  const message =
    typeof upstreamJson === "object" &&
    upstreamJson !== null &&
    "message" in upstreamJson &&
    typeof (upstreamJson as { message?: unknown }).message === "string"
      ? (upstreamJson as { message: string }).message
      : "요청 처리에 실패했습니다"

  return json(upstream.status, { success: false, message, data: null })
}
