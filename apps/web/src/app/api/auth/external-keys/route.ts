import { NextResponse } from "next/server"
import { z } from "zod"
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

/** Cookie 헤더에서 지정 이름의 값을 추출한다 (첫 일치). */
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

const createExternalKeyRequestSchema = z.object({
  provider: z.enum(["GEMINI", "OPENAI", "ANTHROPIC"], {
    message: "provider는 GEMINI/OPENAI/ANTHROPIC 중 하나여야 합니다",
  }),
  externalKey: z
    .string({ message: "externalKey는 문자열이어야 합니다" })
    .trim()
    .min(1, "externalKey는 필수입니다"),
  alias: z.string({ message: "alias는 문자열이어야 합니다" }).trim().min(1, "alias는 필수입니다"),
})

function getUpstreamMessage(upstreamJson: unknown): string | null {
  return typeof upstreamJson === "object" &&
    upstreamJson !== null &&
    "message" in upstreamJson &&
    typeof (upstreamJson as { message?: unknown }).message === "string"
    ? (upstreamJson as { message: string }).message
    : null
}

export async function POST(request: Request) {
  const token = getCookieValue(request.headers.get("cookie"), ACCESS_TOKEN_COOKIE)
  if (!token) {
    return json(401, { success: false, message: "로그인이 필요합니다", data: null })
  }

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

  const parsed = createExternalKeyRequestSchema.safeParse(payload)
  if (!parsed.success) {
    const first = parsed.error.issues[0]
    return json(400, {
      success: false,
      message: first?.message ?? "입력값이 올바르지 않습니다",
      data: null,
    })
  }

  let upstream: Response
  try {
    upstream = await fetch(`${identityBaseUrl}/api/auth/external-keys`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: "application/json",
        "Content-Type": "application/json",
      },
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
    // Identity의 ApiResponse를 최대한 유지한다.
    if (typeof upstreamJson === "object" && upstreamJson !== null) {
      return NextResponse.json(upstreamJson, { status: upstream.status, headers: noStoreHeaders() })
    }
    return json(502, { success: false, message: "응답 형식이 올바르지 않습니다", data: null })
  }

  const message = getUpstreamMessage(upstreamJson) ?? "요청 처리에 실패했습니다"

  if (upstream.status === 400 || upstream.status === 401 || upstream.status === 409) {
    return json(upstream.status, { success: false, message, data: null })
  }

  return json(502, { success: false, message, data: null })
}

