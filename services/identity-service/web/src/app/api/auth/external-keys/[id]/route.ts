import { NextResponse } from "next/server"
import type { ApiResponse } from "@/lib/api/identity/types"
import { updateExternalKeyRequestSchema } from "@/lib/api/identity/external-keys.schema"

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

type RouteContext = { params: Promise<{ id: string }> }

export async function PUT(request: Request, context: RouteContext) {
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

  const { id } = await context.params
  if (!/^\d+$/.test(id)) {
    return json(400, { success: false, message: "잘못된 키 id입니다", data: null })
  }

  let payload: unknown
  try {
    payload = await request.json()
  } catch {
    return json(400, { success: false, message: "JSON 형식이 올바르지 않습니다", data: null })
  }

  const parsed = updateExternalKeyRequestSchema.safeParse(payload)
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
    upstream = await fetch(`${identityBaseUrl}/api/auth/external-keys/${encodeURIComponent(id)}`, {
      method: "PUT",
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

  if (typeof upstreamJson === "object" && upstreamJson !== null) {
    return NextResponse.json(upstreamJson, { status: upstream.status, headers: noStoreHeaders() })
  }
  return json(502, { success: false, message: "요청 처리에 실패했습니다", data: null })
}

export async function DELETE(request: Request, context: RouteContext) {
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

  const { id } = await context.params
  if (!/^\d+$/.test(id)) {
    return json(400, { success: false, message: "잘못된 키 id입니다", data: null })
  }

  const url = new URL(request.url)
  const search = url.search

  let upstream: Response
  try {
    upstream = await fetch(
      `${identityBaseUrl}/api/auth/external-keys/${encodeURIComponent(id)}${search}`,
      {
        method: "DELETE",
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: "application/json",
        },
      },
    )
  } catch {
    return json(502, { success: false, message: "인증 서비스에 연결할 수 없습니다", data: null })
  }

  let upstreamJson: unknown = null
  try {
    upstreamJson = await upstream.json()
  } catch {
    upstreamJson = null
  }

  if (typeof upstreamJson === "object" && upstreamJson !== null) {
    return NextResponse.json(upstreamJson, { status: upstream.status, headers: noStoreHeaders() })
  }
  return json(502, { success: false, message: "요청 처리에 실패했습니다", data: null })
}
