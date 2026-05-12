"use client"

import { useEffect, useMemo, useState } from "react"
import {
  Label,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@ai-usage/ui"
import { TeamMemberAvatar } from "@/components/common/team-member-avatar"
import { formatKstIsoDate, addKstDays } from "@/lib/usage/kst-dates"
import { EMPTY_MEMBER_MODEL_USAGE_MSG } from "@/lib/usage/team-dashboard-empty"
import { teamUsageBffBase } from "@/lib/usage/team-usage-bff-base"
import { DASHBOARD_API_KEY_ALL, DASHBOARD_API_KEY_NONE } from "@/lib/usage/dashboard-api-key-constants"
import {
  DASHBOARD_PROVIDER_ALL,
  type TeamBffApiKeyRow,
  filterTeamBffRowsByProvider,
  parseTeamBffApiKeysPayload,
  teamBffRowsToUsageMenuItems,
} from "@/lib/usage/dashboard-provider-api-keys"
import { DashboardApiKeySelectMenu } from "@/components/usage/dashboard-api-key-select-menu"
import { useDashboardAggregateApiKeySync } from "@/lib/usage/use-dashboard-aggregate-api-key"
import { MemberAnalyticsCharts, type MemberRow } from "./member-analytics-charts"

type TeamMemberDashboardProps = {
  teamId: string
  userId: string
  isActive: boolean
}

type PeriodMode = "today" | "7d" | "30d"
type TeamMemberProfile = { userId: string; displayName?: string; role?: string }
type ModelAgg = {
  model: string
  provider: string
  requestCount: number
  inputTokens?: number
  outputTokens?: number
  estimatedReasoningTokens?: number
}
type BffSummary = {
  totalRequests: number
  totalErrors: number
  totalInputTokens: number
  totalEstimatedCost?: number
  avgLatencyMs?: number | null
}
type BffResponse = {
  byModel?: ModelAgg[]
  memberProfiles?: TeamMemberProfile[]
  summary?: BffSummary
}
type MemberSeries = { userId: string; displayName: string; requests: number }

const memberDashboardCache = new Map<string, BffResponse>()

function memberUsageFetchError(status: number): string {
  if (status === 400) return "멤버 상세 조회 파라미터가 올바르지 않습니다."
  if (status === 401 || status === 403) return "로그인 세션이 만료되었거나 접근 권한이 없습니다."
  if (status === 404) return "멤버 상세 엔드포인트를 찾지 못했습니다."
  if (status >= 500) return "멤버 상세 서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
  return `멤버 상세 조회 실패 (HTTP ${status})`
}

function presetRange(mode: PeriodMode, todayKst: string): { from: string; to: string } {
  switch (mode) {
    case "today":
      return { from: todayKst, to: todayKst }
    case "7d":
      return { from: addKstDays(todayKst, -6), to: todayKst }
    case "30d":
      return { from: addKstDays(todayKst, -29), to: todayKst }
    default:
      return { from: todayKst, to: todayKst }
  }
}

function usageQuery(params: Record<string, string | undefined>): string {
  const sp = new URLSearchParams()
  sp.set("mode", "TEAM_MEMBER")
  for (const [k, v] of Object.entries(params)) {
    if (!v) continue
    sp.set(k, v)
  }
  return sp.toString()
}

function teamTotalQuery(params: Record<string, string | undefined>): string {
  const sp = new URLSearchParams()
  sp.set("mode", "TEAM_TOTAL")
  for (const [k, v] of Object.entries(params)) {
    if (!v) continue
    sp.set(k, v)
  }
  return sp.toString()
}

function rowFromBff(profile: TeamMemberProfile, body: BffResponse): MemberRow {
  return {
    profile,
    byModel: (body.byModel ?? []) as MemberRow["byModel"],
    summary: body.summary,
  }
}

export default function TeamMemberDashboard({ teamId, userId, isActive }: TeamMemberDashboardProps) {
  const todayKst = formatKstIsoDate()
  const [periodMode, setPeriodMode] = useState<PeriodMode>("7d")
  const [provider, setProvider] = useState<string>(DASHBOARD_PROVIDER_ALL)
  const [apiKeyRows, setApiKeyRows] = useState<TeamBffApiKeyRow[]>([])
  const [apiKeyId, setApiKeyId] = useState<string>(DASHBOARD_API_KEY_ALL)
  const [keysLoading, setKeysLoading] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [memberRows, setMemberRows] = useState<MemberRow[]>([])
  const range = useMemo(() => presetRange(periodMode, todayKst), [periodMode, todayKst])

  useEffect(() => {
    if (!teamId || !isActive) {
      setApiKeyRows([])
      setApiKeyId(DASHBOARD_API_KEY_ALL)
      return
    }
    let cancelled = false
    setKeysLoading(true)
    const base = teamUsageBffBase()
    if (!base) {
      setApiKeyRows([])
      setApiKeyId(DASHBOARD_API_KEY_ALL)
      setKeysLoading(false)
      return
    }
    fetch(`${base}/teams/${encodeURIComponent(teamId)}/api-keys`, {
      credentials: "include",
      headers: { Accept: "application/json" },
    })
      .then(async (r) => {
        const json = await r.json()
        if (!r.ok) return []
        return parseTeamBffApiKeysPayload(json)
      })
      .then((rows) => {
        if (cancelled) return
        setApiKeyRows(rows)
      })
      .catch(() => {
        if (!cancelled) {
          setApiKeyRows([])
          setApiKeyId(DASHBOARD_API_KEY_ALL)
        }
      })
      .finally(() => {
        if (!cancelled) setKeysLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [teamId, isActive])

  const filteredApiKeyRows = useMemo(
    () => filterTeamBffRowsByProvider(apiKeyRows, provider),
    [apiKeyRows, provider],
  )
  const apiKeyMenuItems = useMemo(
    () => teamBffRowsToUsageMenuItems(filteredApiKeyRows),
    [filteredApiKeyRows],
  )

  useDashboardAggregateApiKeySync(apiKeyMenuItems, apiKeyId, setApiKeyId, true)

  useEffect(() => {
    setMemberRows([])
    setError(null)
  }, [teamId])

  useEffect(() => {
    if (!isActive || !teamId) {
      setLoading(false)
      return
    }
    const base = teamUsageBffBase()
    if (!base) {
      setError("사용량 API 베이스 URL을 확인할 수 없습니다.")
      return
    }
    let cancelled = false
    setLoading(true)
    setError(null)
    setMemberRows([])
    const qTotal = teamTotalQuery({
      teamId,
      from: range.from,
      to: range.to,
      provider: provider === DASHBOARD_PROVIDER_ALL ? undefined : provider,
      apiKeyId:
        apiKeyId !== DASHBOARD_API_KEY_ALL && apiKeyId !== DASHBOARD_API_KEY_NONE ? apiKeyId : undefined,
    })

    fetch(`${base}/dashboard?${qTotal}`, {
      credentials: "include",
      headers: { Accept: "application/json" },
    })
      .then(async (r) => {
        if (!r.ok) throw new Error(memberUsageFetchError(r.status))
        return (await r.json()) as BffResponse
      })
      .then(async (teamTotal) => {
        if (cancelled) return
        const profiles = (teamTotal.memberProfiles ?? []).filter((p) => !!p.userId)
        if (profiles.length === 0) {
          setMemberRows([])
          return
        }

        const results = await Promise.all(
          profiles.map(async (profile) => {
            const cacheKey = [teamId, profile.userId, range.from, range.to, provider, apiKeyId].join("|")
            const cached = memberDashboardCache.get(cacheKey)
            if (cached) return rowFromBff(profile, cached)
            const qMember = usageQuery({
              teamId,
              userId: profile.userId,
              from: range.from,
              to: range.to,
              provider: provider === DASHBOARD_PROVIDER_ALL ? undefined : provider,
            })
            const r = await fetch(`${base}/dashboard?${qMember}`, {
              credentials: "include",
              headers: { Accept: "application/json" },
            })
            if (!r.ok) throw new Error(memberUsageFetchError(r.status))
            const body = (await r.json()) as BffResponse
            memberDashboardCache.set(cacheKey, body)
            return rowFromBff(profile, body)
          }),
        )
        if (!cancelled) setMemberRows(results)
      })
      .catch((e: Error) => {
        if (!cancelled) setError(e.message)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [isActive, teamId, range.from, range.to, provider, apiKeyId])

  const memberSeries = useMemo<MemberSeries[]>(
    () =>
      memberRows.map(({ profile, byModel }) => ({
        userId: profile.userId,
        displayName: profile.displayName?.trim() || profile.userId,
        requests: byModel.reduce((sum, m) => sum + Math.max(0, m.requestCount), 0),
      })),
    [memberRows],
  )

  const hasData = useMemo(
    () => memberRows.some((r) => r.byModel.some((m) => Math.max(0, m.requestCount) > 0)),
    [memberRows],
  )

  const memberNameById = useMemo(
    () => memberSeries.reduce<Record<string, string>>((acc, cur) => ({ ...acc, [cur.userId]: cur.displayName }), {}),
    [memberSeries],
  )

  if (!isActive) {
    return (
      <div className="rounded-lg border border-border bg-card p-4 text-sm text-muted-foreground">
        멤버 상세 탭을 선택하면 데이터를 불러옵니다.
      </div>
    )
  }

  return (
    <div className="w-full min-w-0 space-y-6">
      <div className="flex flex-wrap items-end gap-4">
        <div className="space-y-2 sm:w-52">
          <Label>공급사</Label>
          <Select value={provider} onValueChange={setProvider}>
            <SelectTrigger><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value={DASHBOARD_PROVIDER_ALL}>전체</SelectItem>
              <SelectItem value="GOOGLE">Gemini (Google)</SelectItem>
              <SelectItem value="OPENAI">OpenAI</SelectItem>
              <SelectItem value="ANTHROPIC">Anthropic</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-2 sm:w-52">
          <Label>API Key</Label>
          <Select value={apiKeyId} onValueChange={setApiKeyId} disabled={keysLoading}>
            <SelectTrigger>
              <SelectValue placeholder={keysLoading ? "불러오는 중…" : apiKeyMenuItems.length === 0 ? "없음" : "전체"} />
            </SelectTrigger>
            <SelectContent className="max-h-[min(70vh,26rem)]">
              <DashboardApiKeySelectMenu
                items={apiKeyMenuItems}
                allValue={DASHBOARD_API_KEY_ALL}
                showAllOption={apiKeyMenuItems.length > 0}
                noneValue={DASHBOARD_API_KEY_NONE}
                showNoneOption={apiKeyMenuItems.length === 0}
              />
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-2 sm:w-44">
          <Label htmlFor="member-period">기간</Label>
          <Select value={periodMode} onValueChange={(v) => setPeriodMode(v as PeriodMode)}>
            <SelectTrigger id="member-period"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="today">오늘</SelectItem>
              <SelectItem value="7d">최근 7일</SelectItem>
              <SelectItem value="30d">최근 30일</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>

      {error ? <p className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p> : null}
      {loading ? (
        <div className="space-y-4" aria-busy="true">
          <div className="h-[320px] animate-pulse rounded-lg border border-border bg-muted/40" />
          <div className="h-[320px] animate-pulse rounded-lg border border-border bg-muted/40" />
          <div className="h-[340px] animate-pulse rounded-lg border border-border bg-muted/40" />
        </div>
      ) : null}

      {!loading && !error && !hasData ? (
        <section className="rounded-lg border border-border p-4 shadow-sm">
          <h2 className="mb-4 text-lg font-medium">팀원별 분석</h2>
          <div className="flex min-h-[240px] items-center justify-center rounded-md border border-dashed border-border bg-muted/20 px-4 py-12">
            <p className="text-center text-sm text-muted-foreground">{EMPTY_MEMBER_MODEL_USAGE_MSG}</p>
          </div>
        </section>
      ) : null}

      {!loading && !error && hasData ? (
        <>
          <div className="flex flex-wrap items-center gap-x-3 gap-y-2 rounded-lg border border-border/80 bg-muted/15 px-3 py-2">
            <span className="text-xs font-medium text-muted-foreground">멤버</span>
            {memberSeries.map((m) => (
              <span
                key={m.userId}
                className="inline-flex items-center gap-1.5 rounded-full border border-border bg-card px-2 py-1 text-xs text-foreground shadow-sm"
              >
                <TeamMemberAvatar userId={m.userId} size={16} className="ring-0" />
                <span className="max-w-[12rem] truncate" title={m.displayName}>
                  {m.displayName}
                </span>
              </span>
            ))}
          </div>
          <MemberAnalyticsCharts memberRows={memberRows} memberNameById={memberNameById} />
        </>
      ) : null}

      {!loading && !error && userId ? (
        <p className="text-xs text-muted-foreground">
          현재 선택된 사용자 힌트: <span className="font-medium text-foreground">{userId}</span> (멤버 전체 집계 기준으로 표시 중)
        </p>
      ) : null}
    </div>
  )
}
