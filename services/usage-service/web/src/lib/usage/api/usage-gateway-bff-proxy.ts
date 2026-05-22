import { NextResponse } from "next/server"

const ACCESS_TOKEN_COOKIE = "access_token"

/** 게이트웨이 `gateway.dev-mode`와 맞춘다. true면 Usage 경로에 `X-User-Id` 보강(Identity 세션 이메일). */
function isGatewayDevMode(): boolean {
  const v = process.env.GATEWAY_DEV_MODE?.toLowerCase()
  return v === "true" || v === "1"
}

function noStoreHeaders(): HeadersInit {
  return { "Cache-Control": "no-store" }
}

function envGatewayBaseUrl(): string | null {
  const url = process.env.API_GATEWAY_URL
  if (!url) return null
  return url.replace(/\/+$/, "")
}

function envIdentityBaseUrl(): string | null {
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

async function fetchSessionEmailForDev(identityBaseUrl: string, token: string): Promise<string | null> {
  let res: Response
  try {
    res = await fetch(`${identityBaseUrl}/api/auth/session`, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: "application/json",
      },
    })
  } catch {
    return null
  }
  let body: unknown
  try {
    body = await res.json()
  } catch {
    return null
  }
  if (!res.ok) return null
  if (typeof body !== "object" || body === null) return null
  const data = (body as { data?: unknown }).data
  if (typeof data !== "object" || data === null) return null
  const email = (data as { email?: unknown }).email
  return typeof email === "string" && email.length > 0 ? email : null
}

function filterUpstreamResponseHeaders(upstream: Response): Headers {
  const out = new Headers()
  const skip = new Set(
    ["connection", "content-encoding", "keep-alive", "proxy-authenticate", "proxy-authorization", "te", "trailers", "transfer-encoding", "upgrade"].map((s) => s.toLowerCase())
  )
  upstream.headers.forEach((value, key) => {
    if (skip.has(key.toLowerCase())) return
    out.append(key, value)
  })
  return out
}

/** BFF 프록시가 upstream 전에 반환하는 공통 사용자 메시지 */
export const USAGE_PROXY_MESSAGE_404 = "잘못된 요청이거나 존재하지 않는 페이지입니다."
export const USAGE_PROXY_MESSAGE_500 =
  "시스템 내부 오류가 발생했습니다. 잠시 후 다시 시도해 주세요. (Code: 500-SYS)"
export const USAGE_PROXY_MESSAGE_502 =
  "서비스가 일시적으로 원활하지 않습니다. 페이지를 새로고침해 주세요. (Code: 502-GW)"

function jsonError(status: number, message: string) {
  return NextResponse.json({ message }, { status, headers: noStoreHeaders() })
}

export function usageProxyJsonError(status: 404 | 500 | 502) {
  const message =
    status === 404
      ? USAGE_PROXY_MESSAGE_404
      : status === 500
        ? USAGE_PROXY_MESSAGE_500
        : USAGE_PROXY_MESSAGE_502
  return jsonError(status, message)
}

/**
 * Encodes each segment and joins with `/` for `${gateway}/api/v1/usage/${usagePath}`.
 */
export function encodeUsagePathSegments(segments: string[]): string {
  return segments.map((s) => encodeURIComponent(s)).join("/")
}

/**
 * Cookie → Bearer 브릿지 후 `GET|POST|… ${API_GATEWAY_URL}/api/v1/usage/${usagePath}${search}` 로 프록시한다.
 * `usagePath`는 비어 있으면 안 된다(호출부에서 검증).
 */
export async function proxyUsageToGateway(request: Request, usagePath: string): Promise<Response> {
  const token = getCookieValue(request.headers.get("cookie"), ACCESS_TOKEN_COOKIE)
  if (!token) {
    return jsonError(401, "로그인이 필요합니다")
  }

  const gatewayBase = envGatewayBaseUrl()
  if (!gatewayBase) {
    return usageProxyJsonError(500)
  }

  const url = new URL(request.url)
  const targetUrl = `${gatewayBase}/api/v1/usage/${usagePath}${url.search}`

  const method = request.method.toUpperCase()
  const outbound = new Headers()
  outbound.set("Authorization", `Bearer ${token}`)

  const accept = request.headers.get("accept")
  outbound.set("Accept", accept && accept.length > 0 ? accept : "application/json")

  const correlation = request.headers.get("x-correlation-id")
  if (correlation && correlation.length > 0) {
    outbound.set("X-Correlation-Id", correlation)
  }

  if (isGatewayDevMode()) {
    const identityBase = envIdentityBaseUrl()
    if (!identityBase) {
      return usageProxyJsonError(500)
    }
    const userId = await fetchSessionEmailForDev(identityBase, token)
    if (!userId) {
      return jsonError(401, "게이트웨이 개발 모드에서 사용자 식별에 실패했습니다")
    }
    outbound.set("X-User-Id", userId)
  }

  const hasBody = method !== "GET" && method !== "HEAD"
  const init: RequestInit & { duplex?: "half" } = {
    method,
    headers: outbound,
    redirect: "manual",
  }

  if (hasBody) {
    const contentType = request.headers.get("content-type")
    if (contentType) {
      outbound.set("Content-Type", contentType)
    }
    init.body = request.body
    init.duplex = "half"
  }

  let upstream: Response
  try {
    upstream = await fetch(targetUrl, init)
  } catch {
    return usageProxyJsonError(502)
  }

  const resHeaders = filterUpstreamResponseHeaders(upstream)
  resHeaders.set("Cache-Control", "no-store")

  return new NextResponse(upstream.body, {
    status: upstream.status,
    statusText: upstream.statusText,
    headers: resHeaders,
  })
}
