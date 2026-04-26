import { NextResponse } from "next/server"
import { cookies } from "next/headers"
import { z } from "zod"
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

async function resolveAccessToken(request: Request): Promise<string | null> {
  const cookieStore = await cookies()
  const tokenFromStore = cookieStore.get(ACCESS_TOKEN_COOKIE)?.value
  if (tokenFromStore && tokenFromStore.length > 0) return tokenFromStore
  return getCookieValue(request.headers.get("cookie"), ACCESS_TOKEN_COOKIE)
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
  monthlyBudgetUsd: z
    .number({ message: "monthlyBudgetUsd는 숫자여야 합니다" })
    .min(0, "monthlyBudgetUsd는 0 이상이어야 합니다")
    .multipleOf(0.01, "monthlyBudgetUsd는 소수점 둘째 자리까지 입력할 수 있습니다"),
})

function getUpstreamMessage(upstreamJson: unknown): string | null {
  return typeof upstreamJson === "object" &&
    upstreamJson !== null &&
    "message" in upstreamJson &&
    typeof (upstreamJson as { message?: unknown }).message === "string"
    ? (upstreamJson as { message: string }).message
    : null
}

function isExternalKeySummary(data: unknown): boolean {
  if (typeof data !== "object" || data === null) return false
  const o = data as Record<string, unknown>
  const optionalIso = (v: unknown) =>
    v === null ||
    v === undefined ||
    (typeof v === "string" && (v.length === 0 || !Number.isNaN(Date.parse(v))))
  const optionalBudget = (v: unknown) => v === null || v === undefined || typeof v === "number"
  return (
    typeof o.id === "number" &&
    typeof o.provider === "string" &&
    (o.provider === "GEMINI" || o.provider === "OPENAI" || o.provider === "ANTHROPIC") &&
    typeof o.alias === "string" &&
    typeof o.createdAt === "string" &&
    optionalBudget(o.monthlyBudgetUsd) &&
    optionalIso(o.deletionRequestedAt) &&
    optionalIso(o.permanentDeletionAt)
  )
}

function isExternalKeysListResponseData(data: unknown): boolean {
  return Array.isArray(data) && data.every(isExternalKeySummary)
}

export async function GET(request: Request) {
  const token = await resolveAccessToken(request)
  if (!token) {
    return json(401, { success: false, message: "로그인이 필요합니다", data: null })
  }

  const gatewayBaseUrl = envGatewayBaseUrl()
  if (!gatewayBaseUrl) {
    return json(500, {
      success: false,
      message: "서버 설정이 필요합니다 (GATEWAY_URL 또는 WEB_GATEWAY_URL)",
      data: null,
    })
  }

  let upstream: Response
  try {
    upstream = await fetch(`${gatewayBaseUrl}/api/identity/auth/external-keys`, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: "application/json",
      },
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

  // 업스트림 오류는 가능한 그대로 전달한다 (상태/본문 유지).
  if (!upstream.ok) {
    if (typeof upstreamJson === "object" && upstreamJson !== null) {
      return NextResponse.json(upstreamJson, { status: upstream.status, headers: noStoreHeaders() })
    }
    const message =
      upstream.status === 401 || upstream.status === 403
        ? "로그인이 필요합니다"
        : "요청 처리에 실패했습니다"
    return json(upstream.status >= 400 ? upstream.status : 502, { success: false, message, data: null })
  }

  // 성공 시에는 data 형식을 방어적으로 검증한다.
  const body = upstreamJson as ApiResponse<unknown>
  if (!body?.success || !isExternalKeysListResponseData(body.data)) {
    return json(502, { success: false, message: "응답 형식이 올바르지 않습니다", data: null })
  }

  // Identity의 ApiResponse를 최대한 유지한다.
  if (typeof upstreamJson === "object" && upstreamJson !== null) {
    return NextResponse.json(upstreamJson, { status: upstream.status, headers: noStoreHeaders() })
  }

  return json(502, { success: false, message: "응답 형식이 올바르지 않습니다", data: null })
}

export async function POST(request: Request) {
  const token = await resolveAccessToken(request)
  if (!token) {
    return json(401, { success: false, message: "로그인이 필요합니다", data: null })
  }

  const gatewayBaseUrl = envGatewayBaseUrl()
  if (!gatewayBaseUrl) {
    return json(500, {
      success: false,
      message: "서버 설정이 필요합니다 (GATEWAY_URL 또는 WEB_GATEWAY_URL)",
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
    upstream = await fetch(`${gatewayBaseUrl}/api/identity/auth/external-keys`, {
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

  const message =
    getUpstreamMessage(upstreamJson) ??
    (upstream.status === 401 || upstream.status === 403 ? "로그인이 필요합니다" : "요청 처리에 실패했습니다")

  if (upstream.status === 400 || upstream.status === 401 || upstream.status === 409) {
    return json(upstream.status, { success: false, message, data: null })
  }

  return json(502, { success: false, message, data: null })
}

