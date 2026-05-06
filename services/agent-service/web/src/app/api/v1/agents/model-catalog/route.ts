import { NextResponse } from "next/server"

const ORIGIN_PROBE_TIMEOUT_MS = 3000
const REQUEST_TIMEOUT_MS = 10000

function backendOriginCandidates(): string[] {
  const configured = (process.env.AI_AGENT_SERVICE_INTERNAL_ORIGIN ?? "").trim().replace(/\/$/, "")
  const defaults = ["http://agent-service:8096", "http://host.docker.internal:8096", "http://localhost:8096"]
  return Array.from(new Set([configured, ...defaults].filter((value) => value.length > 0)))
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

export async function GET() {
  const origin = await resolveBackendOrigin()
  if (origin == null) {
    return NextResponse.json(
      {
        message: "model catalog backend 호출에 실패했습니다. (agent-service 연결 불가)",
      },
      { status: 502 },
    )
  }

  try {
    const response = await fetchWithTimeout(`${origin}/api/v1/agents/model-catalog`, REQUEST_TIMEOUT_MS, {
      method: "GET",
      cache: "no-store",
    })
    const responseBody = await response.text()
    const contentType = response.headers.get("content-type") ?? "application/json"
    return new NextResponse(responseBody, {
      status: response.status,
      headers: { "content-type": contentType },
    })
  } catch {
    return NextResponse.json(
      {
        message: "model catalog backend 호출에 실패했습니다.",
      },
      { status: 502 },
    )
  }
}
