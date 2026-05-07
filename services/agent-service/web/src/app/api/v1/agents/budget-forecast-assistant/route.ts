import { NextRequest, NextResponse } from "next/server"

const ORIGIN_PROBE_TIMEOUT_MS = 3000
const FORECAST_FETCH_TIMEOUT_MS = 45000

type SessionApiResponse = {
  success?: boolean
  data?: { email?: string | null } | null
}

function backendOriginCandidates(): string[] {
  const configured = (process.env.AI_AGENT_SERVICE_INTERNAL_ORIGIN ?? "").trim().replace(/\/$/, "")
  const defaults = ["http://agent-service:8096", "http://host.docker.internal:8096", "http://localhost:8096"]
  return Array.from(new Set([configured, ...defaults].filter((value) => value.length > 0)))
}

function identityWebOriginCandidates(): string[] {
  const configured = (process.env.IDENTITY_WEB_INTERNAL_ORIGIN ?? "").trim().replace(/\/$/, "")
  const publicOrigin = (process.env.NEXT_PUBLIC_IDENTITY_WEB_ORIGIN ?? "").trim().replace(/\/$/, "")
  const defaults = ["http://identity-web:3000", "http://host.docker.internal:3000", "http://localhost:3000"]
  return Array.from(new Set([configured, publicOrigin, ...defaults].filter((value) => value.length > 0)))
}

async function fetchWithTimeout(url: string, timeoutMs: number, init: RequestInit): Promise<Response> {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), timeoutMs)
  try {
    return await fetch(url, {
      ...init,
      signal: controller.signal,
    })
  } finally {
    clearTimeout(timeout)
  }
}

async function resolveBackendOrigin(): Promise<string | null> {
  for (const origin of backendOriginCandidates()) {
    try {
      const response = await fetchWithTimeout(`${origin}/api/v1/agents/identity-api-keys`, ORIGIN_PROBE_TIMEOUT_MS, {
        method: "GET",
        cache: "no-store",
      })
      if (response.ok) {
        return origin
      }
    } catch {
      // try next origin
    }
  }
  return null
}

async function resolveSessionEmail(request: NextRequest): Promise<string | null> {
  const cookieHeader = request.headers.get("cookie") ?? ""
  const forwardedHeaders: HeadersInit = { Accept: "application/json" }
  if (cookieHeader.trim().length > 0) {
    forwardedHeaders.Cookie = cookieHeader
  }
  for (const origin of identityWebOriginCandidates()) {
    try {
      const response = await fetchWithTimeout(`${origin}/api/auth/session`, ORIGIN_PROBE_TIMEOUT_MS, {
        method: "GET",
        headers: forwardedHeaders,
      })
      if (!response.ok) continue
      const payload = (await response.json()) as SessionApiResponse
      if (!payload.success) continue
      const email = payload.data?.email?.trim() ?? ""
      if (email.includes("@")) return email
    } catch {
      // try next origin
    }
  }
  return null
}

export async function POST(request: NextRequest) {
  const body = await request.text()
  const origin = await resolveBackendOrigin()
  if (origin == null) {
    return NextResponse.json(
      {
        message: "budget forecast backend 호출에 실패했습니다. (agent-service 연결 불가)",
      },
      { status: 502 },
    )
  }
  const targetUrl = `${origin}/api/v1/agents/budget-forecast-assistant`
  const sessionEmail = await resolveSessionEmail(request)
  const forwardedUserId = request.headers.get("x-user-id")?.trim() ?? ""
  const forwardedEmail = sessionEmail ?? request.headers.get("x-user-email")?.trim() ?? ""
  const forwardedHeaders: Record<string, string> = { "Content-Type": "application/json" }
  if (forwardedUserId.length > 0) {
    forwardedHeaders["x-user-id"] = forwardedUserId
  }
  if (forwardedEmail.length > 0) {
    forwardedHeaders["x-user-email"] = forwardedEmail
  }

  for (let attempt = 0; attempt < 2; attempt += 1) {
    try {
      const response = await fetchWithTimeout(targetUrl, FORECAST_FETCH_TIMEOUT_MS, {
        method: "POST",
        headers: forwardedHeaders,
        body,
        cache: "no-store",
      })

      const responseBody = await response.text()
      const contentType = response.headers.get("content-type") ?? "application/json"
      return new NextResponse(responseBody, {
        status: response.status,
        headers: { "content-type": contentType },
      })
    } catch (error) {
      const isLastAttempt = attempt === 1
      if (!isLastAttempt) {
        continue
      }
      const detail =
        error instanceof Error && error.message.trim().length > 0
          ? error.message
          : "unknown fetch error"
      return NextResponse.json(
        {
          message: "budget forecast backend 호출에 실패했습니다.",
          detail,
        },
        { status: 502 },
      )
    }
  }
  return NextResponse.json(
    {
      message: "budget forecast backend 호출에 실패했습니다.",
      detail: "unexpected routing branch",
    },
    { status: 502 },
  )
}
