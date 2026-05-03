export type TeamRouteSection = "dashboard" | "memberDetail";

/**
 * URL `tab` 쿼리를 정규화한다. 레거시 `members`·`api-keys`·`memberDetail`은 모두 `memberDetail`로 수렴한다.
 */
export function normalizeTab(q: string | string[] | undefined): TeamRouteSection {
  const raw = Array.isArray(q) ? q[0] : q;
  if (raw === "memberDetail" || raw === "members" || raw === "api-keys") {
    return "memberDetail";
  }
  return "dashboard";
}
