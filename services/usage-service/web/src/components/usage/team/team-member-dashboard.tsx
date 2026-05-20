"use client"

import { useEffect, useMemo, useState } from "react"
import { TeamMemberAvatar } from "@/components/common/team-member-avatar"
import {
  MEMBER_DETAIL_MESSAGES,
  logMemberDetailApiKeysFetch,
} from "@/lib/usage/messaging/dashboard-messages"
import { teamUsageBffBase } from "@/lib/usage/api/team-usage-bff-base"
import { DASHBOARD_API_KEY_ALL, DASHBOARD_API_KEY_NONE } from "@/lib/usage/dashboard-api-key-constants"
import {
  DASHBOARD_PROVIDER_ALL,
  type TeamBffApiKeyRow,
  filterTeamBffRowsByProvider,
  parseTeamBffApiKeysPayload,
  teamBffRowsToUsageMenuItems,
} from "@/lib/usage/dashboard-provider-api-keys"
import { UsageFilterBar } from "@/components/usage/usage-filter-bar"
import { useDashboardAggregateApiKeySync } from "@/lib/usage/hooks/use-dashboard-aggregate-api-key"
import { useFilterStorage } from "@/lib/usage/hooks/use-filter-storage"
import {
  assertTeamBffResponseOk,
  logTeamBffCatchError,
  memberUsageFetchError,
  TeamBffMaskedHttpError,
} from "@/lib/usage/messaging/team-bff-fetch-errors"
import { MemberAnalyticsCharts, type MemberRow } from "./member-analytics-charts"

type TeamMemberDashboardProps = {
  teamId: string
  userId: string
  isActive: boolean
}

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
  const [clientReady, setClientReady] = useState(false)
  useEffect(() => {
    setClientReady(true)
  }, [])
  const { settings, patch } = useFilterStorage("team", "team-member", { clientReady })
  const provider = settings.provider
  const apiKeyId = settings.apiKeyId
  const range = useMemo(
    () => ({ from: settings.period.from, to: settings.period.to }),
    [settings.period.from, settings.period.to],
  )
  const [apiKeyRows, setApiKeyRows] = useState<TeamBffApiKeyRow[]>([])
  const [keysLoading, setKeysLoading] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [memberRows, setMemberRows] = useState<MemberRow[]>([])

  useEffect(() => {
    if (!teamId || !isActive) {
      setApiKeyRows([])
      patch({ apiKeyId: DASHBOARD_API_KEY_ALL })
      return
    }
    let cancelled = false
    setKeysLoading(true)
    const base = teamUsageBffBase()
    if (!base) {
      setApiKeyRows([])
      patch({ apiKeyId: DASHBOARD_API_KEY_ALL })
      setKeysLoading(false)
      return
    }
    const apiKeysUrl = `${base}/teams/${encodeURIComponent(teamId)}/api-keys`
    fetch(apiKeysUrl, {
      credentials: "include",
      headers: { Accept: "application/json" },
    })
      .then(async (r) => {
        const json = await r.json()
        if (!r.ok) {
          logMemberDetailApiKeysFetch({
            request: apiKeysUrl,
            teamId,
            status: r.status,
          })
          return []
        }
        return parseTeamBffApiKeysPayload(json)
      })
      .then((rows) => {
        if (cancelled) return
        setApiKeyRows(rows)
      })
      .catch(() => {
        if (!cancelled) {
          setApiKeyRows([])
          patch({ apiKeyId: DASHBOARD_API_KEY_ALL })
        }
      })
      .finally(() => {
        if (!cancelled) setKeysLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [teamId, isActive, patch])

  const filteredApiKeyRows = useMemo(
    () => filterTeamBffRowsByProvider(apiKeyRows, provider),
    [apiKeyRows, provider],
  )
  const apiKeyMenuItems = useMemo(
    () => teamBffRowsToUsageMenuItems(filteredApiKeyRows),
    [filteredApiKeyRows],
  )

  useDashboardAggregateApiKeySync(apiKeyMenuItems, apiKeyId, (id) => patch({ apiKeyId: id }), true)

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
      setError(MEMBER_DETAIL_MESSAGES.errors.env)
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

    const teamTotalUrl = `${base}/dashboard?${qTotal}`
    fetch(teamTotalUrl, {
      credentials: "include",
      headers: { Accept: "application/json" },
    })
      .then(async (r) => {
        await assertTeamBffResponseOk(r, {
          logTag: MEMBER_DETAIL_MESSAGES.logTags.fetch,
          request: teamTotalUrl,
          maskMessage: memberUsageFetchError,
        })
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
              apiKeyId:
                apiKeyId !== DASHBOARD_API_KEY_ALL && apiKeyId !== DASHBOARD_API_KEY_NONE ? apiKeyId : undefined,
            })
            const memberUrl = `${base}/dashboard?${qMember}`
            const r = await fetch(memberUrl, {
              credentials: "include",
              headers: { Accept: "application/json" },
            })
            await assertTeamBffResponseOk(r, {
              logTag: MEMBER_DETAIL_MESSAGES.logTags.fetch,
              request: memberUrl,
              maskMessage: memberUsageFetchError,
            })
            const body = (await r.json()) as BffResponse
            memberDashboardCache.set(cacheKey, body)
            return rowFromBff(profile, body)
          }),
        )
        if (!cancelled) setMemberRows(results)
      })
      .catch((e: unknown) => {
        if (cancelled) return
        if (!(e instanceof TeamBffMaskedHttpError)) {
          logTeamBffCatchError(MEMBER_DETAIL_MESSAGES.logTags.fetch, { request: teamTotalUrl, teamId }, e)
        }
        setError(e instanceof Error ? e.message : memberUsageFetchError(0))
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
        {MEMBER_DETAIL_MESSAGES.inactiveTab}
      </div>
    )
  }

  return (
    <div className="w-full min-w-0 space-y-6">
      <UsageFilterBar
        idPrefix="member-dash"
        provider={provider}
        onProviderChange={(v) => patch({ provider: v })}
        period={settings.period}
        onPeriodChange={(p) => patch({ period: p })}
        apiKey={{
          value: apiKeyId,
          onValueChange: (id) => patch({ apiKeyId: id }),
          menuItems: apiKeyMenuItems,
          keysLoading,
          allValue: DASHBOARD_API_KEY_ALL,
          showAllOption: apiKeyMenuItems.length > 0,
          noneValue: DASHBOARD_API_KEY_NONE,
          showNoneOption: apiKeyMenuItems.length === 0,
          selectId: "member-api-key",
        }}
      />

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
            <p className="text-center text-sm text-muted-foreground">{MEMBER_DETAIL_MESSAGES.hints.noModelUsage}</p>
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
