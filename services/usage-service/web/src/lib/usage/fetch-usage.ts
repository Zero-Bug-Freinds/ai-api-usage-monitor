import { getSafeNextPath } from "@/lib/auth/safe-next-path"

/**
 * 보호 페이지(대시보드) 전용: 401이면 `/login?next=` 로 이동 ([web-identity-bff §6.1]).
 */
export async function fetchUsageJson<T>(pathAndQuery: string): Promise<T> {
  const res = await fetch(`/api/usage/${pathAndQuery}`, {
    credentials: "include",
    headers: { Accept: "application/json" },
    cache: "no-store",
  })

  if (res.status === 401) {
    const next = encodeURIComponent(getSafeNextPath("/dashboard"))
    const idOrigin = (process.env.NEXT_PUBLIC_IDENTITY_WEB_ORIGIN ?? "").replace(/\/$/, "")
    window.location.assign(`${idOrigin}/login?next=${next}`)
    throw new Error("Unauthorized")
  }

  if (!res.ok) {
    let message = res.statusText
    try {
      const body = await res.json()
      if (body && typeof body === "object" && "message" in body && typeof (body as { message: unknown }).message === "string") {
        message = (body as { message: string }).message
      }
    } catch {
      try {
        message = await res.text()
      } catch {
        /* ignore */
      }
    }
    throw new Error(message || `HTTP ${res.status}`)
  }

  return res.json() as Promise<T>
}

export function buildUsageQuery(params: Record<string, string | number | undefined | null>): string {
  const sp = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === "") continue
    sp.set(k, String(v))
  }
  const q = sp.toString()
  return q.length > 0 ? `?${q}` : ""
}
