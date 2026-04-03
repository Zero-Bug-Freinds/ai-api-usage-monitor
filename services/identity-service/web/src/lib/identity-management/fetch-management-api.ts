import { getSafeNextPath } from "@/lib/auth/safe-next-path"
import type { ApiResponse } from "@/lib/api/identity/types"

function isApiResponse(value: unknown): value is ApiResponse<unknown> {
  if (typeof value !== "object" || value === null) return false
  const o = value as Record<string, unknown>
  return typeof o.success === "boolean" && typeof o.message === "string" && "data" in o
}

/**
 * 보호 페이지 전용: 쿠키 Bearer로 `/api/identity/v1/...` 호출.
 * 401이면 `nextPath` 기준으로 `/login?next=` 이동 ([web-identity-bff §6.1]).
 */
export async function fetchIdentityManagement<T>(pathAndQuery: string, loginNextPath: string): Promise<T> {
  const trimmed = pathAndQuery.replace(/^\/+/, "")
  const res = await fetch(`/api/identity/${trimmed}`, {
    credentials: "include",
    headers: { Accept: "application/json" },
    cache: "no-store",
  })

  if (res.status === 401) {
    const next = encodeURIComponent(getSafeNextPath(loginNextPath))
    window.location.assign(`/login?next=${next}`)
    throw new Error("Unauthorized")
  }

  let body: unknown
  try {
    body = await res.json()
  } catch {
    body = null
  }

  if (!res.ok) {
    let message = res.statusText
    if (isApiResponse(body)) {
      message = body.message || message
    } else if (body && typeof body === "object" && "message" in body && typeof (body as { message: unknown }).message === "string") {
      message = (body as { message: string }).message
    }
    const err = new Error(message || `HTTP ${res.status}`)
    ;(err as Error & { status?: number }).status = res.status
    throw err
  }

  if (isApiResponse(body)) {
    if (!body.success) {
      throw new Error(body.message || "요청에 실패했습니다")
    }
    return body.data as T
  }

  return body as T
}
