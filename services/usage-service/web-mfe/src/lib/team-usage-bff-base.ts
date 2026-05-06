/**
 * 팀 MF → usage-web BFF (`/dashboard/api/usage/team/bff/...`).
 * `NEXT_PUBLIC_USAGE_TEAM_BFF_BASE`가 있으면 그 접두만 사용(끝 슬래시 제거).
 * 미설정 시 브라우저에서는 동일 오리진 + `/dashboard/api/usage/team/bff`.
 */
export function teamUsageBffBase(): string {
  const override = (process.env.NEXT_PUBLIC_USAGE_TEAM_BFF_BASE ?? "").trim().replace(/\/+$/, "");
  if (override) return override;
  if (typeof window === "undefined") return "";
  return `${window.location.origin}/dashboard/api/usage/team/bff`;
}
