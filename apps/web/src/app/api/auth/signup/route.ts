import { NextResponse } from "next/server"
import { signupRequestSchema } from "@/lib/api/identity/signup.schema"
import type { ApiResponse, SignupResponse } from "@/lib/api/identity/types"

function json<T>(status: number, body: ApiResponse<T>) {
  return NextResponse.json(body, { status })
}

function envIdentityBaseUrl() {
  const url = process.env.IDENTITY_SERVICE_URL
  if (!url) return null
  return url.replace(/\/+$/, "")
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

  const parsed = signupRequestSchema.safeParse(payload)
  if (!parsed.success) {
    const first = parsed.error.issues[0]
    return json(400, { success: false, message: first?.message ?? "입력값이 올바르지 않습니다", data: null })
  }

  let upstream: Response
  try {
    upstream = await fetch(`${identityBaseUrl}/api/auth/signup`, {
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
    const body = upstreamJson as ApiResponse<SignupResponse>
    return json(201, body)
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

