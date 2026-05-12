"use client"

import * as React from "react"
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ComposedChart,
  Legend,
  Line,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts"
import {
  Button,
  Input,
  Label,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@ai-usage/ui"
import { formatKstIsoDate, addKstDays } from "@/lib/usage/kst-dates"
import { formatRequestCount, formatTokenCount, formatUsd, toNumber } from "@/lib/usage/format"
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
const LAST_TEAM_STORAGE_KEY = "last_team_id"

export type TeamDashboardProps = {
  viewTeamIdFromQuery?: string
  onSelectUser: (userId: string) => void
  onEffectiveTeamChange?: (teamId: string) => void
}

type UsageSeriesPoint = {
  bucketLabel: string
  requestCount: number
  errorCount: number
  inputTokens: number
  estimatedCost: number | string
}

type ModelAgg = {
  model: string
  provider: string
  requestCount: number
  inputTokens: number
  estimatedReasoningTokens: number
  outputTokens: number
}

type BffResponse = {
  summary?: {
    totalRequests?: number
    totalErrors?: number
    totalInputTokens?: number
    totalEstimatedCost?: number | string
  }
  usageSeries?: UsageSeriesPoint[]
  usageSeriesUnit?: "HOUR" | "DAY" | "MONTH"
  byModel?: ModelAgg[]
  memberProfiles?: Array<{ userId: string; displayName?: string; role?: string }>
  enrichment?: { partial?: boolean; warnings?: string[] }
}

type TeamSummary = { id: string; name: string; createdAt?: string }
type PeriodMode = "today" | "7d" | "30d" | "custom"

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const AnyLegend = Legend as any

function usageFetchErrorMessage(status: number): string {
  if (status === 400) return "팀/기간 필터를 확인해 주세요."
  if (status === 401 || status === 403) return "인증이 만료되었거나 권한이 없습니다. 다시 로그인해 주세요."
  if (status === 404) return "대시보드 페이지를 찾지 못했습니다."
  if (status >= 500) return "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
  return `사용량 데이터를 불러오지 못했습니다. (HTTP ${status})`
}

function buildUsageDashboardQuery(params: Record<string, string | undefined | null>): string {
  const sp = new URLSearchParams()
  sp.set("mode", "TEAM_TOTAL")
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === "") continue
    sp.set(k, String(v))
  }
  return sp.toString()
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

function pickOldestTeamId(list: TeamSummary[]): string {
  if (list.length === 0) return ""
  const dated = list.filter((t) => t.createdAt)
  if (dated.length === 0) return list[0]!.id
  return [...dated].sort((a, b) => (a.createdAt ?? "").localeCompare(b.createdAt ?? ""))[0]!.id
}

function pickTeamIdFromSources(list: TeamSummary[], viewQ: string | undefined): string {
  if (list.length === 0) return ""
  if (viewQ && list.some((t) => t.id === viewQ)) return viewQ
  if (typeof window !== "undefined") {
    const saved = window.localStorage.getItem(LAST_TEAM_STORAGE_KEY)
    if (saved && list.some((t) => t.id === saved)) {
      return saved
    }
  }
  return pickOldestTeamId(list)
}

type MainRow = { label: string; requestCount: number; successRate: number; errorRate: number }

function mainChartTitle(unit: string | undefined): string {
  if (unit === "HOUR") return "시간별 요청·성공률·오류율"
  if (unit === "DAY") return "일별 요청·성공률·오류율"
  if (unit === "MONTH") return "월별 요청·성공률·오류율"
  return "요청·성공률·오류율"
}

function stabilityRateDomain(rows: MainRow[]): [number, number] {
  let max = 0
  for (const r of rows) max = Math.max(max, r.successRate, r.errorRate)
  const hi = Math.max(100, Math.ceil(max * 1.1))
  return [0, hi]
}

