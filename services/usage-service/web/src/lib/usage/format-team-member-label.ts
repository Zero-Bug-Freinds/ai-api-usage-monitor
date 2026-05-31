/**
 * Team log table: show local part before @ when member id is email-like.
 */
export function formatTeamMemberLocalId(memberUserId: string | null | undefined): string {
  if (memberUserId == null || memberUserId.trim() === "") return "—"
  const s = memberUserId.trim()
  const at = s.indexOf("@")
  if (at > 0) return s.slice(0, at)
  if (s.length <= 12) return s
  return `${s.slice(0, 12)}…`
}
