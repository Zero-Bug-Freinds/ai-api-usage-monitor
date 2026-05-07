/**
 * 팀 사용량 UI → usage-web BFF (`/dashboard/api/usage/team/bff/...`).
 * `NEXT_PUBLIC_USAGE_TEAM_BFF_BASE`가 있으면 해당 값을 우선 사용한다.
 */
export function teamUsageBffBase(): string {
  const override = (process.env.NEXT_PUBLIC_USAGE_TEAM_BFF_BASE ?? "").trim().replace(/\/+$/, "")
  if (override) return override
  if (typeof window === "undefined") return ""
  return `${window.location.origin}/dashboard/api/usage/team/bff`
}
