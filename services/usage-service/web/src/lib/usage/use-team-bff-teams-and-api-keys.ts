"use client"

import * as React from "react"
import { teamUsageBffBase } from "@/lib/usage/team-usage-bff-base"
import {
  MY_USAGE_BY_TEAM_LAST_SELECTED_TEAM_ID,
  type MemberTeamSummary,
  pickMemberTeamIdFromSources,
} from "@/lib/usage/team-member-team-picker"
import { parseTeamBffApiKeysPayload, type TeamBffApiKeyRow } from "@/lib/usage/dashboard-provider-api-keys"

/**
 * 팀 BFF `/teams`, `/teams/{id}/api-keys` 로 소속 팀·팀 API 키 목록을 로드한다.
 * 대시보드 "팀별 나의 사용량"과 동일한 데이터 소스.
 */
export function useTeamBffTeamsAndApiKeys(enabled: boolean) {
  const [memberTeams, setMemberTeams] = React.useState<MemberTeamSummary[]>([])
  const [memberTeamsLoading, setMemberTeamsLoading] = React.useState(false)
  const [memberTeamsErr, setMemberTeamsErr] = React.useState<string | null>(null)
  const [teamMemberTeamId, setTeamMemberTeamId] = React.useState("")
  const [teamMemberRawApiKeyRows, setTeamMemberRawApiKeyRows] = React.useState<TeamBffApiKeyRow[]>([])
  const [teamMemberKeysLoading, setTeamMemberKeysLoading] = React.useState(false)

  React.useEffect(() => {
    if (!enabled) {
      setMemberTeams([])
      setMemberTeamsErr(null)
      setMemberTeamsLoading(false)
      setTeamMemberTeamId("")
      return
    }
    let cancelled = false
    void (async () => {
      setMemberTeamsLoading(true)
      setMemberTeamsErr(null)
      const base = teamUsageBffBase()
      if (!base) {
        if (!cancelled) {
          setMemberTeamsErr("사용량 API 베이스 URL을 확인할 수 없습니다")
          setMemberTeams([])
          setMemberTeamsLoading(false)
        }
        return
      }
      try {
        const res = await fetch(`${base}/teams`, { credentials: "include", headers: { Accept: "application/json" } })
        const json = (await res.json()) as { teams?: unknown }
        if (!res.ok || !Array.isArray(json.teams)) {
          if (!cancelled) setMemberTeamsErr("팀 목록을 불러오지 못했습니다")
          return
        }
        const list = (json.teams as unknown[])
          .map((item): MemberTeamSummary | null => {
            if (!item || typeof item !== "object") return null
            const o = item as Record<string, unknown>
            if (typeof o.id !== "string" && typeof o.id !== "number") return null
            if (typeof o.name !== "string") return null
            return {
              id: String(o.id),
              name: o.name,
              createdAt: typeof o.createdAt === "string" ? o.createdAt : undefined,
            }
          })
          .filter((x): x is MemberTeamSummary => x !== null)
        if (!cancelled) {
          setMemberTeams(list)
          setMemberTeamsErr(null)
        }
      } catch {
        if (!cancelled) setMemberTeamsErr("팀 목록을 불러오지 못했습니다")
      } finally {
        if (!cancelled) setMemberTeamsLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [enabled])

  React.useEffect(() => {
    if (!enabled) return
    setTeamMemberTeamId((prev) => {
      if (prev && memberTeams.some((t) => t.id === prev)) return prev
      return pickMemberTeamIdFromSources(memberTeams)
    })
  }, [enabled, memberTeams])

  React.useEffect(() => {
    if (!enabled || !teamMemberTeamId || typeof window === "undefined") return
    try {
      window.localStorage.setItem(MY_USAGE_BY_TEAM_LAST_SELECTED_TEAM_ID, teamMemberTeamId)
    } catch {
      /* ignore */
    }
  }, [enabled, teamMemberTeamId])

  React.useEffect(() => {
    if (!enabled || !teamMemberTeamId) {
      setTeamMemberRawApiKeyRows([])
      setTeamMemberKeysLoading(false)
      return
    }
    let cancelled = false
    setTeamMemberKeysLoading(true)
    const base = teamUsageBffBase()
    if (!base) {
      setTeamMemberRawApiKeyRows([])
      setTeamMemberKeysLoading(false)
      return
    }
    void fetch(`${base}/teams/${encodeURIComponent(teamMemberTeamId)}/api-keys`, {
      credentials: "include",
      headers: { Accept: "application/json" },
    })
      .then(async (r) => {
        const json = await r.json()
        if (!r.ok) {
          if (!cancelled) setTeamMemberRawApiKeyRows([])
          return
        }
        if (!cancelled) setTeamMemberRawApiKeyRows(parseTeamBffApiKeysPayload(json))
      })
      .catch(() => {
        if (!cancelled) setTeamMemberRawApiKeyRows([])
      })
      .finally(() => {
        if (!cancelled) setTeamMemberKeysLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [enabled, teamMemberTeamId])

  return {
    memberTeams,
    memberTeamsLoading,
    memberTeamsErr,
    teamMemberTeamId,
    setTeamMemberTeamId,
    teamMemberRawApiKeyRows,
    teamMemberKeysLoading,
  }
}
