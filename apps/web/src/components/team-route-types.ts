export type TeamRouteSection = "dashboard" | "members" | "api-keys";

export function normalizeTab(q: string | string[] | undefined): TeamRouteSection {
  const raw = Array.isArray(q) ? q[0] : q;
  if (raw === "members" || raw === "api-keys") return raw;
  return "dashboard";
}
