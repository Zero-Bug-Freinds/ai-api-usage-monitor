/** 팀별 나의 사용량·사용 로그 팀 탭과 동일 localStorage 키 (대시보드 `last_team_id` 와 분리). */
export const MY_USAGE_BY_TEAM_LAST_SELECTED_TEAM_ID = "MY_USAGE_BY_TEAM_LAST_SELECTED_TEAM_ID"

export type MemberTeamSummary = { id: string; name: string; createdAt?: string }

export function pickOldestMemberTeamId(list: MemberTeamSummary[]): string {
  if (list.length === 0) return ""
  const dated = list.filter((t) => t.createdAt)
  if (dated.length === 0) return list[0]!.id
  return [...dated].sort((a, b) => (a.createdAt ?? "").localeCompare(b.createdAt ?? ""))[0]!.id
}

export function pickMemberTeamIdFromSources(list: MemberTeamSummary[]): string {
  if (list.length === 0) return ""
  if (typeof window !== "undefined") {
    const saved = window.localStorage.getItem(MY_USAGE_BY_TEAM_LAST_SELECTED_TEAM_ID)
    if (saved && list.some((t) => t.id === saved)) {
      return saved
    }
  }
  return pickOldestMemberTeamId(list)
}
