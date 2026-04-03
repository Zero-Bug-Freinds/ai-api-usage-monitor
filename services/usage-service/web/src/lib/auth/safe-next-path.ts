/**
 * 미들웨어 등에서 전달된 `next`는 동일 출처 내부 경로만 허용한다 (오픈 리다이렉트 방지).
 */
export function getSafeNextPath(raw: string | null | undefined): string {
  const fallback = "/dashboard"
  if (raw == null || raw === "") return fallback

  let decoded: string
  try {
    decoded = decodeURIComponent(raw.trim())
  } catch {
    return fallback
  }

  if (!decoded.startsWith("/") || decoded.startsWith("//")) return fallback
  if (decoded.includes("://") || decoded.includes("\\")) return fallback

  return decoded || fallback
}
