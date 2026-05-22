import { warnStorageError } from "@/lib/usage/messaging/storage-errors"

/** 팀 대시보드 선택 팀 — 멤버별 분석 탭 새로고침 시에도 동일 팀을 복원한다. */
export const TEAM_DASHBOARD_LAST_TEAM_ID_KEY = "last_team_id"

export function readTeamDashboardLastTeamId(): string {
  if (typeof window === "undefined") return ""
  try {
    const raw = window.localStorage.getItem(TEAM_DASHBOARD_LAST_TEAM_ID_KEY)
    return raw?.trim() ?? ""
  } catch (e) {
    warnStorageError(e)
    return ""
  }
}

export function writeTeamDashboardLastTeamId(teamId: string): void {
  const normalized = teamId.trim()
  if (typeof window === "undefined" || normalized.length === 0) return
  try {
    window.localStorage.setItem(TEAM_DASHBOARD_LAST_TEAM_ID_KEY, normalized)
  } catch (e) {
    warnStorageError(e)
  }
}