function hashToUint(str: string): number {
  let h = 2166136261
  for (let i = 0; i < str.length; i++) {
    h ^= str.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return h >>> 0
}

const MODEL_PALETTE = ["#9a3412", "#c2410c", "#ea580c", "#F97316", "#fb923c", "#fdba74", "#64748b"]

/** 로딩·성공 동일 레이아웃용 (기존 성공 상태 높이에 맞춤). */
const TEAM_DASH_MAIN_CHART_H = "h-[380px] min-h-[380px]"
const TEAM_DASH_PIE_WRAP_MIN = "min-h-[300px]"
const TEAM_DASH_BAR_CHART_H = "h-[300px] min-h-[300px]"
const TEAM_DASH_STATS_ROW_MIN = "min-h-[2.75rem]"

function TeamDashBlockSkeleton({ className }: { className?: string }) {
  return (
    <div
      className={`animate-pulse rounded-lg border border-border bg-muted/35 ${className ?? ""}`}
      aria-hidden="true"
    />
  )
}

function TeamDashStatsRowSkeleton() {
  return (
    <div className={`mt-3 flex flex-wrap gap-3 ${TEAM_DASH_STATS_ROW_MIN}`} aria-hidden="true">
      <div className="h-4 w-24 animate-pulse rounded bg-muted/50" />
      <div className="h-4 w-28 animate-pulse rounded bg-muted/50" />
      <div className="h-4 w-20 animate-pulse rounded bg-muted/50" />
      <div className="h-4 w-32 animate-pulse rounded bg-muted/50" />
    </div>
  )
}

function colorForModel(model: string, provider: string): string {
  const idx = hashToUint(`${provider}::${model}`) % MODEL_PALETTE.length
  return MODEL_PALETTE[idx] ?? "#94a3b8"
}

function truncateModelLabel(model: string, max = 36): string {
  if (model.length <= max) return model
  return `${model.slice(0, max - 1)}…`
}

export default function TeamDashboard({
  viewTeamIdFromQuery,
  onSelectUser,
  onEffectiveTeamChange,
}: TeamDashboardProps) {
  const todayKst = formatKstIsoDate()
  const [dashProvider, setDashProvider] = React.useState(DASHBOARD_PROVIDER_ALL)
  const [periodMode, setPeriodMode] = React.useState<PeriodMode>("today")
  const [customFrom, setCustomFrom] = React.useState(todayKst)
  const [customTo, setCustomTo] = React.useState(todayKst)
  const range = React.useMemo(
    () => (periodMode === "custom" ? { from: customFrom, to: customTo } : presetRange(periodMode, todayKst)),
    [periodMode, customFrom, customTo, todayKst],
  )
  const [teams, setTeams] = React.useState<TeamSummary[]>([])
  const [teamsLoading, setTeamsLoading] = React.useState(true)
  const [teamsErr, setTeamsErr] = React.useState<string | null>(null)
  const [selectedTeamId, setSelectedTeamId] = React.useState("")
  const [apiKeyRows, setApiKeyRows] = React.useState<TeamBffApiKeyRow[]>([])
  const [keysLoading, setKeysLoading] = React.useState(false)
  const [selectedApiKeyId, setSelectedApiKeyId] = React.useState<string>(DASHBOARD_API_KEY_ALL)
  const [loading, setLoading] = React.useState(false)
  const [error, setError] = React.useState<string | null>(null)
  const [data, setData] = React.useState<BffResponse | null>(null)
  const [refresh, setRefresh] = React.useState(0)

  React.useEffect(() => {
    let cancelled = false
    void (async () => {
      setTeamsLoading(true)
      const base = teamUsageBffBase()
      if (!base) {
        if (!cancelled) {
          setTeamsErr("사용량 API 베이스 URL을 확인할 수 없습니다")
          setTeamsLoading(false)
        }
        return
      }
      try {
        const res = await fetch(`${base}/teams`, { credentials: "include", headers: { Accept: "application/json" } })
        const json = (await res.json()) as { teams?: unknown }
        if (!res.ok || !Array.isArray(json.teams)) {
          if (!cancelled) setTeamsErr("팀 목록을 불러오지 못했습니다")
          return
        }
        const list = (json.teams as unknown[])
          .map((item): TeamSummary | null => {
            if (!item || typeof item !== "object") return null
            const o = item as Record<string, unknown>
            if (typeof o.id !== "string" && typeof o.id !== "number") return null
            if (typeof o.name !== "string") return null
            return { id: String(o.id), name: o.name, createdAt: typeof o.createdAt === "string" ? o.createdAt : undefined }
          })
          .filter((x): x is TeamSummary => x !== null)
        if (cancelled) return
        setTeams(list)
        setTeamsErr(null)
      } catch {
        if (!cancelled) setTeamsErr("팀 목록을 불러오지 못했습니다")
      } finally {
        if (!cancelled) setTeamsLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  React.useEffect(() => {
    setSelectedTeamId((prev) => {
      if (viewTeamIdFromQuery && teams.some((t) => t.id === viewTeamIdFromQuery)) return viewTeamIdFromQuery
      if (prev && teams.some((t) => t.id === prev)) return prev
      return pickTeamIdFromSources(teams, viewTeamIdFromQuery)
    })
  }, [teams, viewTeamIdFromQuery])

  React.useEffect(() => {
    if (!selectedTeamId || typeof window === "undefined") return
    window.localStorage.setItem(LAST_TEAM_STORAGE_KEY, selectedTeamId)
  }, [selectedTeamId])

  React.useEffect(() => {
    if (!selectedTeamId) {
      setApiKeyRows([])
      return
    }
    let cancelled = false
    setKeysLoading(true)
    const base = teamUsageBffBase()
    if (!base) {
      setApiKeyRows([])
      setKeysLoading(false)
      return
    }
    fetch(`${base}/teams/${encodeURIComponent(selectedTeamId)}/api-keys`, {
      credentials: "include",
      headers: { Accept: "application/json" },
    })
      .then(async (r) => {
        const json = await r.json()
        if (!r.ok) return []
        return parseTeamBffApiKeysPayload(json)
      })
      .then((sorted) => {
        if (cancelled) return
        setApiKeyRows(sorted)
      })
      .catch(() => {
        if (!cancelled) setApiKeyRows([])
      })
      .finally(() => {
        if (!cancelled) setKeysLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [selectedTeamId])

  const filteredApiKeyRows = React.useMemo(
    () => filterTeamBffRowsByProvider(apiKeyRows, dashProvider),
    [apiKeyRows, dashProvider],
  )
  const apiKeyMenuItems = React.useMemo(
    () => teamBffRowsToUsageMenuItems(filteredApiKeyRows),
    [filteredApiKeyRows],
  )

  useDashboardAggregateApiKeySync(apiKeyMenuItems, selectedApiKeyId, setSelectedApiKeyId, true)

  const effectiveTeamId = selectedTeamId
  React.useEffect(() => {
    onEffectiveTeamChange?.(effectiveTeamId)
  }, [effectiveTeamId, onEffectiveTeamChange])

  React.useEffect(() => {
    if (!effectiveTeamId) {
      setData(null)
      onSelectUser("")
      return
    }
    const base = teamUsageBffBase()
    if (!base) {
      setError("사용량 API 베이스 URL을 확인할 수 없습니다")
      return
    }
    let cancelled = false
    setLoading(true)
    setError(null)
    const q = buildUsageDashboardQuery({
      teamId: effectiveTeamId,
      from: range.from,
      to: range.to,
      provider: dashProvider === DASHBOARD_PROVIDER_ALL ? undefined : dashProvider,
      apiKeyId:
        selectedApiKeyId !== DASHBOARD_API_KEY_ALL && selectedApiKeyId !== DASHBOARD_API_KEY_NONE
          ? selectedApiKeyId
          : undefined,
    })
    fetch(`${base}/dashboard?${q}`, { credentials: "include", headers: { Accept: "application/json" } })
      .then(async (r) => {
        if (!r.ok) throw new Error(usageFetchErrorMessage(r.status))
        return (await r.json()) as BffResponse
      })
      .then((body) => {
        if (cancelled) return
        setData(body)
        onSelectUser(body.memberProfiles?.[0]?.userId ?? "")
      })
      .catch((e: Error) => {
        if (!cancelled) {
          setError(e.message)
          onSelectUser("")
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [effectiveTeamId, range.from, range.to, dashProvider, selectedApiKeyId, refresh, onSelectUser])

  const summary = data?.summary
  const mainRows: MainRow[] = React.useMemo(
    () =>
      (data?.usageSeries ?? []).map((row) => {
        const successCount = Math.max(0, row.requestCount - row.errorCount)
        return {
          label: data?.usageSeriesUnit === "HOUR" ? row.bucketLabel.replace(":00", "시") : row.bucketLabel,
          requestCount: row.requestCount,
          successRate: row.requestCount > 0 ? (100 * successCount) / row.requestCount : 0,
          errorRate: row.requestCount > 0 ? (100 * row.errorCount) / row.requestCount : 0,
        }
      }),
    [data?.usageSeries, data?.usageSeriesUnit],
  )
  const rateDomain = React.useMemo(() => stabilityRateDomain(mainRows), [mainRows])
  const rangeRequests = summary?.totalRequests ?? 0
  const rangeErrors = summary?.totalErrors ?? 0
  const rangeTokens = summary?.totalInputTokens ?? 0
  const rangeCost = toNumber(summary?.totalEstimatedCost)
  const rangeSuccess = rangeRequests > 0 ? Math.max(0, rangeRequests - rangeErrors) : 0
  const successRatePercent = rangeRequests > 0 ? (100 * rangeSuccess) / rangeRequests : 0
  const pieData = React.useMemo(() => {
    const models = data?.byModel ?? []
    const totalReq = models.reduce((s, m) => s + m.requestCount, 0)
    if (totalReq <= 0) return []
    return models
      .filter((m) => m.requestCount > 0)
      .map((m) => ({ name: truncateModelLabel(m.model), fullName: m.model, provider: m.provider, value: m.requestCount, percent: m.requestCount / totalReq }))
  }, [data?.byModel])
  const barModelData = React.useMemo(
    () =>
      [...(data?.byModel ?? [])]
        .filter((m) => m.requestCount > 0)
        .sort((a, b) => b.requestCount - a.requestCount)
        .slice(0, 15)
        .map((m) => ({ label: truncateModelLabel(m.model, 28), fullName: m.model, provider: m.provider, requests: m.requestCount })),
    [data?.byModel],
  )
  const hasMainData = (summary?.totalRequests ?? 0) > 0 || (data?.usageSeries ?? []).some((r) => r.requestCount > 0)
  const hasTeamMembership = teams.length > 0
  /** 팀 소속 없음 안내: 목록 로드 완료 후 비었고, 오류가 아닐 때만 */
  const showNoTeamBanner = !teamsLoading && !hasTeamMembership && !teamsErr
  /** 필터 비활성화는 팀 미소속일 때만 (사용량·API 키 유무와 무관) */
  const teamSelectDisabled = teamsLoading || !hasTeamMembership
  const shouldShowNoDataGuide =
    hasTeamMembership &&
    !!effectiveTeamId &&
    !loading &&
    !error &&
    (!hasMainData || apiKeyRows.length === 0)

  /** 팀 목록 오류가 아니면, 팀 목록 로딩 중이거나 소속 팀이 있을 때 동일한 차트 격자를 유지한다. */
  const showDashChartShell = !teamsErr && (teamsLoading || hasTeamMembership)
  const showComposedChart = Boolean(
    effectiveTeamId && !teamsLoading && !keysLoading && !loading && !error,
  )
  const showMainChartError = Boolean(
    showDashChartShell && effectiveTeamId && !!error && !loading,
  )
  const showMainChartSkeleton =
    showDashChartShell && !showComposedChart && !showMainChartError
  const showSecondarySkeleton = showDashChartShell && !showComposedChart
  const dashAriaBusy = showDashChartShell && (teamsLoading || keysLoading || loading)

  return (
    <div className="w-full min-h-full pb-6">
      <header className="mb-6 flex flex-col gap-4 border-b border-border pb-6 sm:flex-row sm:items-start sm:justify-between">
        <div className="space-y-2">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-2xl font-semibold tracking-tight">팀 사용량 대시보드</h1>
            <span className="rounded-full border border-border bg-muted/50 px-2 py-0.5 text-xs font-medium text-muted-foreground">팀</span>
          </div>
          <p className="text-sm text-muted-foreground">자세한 비용 내역은 &apos;지출&apos; 메뉴를 통해 확인하세요. 집계 구간은 KST 기준입니다.</p>
        </div>
        <Button type="button" variant="outline" size="sm" disabled={loading || teamsLoading} onClick={() => setRefresh((n) => n + 1)}>새로고침</Button>
      </header>
      <div className="mb-6 flex flex-row flex-wrap items-end gap-4">
        <div className="space-y-2 sm:w-52">
          <Label htmlFor="team-dash-provider">공급사</Label>
          <Select value={dashProvider} onValueChange={setDashProvider}>
            <SelectTrigger id="team-dash-provider"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value={DASHBOARD_PROVIDER_ALL}>전체</SelectItem>
              <SelectItem value="GOOGLE">Gemini (Google)</SelectItem>
              <SelectItem value="OPENAI">OpenAI</SelectItem>
              <SelectItem value="ANTHROPIC">Anthropic</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-2 sm:w-52">
          <Label>팀</Label>
          <Select
            value={selectedTeamId}
            onValueChange={setSelectedTeamId}
            disabled={teamSelectDisabled}
          >
            <SelectTrigger>
              <SelectValue
                placeholder={
                  teamsLoading ? "팀 목록 불러오는 중…" : !hasTeamMembership ? "소속 팀 없음" : "팀 선택"
                }
              />
            </SelectTrigger>
            <SelectContent>{teams.map((t) => <SelectItem key={t.id} value={t.id}>{t.name}</SelectItem>)}</SelectContent>
          </Select>
        </div>
        <div className="space-y-2 sm:w-52">
          <Label>API Key</Label>
          <Select value={selectedApiKeyId} onValueChange={setSelectedApiKeyId} disabled={keysLoading}>
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
          <Label htmlFor="team-dash-period">기간</Label>
          <Select value={periodMode} onValueChange={(v) => {
            const next = v as PeriodMode
            setPeriodMode(next)
            if (next !== "custom") {
              const pr = presetRange(next, todayKst)
              setCustomFrom(pr.from)
              setCustomTo(pr.to)
            }
          }}>
            <SelectTrigger id="team-dash-period"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="today">오늘</SelectItem>
              <SelectItem value="7d">최근 7일</SelectItem>
              <SelectItem value="30d">최근 30일</SelectItem>
              <SelectItem value="custom">사용자 지정</SelectItem>
            </SelectContent>
          </Select>
        </div>
        {periodMode === "custom" ? (
          <div className="flex flex-wrap gap-3">
            <div className="space-y-2"><Label htmlFor="team-from">시작</Label><Input id="team-from" type="date" value={customFrom} onChange={(e) => setCustomFrom(e.target.value)} /></div>
            <div className="space-y-2"><Label htmlFor="team-to">종료</Label><Input id="team-to" type="date" value={customTo} onChange={(e) => setCustomTo(e.target.value)} /></div>
          </div>
        ) : null}
      </div>

      {teamsErr ? <p className="mb-4 text-sm text-amber-700">{teamsErr}</p> : null}
      {showNoTeamBanner ? (
        <div className="mb-6 rounded-lg border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-xs text-amber-950 dark:text-amber-100" role="note">
          팀에 속하게 되면 팀 대시보드 사용이 가능해집니다.
        </div>
      ) : null}
      {showDashChartShell && showMainChartError ? (
        <p className="mb-4 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive" role="alert" aria-live="polite">
          {error}
        </p>
      ) : null}
      {showDashChartShell ? (
        <div aria-busy={dashAriaBusy ? "true" : undefined}>
          <section className="mb-8 w-full min-w-0 rounded-lg border border-border p-4 shadow-sm">
            <h2 className="mb-4 text-lg font-medium">{mainChartTitle(data?.usageSeriesUnit)}</h2>
            <div className={`w-full min-w-0 ${TEAM_DASH_MAIN_CHART_H}`}>
              {showMainChartSkeleton ? (
                <TeamDashBlockSkeleton className="h-full w-full min-h-0" />
              ) : null}
              {showMainChartError ? (
                <div
                  className="h-full min-h-0 rounded-md border border-destructive/30 bg-destructive/5"
                  role="alert"
                  aria-live="polite"
                />
              ) : null}
              {showComposedChart ? (
                <ResponsiveContainer width="100%" height="100%">
                  <ComposedChart data={mainRows.length > 0 ? mainRows : [{ label: "—", requestCount: 0, successRate: 0, errorRate: 0 }]}>
                    <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                    <XAxis dataKey="label" tick={{ fontSize: 11 }} />
                    <YAxis yAxisId="left" tick={{ fontSize: 11 }} />
                    <YAxis yAxisId="right" orientation="right" domain={rateDomain} tick={{ fontSize: 11 }} tickFormatter={(v) => `${Number(v).toFixed(1)}%`} />
                    <Tooltip
                      formatter={(value, name) => {
                        const label = String(name ?? "")
                        if (typeof value === "number" && label.includes("률")) {
                          return [`${value.toFixed(1)}%`, label]
                        }
                        return [value ?? "", label]
                      }}
                    />
                    <AnyLegend />
                    <Bar yAxisId="left" dataKey="requestCount" name="총 요청 수" fill="#a3a3a3" radius={[4, 4, 0, 0]} />
                    <Line yAxisId="right" type="monotone" dataKey="successRate" name="성공률" stroke="#10b981" strokeWidth={2} dot={false} />
                    <Line yAxisId="right" type="monotone" dataKey="errorRate" name="오류율" stroke="#f43f5e" strokeWidth={2} dot={false} />
                  </ComposedChart>
                </ResponsiveContainer>
              ) : null}
            </div>
            {showMainChartSkeleton ? <TeamDashStatsRowSkeleton /> : null}
            {showMainChartError ? <div className={TEAM_DASH_STATS_ROW_MIN} aria-hidden="true" /> : null}
            {showComposedChart && !hasMainData ? (
              <p className="mt-3 text-center text-sm text-muted-foreground">{EMPTY_MEMBER_MODEL_USAGE_MSG}</p>
            ) : null}
            {showComposedChart && hasMainData ? (
              <div className="mt-3 text-xs text-muted-foreground">
                총 요청 {formatRequestCount(rangeRequests)} · 오류 {rangeErrors.toLocaleString("en-US")}건 · 성공률 {successRatePercent.toFixed(1)}% · 총 비용 {formatUsd(rangeCost)} · 총 입력 토큰 {formatTokenCount(rangeTokens)}
              </div>
            ) : null}
            {shouldShowNoDataGuide ? (
              <div className="mt-3 rounded-lg border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-xs text-amber-950 dark:text-amber-100" role="note">
                Api key를 추가하여 AI를 호출하면 API 데이터가 쌓입니다.
              </div>
            ) : null}
            {showMainChartSkeleton && hasTeamMembership && !effectiveTeamId && !teamsLoading ? (
              <p className="mt-2 text-center text-xs text-muted-foreground">조회할 팀을 선택해 주세요.</p>
            ) : null}
          </section>
          <div className="mb-8 flex min-w-0 flex-col gap-6">
            <section className="min-w-0 rounded-lg border border-border p-4 shadow-sm">
              <h2 className="mb-4 text-lg font-medium">모델별 요청 비중</h2>
              {showSecondarySkeleton ? (
                <div className={`flex ${TEAM_DASH_PIE_WRAP_MIN} flex-col items-center gap-4 sm:flex-row`}>
                  <TeamDashBlockSkeleton className="h-[260px] w-full max-w-[260px] shrink-0 rounded-full" />
                  <div className="flex w-full max-h-[220px] flex-1 flex-col gap-2 overflow-hidden">
                    {[0, 1, 2, 3, 4].map((i) => (
                      <div key={i} className="h-3 w-full animate-pulse rounded bg-muted/45" aria-hidden="true" />
                    ))}
                  </div>
                </div>
              ) : null}
              {!showSecondarySkeleton ? (
                <>
                  <div className={`flex ${TEAM_DASH_PIE_WRAP_MIN} flex-col items-center gap-4 sm:flex-row`}>
                    <div className="h-[260px] w-full max-w-[260px] shrink-0">
                      <ResponsiveContainer width="100%" height="100%">
                        <PieChart>
                          <Pie data={pieData.length > 0 ? pieData : [{ name: "—", value: 1, fullName: "__empty__", provider: "GOOGLE", percent: 1 }]} dataKey="value" nameKey="name" cx="50%" cy="50%" innerRadius="52%" outerRadius="78%" paddingAngle={2}>
                            {(pieData.length > 0 ? pieData : [{ name: "—", fullName: "__empty__", provider: "GOOGLE", value: 1 }]).map((entry, i) => (
                              <Cell key={`cell-${entry.fullName}-${i}`} fill={pieData.length === 0 ? "var(--border)" : colorForModel(entry.fullName ?? "", entry.provider ?? "")} fillOpacity={pieData.length === 0 ? 0.35 : 1} />
                            ))}
                          </Pie>
                          <Tooltip formatter={(value) => formatRequestCount(Number(value ?? 0))} />
                        </PieChart>
                      </ResponsiveContainer>
                    </div>
                    <ul className="max-h-[220px] w-full flex-1 space-y-1 overflow-auto text-xs">
                      {pieData.length === 0 ? <li className="text-muted-foreground">—</li> : pieData.map((p) => <li key={p.fullName} className="flex justify-between gap-2"><span className="truncate text-muted-foreground">{p.name}</span><span className="tabular-nums">{(p.percent * 100).toFixed(1)}%</span></li>)}
                    </ul>
                  </div>
                  {pieData.length === 0 ? (
                    <p className="mt-3 text-center text-sm text-muted-foreground">{EMPTY_MEMBER_MODEL_USAGE_MSG}</p>
                  ) : null}
                </>
              ) : null}
            </section>
            <section className="min-w-0 rounded-lg border border-border p-4 shadow-sm">
              <h2 className="mb-4 text-lg font-medium">모델별 요청 수 (상위)</h2>
              {showSecondarySkeleton ? (
                <TeamDashBlockSkeleton className={`w-full min-w-0 ${TEAM_DASH_BAR_CHART_H}`} />
              ) : (
                <>
                  <div className={`${TEAM_DASH_BAR_CHART_H} w-full min-w-0`}>
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart
                        data={barModelData.length > 0 ? barModelData : [{ label: "—", fullName: "", provider: "", requests: 0 }]}
                        layout="vertical"
                        margin={{ left: 8, right: 8 }}
                      >
                        <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                        <XAxis type="number" tick={{ fontSize: 11 }} />
                        <YAxis type="category" dataKey="label" width={100} tick={{ fontSize: 10 }} />
                        <Tooltip />
                        <Bar dataKey="requests" name="요청 수" fill="#64748b" fillOpacity={barModelData.length === 0 ? 0.2 : 1} radius={[0, 4, 4, 0]} />
                      </BarChart>
                    </ResponsiveContainer>
                  </div>
                  {barModelData.length === 0 ? (
                    <p className="mt-3 text-center text-sm text-muted-foreground">{EMPTY_MEMBER_MODEL_USAGE_MSG}</p>
                  ) : null}
                </>
              )}
            </section>
          </div>
          {showComposedChart && data?.enrichment?.partial ? (
            <div className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-900">프로필 일부 결합 실패: {(data.enrichment.warnings ?? []).join(", ")}</div>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}
