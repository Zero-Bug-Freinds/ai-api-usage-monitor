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
import { buildUsageQuery, fetchUsageJson } from "@/lib/usage/fetch-usage"
import { formatOccurredAtKst } from "@/lib/usage/format-occurred-at-kst"
import { formatRequestCount, formatTokenCount, formatUsd, toNumber } from "@/lib/usage/format"
import type {
  DailyUsagePoint,
  HourlyUsagePoint,
  ModelUsageAggregate,
  MonthlyUsagePoint,
  UsageCostIntradayKpiResponse,
  UsageProviderFilter,
  UsageSummaryResponse,
} from "@/lib/usage/types"
import { addKstDays, formatKstIsoDate } from "@/lib/usage/kst-dates"

/** 공급사별 기본 색 — 모든 차트에서 동일 키에 동일 색 */
const PROVIDER_COLOR: Record<string, string> = {
  GOOGLE: "#16a34a",
  OPENAI: "#0a0a0a",
  ANTHROPIC: "#c2410c",
}

const PROVIDER_LABEL: Record<string, string> = {
  GOOGLE: "Gemini (Google)",
  OPENAI: "OpenAI",
  ANTHROPIC: "Anthropic",
}

const MONTHLY_LOOKBACK_DAYS = 365

const DASHBOARD_PROVIDER_ALL = "__ALL__"

type PeriodMode = "today" | "7d" | "30d" | "custom"

function tooltipNumericValue(value: unknown): number {
  if (typeof value === "number") return value
  if (typeof value === "string") return toNumber(value)
  return 0
}

function truncateModelLabel(model: string, max = 36) {
  if (model.length <= max) return model
  return `${model.slice(0, max - 1)}…`
}

function providerParam(v: string): UsageProviderFilter | undefined {
  if (v === DASHBOARD_PROVIDER_ALL) return undefined
  return v as UsageProviderFilter
}

function rangeForPeriod(mode: PeriodMode, todayKst: string, customFrom: string, customTo: string) {
  switch (mode) {
    case "today":
      return { from: todayKst, to: todayKst }
    case "7d":
      return { from: addKstDays(todayKst, -6), to: todayKst }
    case "30d":
      return { from: addKstDays(todayKst, -29), to: todayKst }
    case "custom":
      return { from: customFrom || todayKst, to: customTo || todayKst }
    default:
      return { from: todayKst, to: todayKst }
  }
}

/** KST 날짜 문자열 구간의 포함 일수 (시작·종료 당일 포함). */
function kstDaysInclusive(fromIso: string, toIso: string): number {
  const a = Date.parse(`${fromIso}T12:00:00+09:00`)
  const b = Date.parse(`${toIso}T12:00:00+09:00`)
  if (Number.isNaN(a) || Number.isNaN(b) || b < a) return 1
  return Math.floor((b - a) / 86_400_000) + 1
}

/** 동일 길이의 직전 기간 [prevFrom, prevTo] (포함 일수 = 선택 구간과 동일). */
function previousPeriodBounds(fromIso: string, toIso: string): { prevFrom: string; prevTo: string } {
  const n = kstDaysInclusive(fromIso, toIso)
  const prevTo = addKstDays(fromIso, -1)
  const prevFrom = addKstDays(fromIso, -n)
  return { prevFrom, prevTo }
}

function kpiPeriodPrefix(mode: PeriodMode, fromIso: string, toIso: string): string {
  switch (mode) {
    case "today":
      return "오늘의"
    case "7d":
      return "7일간"
    case "30d":
      return "30일간"
    case "custom": {
      const n = kstDaysInclusive(fromIso, toIso)
      return `${n}일간`
    }
    default:
      return "선택 기간"
  }
}

function costCompareLabel(mode: PeriodMode, fromIso: string, toIso: string): string {
  switch (mode) {
    case "today":
      return "전일 동기 대비"
    case "7d":
      return "이전 7일 대비"
    case "30d":
      return "이전 30일 대비"
    case "custom": {
      const n = kstDaysInclusive(fromIso, toIso)
      return `이전 ${n}일 대비`
    }
    default:
      return "이전 동일 기간 대비"
  }
}

/**
 * 이전 기간 대비 비용 증감률(%). 기준 비용 ≤ 0이면 0%로 표시해 '비교 불가' 문구를 쓰지 않음.
 */
function percentCostChangeVsPrevious(current: number, previous: number): number {
  if (previous > 0) {
    return ((current - previous) / previous) * 100
  }
  return 0
}

function hashToUint(str: string): number {
  let h = 2166136261
  for (let i = 0; i < str.length; i++) {
    h ^= str.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return h >>> 0
}

/**
 * 동일 모델·공급사 조합은 항상 같은 색(공급사 팔레트 기반 변형).
 */
function colorForModel(model: string, provider: string): string {
  const variants: Record<string, string[]> = {
    GOOGLE: ["#14532d", "#15803d", "#16a34a", "#22c55e", "#4ade80", "#86efac"],
    OPENAI: ["#0a0a0a", "#171717", "#262626", "#404040", "#525252", "#737373"],
    ANTHROPIC: ["#7c2d12", "#9a3412", "#c2410c", "#ea580c", "#fb923c", "#fdba74"],
  }
  const list = variants[provider] ?? ["#525252", "#737373", "#a3a3a3", "#d4d4d4"]
  const idx = hashToUint(`${provider}::${model}`) % list.length
  return list[idx] ?? "#737373"
}

function labelForProviderCode(code: string): string {
  return PROVIDER_LABEL[code] ?? code
}

function isAbortError(e: unknown): boolean {
  return (
    (typeof DOMException !== "undefined" && e instanceof DOMException && e.name === "AbortError") ||
    (e instanceof Error && e.name === "AbortError")
  )
}

const H_BAR_MARGIN = { left: 8, right: 16 }
/** 출력 토큰 차트: 왼쪽 차트와 모델 라벨 중복을 줄이기 위해 Y축만 좁힘 */
const H_BAR_MARGIN_NO_Y_LABEL = { left: 4, right: 16 }
const H_BAR_HEIGHT = 320

export function UsageDashboard() {
  const [periodMode, setPeriodMode] = React.useState<PeriodMode>("today")
  const [customFrom, setCustomFrom] = React.useState("")
  const [customTo, setCustomTo] = React.useState("")
  const [dashProvider, setDashProvider] = React.useState<string>("GOOGLE")

  const [mainLoading, setMainLoading] = React.useState(true)
  const [mainError, setMainError] = React.useState<string | null>(null)
  const [mainRefresh, setMainRefresh] = React.useState(0)

  const [costKpi, setCostKpi] = React.useState<UsageCostIntradayKpiResponse | null>(null)
  const [hourly, setHourly] = React.useState<HourlyUsagePoint[] | null>(null)
  const [kpiSummary, setKpiSummary] = React.useState<UsageSummaryResponse | null>(null)
  const [prevPeriodSummary, setPrevPeriodSummary] = React.useState<UsageSummaryResponse | null>(null)
  const [daily, setDaily] = React.useState<DailyUsagePoint[]>([])
  const [monthly, setMonthly] = React.useState<MonthlyUsagePoint[]>([])
  const [byModel, setByModel] = React.useState<ModelUsageAggregate[]>([])

  /** API·KPI 라벨에 쓰는 구간(마지막으로 로드한 기준). effect 안에서만 갱신해 요청 간 날짜 불일치를 막는다. */
  const [loadedRange, setLoadedRange] = React.useState<{ from: string; to: string } | null>(null)

  const [clientReady, setClientReady] = React.useState(false)
  React.useLayoutEffect(() => {
    setClientReady(true)
  }, [])

  const kstTodayFallback = formatKstIsoDate()
  const rangeFrom = loadedRange?.from ?? kstTodayFallback
  const rangeTo = loadedRange?.to ?? kstTodayFallback

  React.useEffect(() => {
    if (!clientReady) return
    const ac = new AbortController()
    const { signal } = ac
    let cancelled = false
    setMainLoading(true)
    setMainError(null)
    ;(async () => {
      try {
        const t = formatKstIsoDate()
        const { from: rf, to: rt } = rangeForPeriod(periodMode, t, customFrom, customTo)
        const { prevFrom: pf, prevTo: pt } = previousPeriodBounds(rf, rt)

        const fy = addKstDays(t, -MONTHLY_LOOKBACK_DAYS)
        const pq = buildUsageQuery({ provider: providerParam(dashProvider) })
        const qRange = buildUsageQuery({
          from: rf,
          to: rt,
          provider: providerParam(dashProvider),
        })
        const qPrev = buildUsageQuery({
          from: pf,
          to: pt,
          provider: providerParam(dashProvider),
        })

        const opt = { signal }
        const summaryP = fetchUsageJson<UsageSummaryResponse>(`dashboard/summary${qRange}`, opt)
        const summaryPrevP = fetchUsageJson<UsageSummaryResponse>(`dashboard/summary${qPrev}`, opt)
        const dailyP = fetchUsageJson<DailyUsagePoint[]>(`dashboard/series/daily${qRange}`, opt)
        const monthlyP = fetchUsageJson<MonthlyUsagePoint[]>(
          `dashboard/series/monthly${buildUsageQuery({ from: fy, to: rt, provider: providerParam(dashProvider) })}`,
          opt
        )
        const byModelP = fetchUsageJson<ModelUsageAggregate[]>(`dashboard/by-model${qRange}`, opt)

        if (periodMode === "today") {
          const [kpi, h, cur, prev, d, m, bm] = await Promise.all([
            fetchUsageJson<UsageCostIntradayKpiResponse>(`dashboard/kpi/cost-intraday${pq}`, opt),
            fetchUsageJson<HourlyUsagePoint[]>(
              `dashboard/series/hourly${buildUsageQuery({ date: t, provider: providerParam(dashProvider) })}`,
              opt
            ),
            summaryP,
            summaryPrevP,
            dailyP,
            monthlyP,
            byModelP,
          ])
          if (!cancelled) {
            setLoadedRange({ from: rf, to: rt })
            setCostKpi(kpi)
            setHourly(Array.isArray(h) ? h : [])
            setKpiSummary(cur)
            setPrevPeriodSummary(prev)
            setDaily(Array.isArray(d) ? d : [])
            setMonthly(Array.isArray(m) ? m : [])
            setByModel(Array.isArray(bm) ? bm : [])
          }
        } else {
          const [cur, prev, d, m, bm] = await Promise.all([
            summaryP,
            summaryPrevP,
            dailyP,
            monthlyP,
            byModelP,
          ])
          if (!cancelled) {
            setLoadedRange({ from: rf, to: rt })
            setCostKpi(null)
            setHourly(null)
            setKpiSummary(cur)
            setPrevPeriodSummary(prev)
            setDaily(Array.isArray(d) ? d : [])
            setMonthly(Array.isArray(m) ? m : [])
            setByModel(Array.isArray(bm) ? bm : [])
          }
        }
      } catch (e) {
        if (isAbortError(e)) return
        if (!cancelled) {
          setMainError(e instanceof Error ? e.message : "데이터를 불러오지 못했습니다")
        }
      } finally {
        if (!cancelled) setMainLoading(false)
      }
    })()
    return () => {
      cancelled = true
      ac.abort()
    }
  }, [clientReady, mainRefresh, periodMode, customFrom, customTo, dashProvider])

  const dailyChart = React.useMemo(
    () =>
      daily.map((row) => ({
        date: row.date,
        requestCount: row.requestCount,
        cost: toNumber(row.estimatedCost),
      })),
    [daily]
  )

  const hourlyChart = React.useMemo(
    () =>
      (hourly ?? []).map((row) => ({
        hour: row.hour,
        label: `${row.hour}시`,
        requestCount: row.requestCount,
        cost: toNumber(row.estimatedCostUsd),
      })),
    [hourly]
  )

  const monthlyChart = React.useMemo(
    () =>
      monthly.map((row) => ({
        yearMonth: row.yearMonth,
        requestCount: row.requestCount,
      })),
    [monthly]
  )

  const pieData = React.useMemo(() => {
    const totalReq = byModel.reduce((s, m) => s + m.requestCount, 0)
    if (totalReq <= 0) return []
    return byModel
      .filter((m) => m.requestCount > 0)
      .map((m) => ({
        name: truncateModelLabel(m.model),
        fullName: m.model,
        provider: m.provider,
        value: m.requestCount,
      }))
  }, [byModel])

  const providerPieData = React.useMemo(() => {
    const totalReq = byModel.reduce((s, m) => s + m.requestCount, 0)
    if (totalReq <= 0) return []
    const acc = new Map<string, number>()
    for (const m of byModel) {
      if (m.requestCount <= 0) continue
      acc.set(m.provider, (acc.get(m.provider) ?? 0) + m.requestCount)
    }
    return [...acc.entries()].map(([provider, value]) => ({
      name: labelForProviderCode(provider),
      provider,
      value,
    }))
  }, [byModel])

  const modelBarRows = React.useMemo(() => {
    return [...byModel]
      .sort((a, b) => b.requestCount - a.requestCount)
      .map((m) => ({
        label: truncateModelLabel(m.model),
        model: m.model,
        provider: m.provider,
        requests: m.requestCount,
        tokens: m.inputTokens,
        outTokens: m.outputTokens,
      }))
  }, [byModel])

  const hasMainData =
    (kpiSummary && kpiSummary.totalRequests > 0) ||
    daily.length > 0 ||
    monthly.length > 0 ||
    byModel.some((m) => m.requestCount > 0) ||
    (hourly && hourly.some((h) => h.requestCount > 0 || toNumber(h.estimatedCostUsd) > 0))

  const periodPrefix = kpiPeriodPrefix(periodMode, rangeFrom, rangeTo)
  const compareCostLabel = costCompareLabel(periodMode, rangeFrom, rangeTo)

  const rangeCost = kpiSummary ? toNumber(kpiSummary.totalEstimatedCost) : 0
  const rangeRequests = kpiSummary?.totalRequests ?? 0
  const rangeTokens = kpiSummary?.totalInputTokens ?? 0
  const rangeErrors = kpiSummary?.totalErrors ?? 0
  const rangeSuccess =
    rangeRequests > 0 ? Math.max(0, rangeRequests - rangeErrors) : 0
  const successRatePercent =
    rangeRequests > 0 ? (100 * rangeSuccess) / rangeRequests : 0

  const prevCostForCompare = prevPeriodSummary ? toNumber(prevPeriodSummary.totalEstimatedCost) : 0

  const costChangePercent = React.useMemo(() => {
    if (periodMode === "today") {
      if (costKpi?.changeRatePercent == null) return 0
      return toNumber(costKpi.changeRatePercent)
    }
    return percentCostChangeVsPrevious(rangeCost, prevCostForCompare)
  }, [periodMode, costKpi, rangeCost, prevCostForCompare])

  const monthlyHasActivity = monthlyChart.some((r) => r.requestCount > 0)

  const mainChartTitle =
    periodMode === "today" ? "시간별 요청·비용 (오늘 KST)" : "일별 요청·비용 (선택 기간)"

  const errorSubStyle =
    rangeErrors >= 1 ? "text-red-500" : "text-foreground"

  return (
    <div className="w-full min-h-full pb-8">
      <header className="mb-6 flex flex-col gap-4 border-b border-border pb-6 sm:flex-row sm:items-start sm:justify-between">
        <div className="space-y-2">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-2xl font-semibold tracking-tight">API 사용량</h1>
            <span className="rounded-full border border-border bg-muted/50 px-2 py-0.5 text-xs font-medium text-muted-foreground">
              개인
            </span>
          </div>
          <p className="text-sm text-muted-foreground">
            집계·차트 구간은 한국 표준시(KST) 기준입니다. 상세 로그는 사이드바의 「상세 로그」에서 확인할 수
            있습니다.
          </p>
        </div>
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="shrink-0"
          disabled={mainLoading}
          onClick={() => setMainRefresh((n) => n + 1)}
        >
          새로고침
        </Button>
      </header>

      <div className="mb-6 flex flex-col gap-4 lg:flex-row lg:flex-wrap lg:items-end">
        <div className="space-y-2 sm:w-44">
          <Label htmlFor="dash-period">기간</Label>
          <Select
            value={periodMode}
            onValueChange={(v) => setPeriodMode(v as PeriodMode)}
          >
            <SelectTrigger id="dash-period">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="today">오늘</SelectItem>
              <SelectItem value="7d">최근 7일</SelectItem>
              <SelectItem value="30d">최근 30일</SelectItem>
              <SelectItem value="custom">기간 지정</SelectItem>
            </SelectContent>
          </Select>
        </div>

        {periodMode === "custom" ? (
          <div className="flex flex-wrap gap-3">
            <div className="space-y-2">
              <Label htmlFor="custom-from">시작</Label>
              <Input
                id="custom-from"
                type="date"
                value={customFrom}
                onChange={(e) => setCustomFrom(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="custom-to">종료</Label>
              <Input
                id="custom-to"
                type="date"
                value={customTo}
                onChange={(e) => setCustomTo(e.target.value)}
              />
            </div>
          </div>
        ) : null}

        <div className="space-y-2 sm:w-52">
          <Label htmlFor="dash-provider">공급사</Label>
          <Select value={dashProvider} onValueChange={setDashProvider}>
            <SelectTrigger id="dash-provider">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={DASHBOARD_PROVIDER_ALL}>전체</SelectItem>
              <SelectItem value="GOOGLE">Gemini (Google)</SelectItem>
              <SelectItem value="OPENAI">OpenAI</SelectItem>
              <SelectItem value="ANTHROPIC">Anthropic</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>

      {mainError ? (
        <p className="mb-6 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {mainError}
        </p>
      ) : null}

      {!clientReady || mainLoading ? (
        <p className="mb-8 text-sm text-muted-foreground">불러오는 중…</p>
      ) : (
        <>
          <section className="mb-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <div className="rounded-lg border border-border bg-card p-4 shadow-sm">
              <p className="text-xs font-medium text-muted-foreground">
                {periodPrefix} 총 비용 (USD)
              </p>
              <p className="mt-1 text-2xl font-semibold tabular-nums">{formatUsd(rangeCost)}</p>
              <p className="mt-2 text-xs text-muted-foreground">
                {compareCostLabel}{" "}
                <span className="font-medium text-foreground tabular-nums">
                  {costChangePercent >= 0 ? "+" : ""}
                  {costChangePercent.toFixed(1)}%
                </span>
              </p>
              {periodMode === "today" && costKpi ? (
                <p className="mt-1 text-[11px] text-muted-foreground leading-snug">
                  기준 {formatOccurredAtKst(costKpi.comparisonWindowEnd)} (KST)
                </p>
              ) : null}
            </div>
            <div className="rounded-lg border border-border bg-card p-4 shadow-sm">
              <p className="text-xs font-medium text-muted-foreground">{periodPrefix} 총 요청 수</p>
              <p className="mt-1 text-2xl font-semibold tabular-nums">
                {formatRequestCount(rangeRequests)}
              </p>
            </div>
            <div className="rounded-lg border border-border bg-card p-4 shadow-sm">
              <p className="text-xs font-medium text-muted-foreground">{periodPrefix} 총 입력 토큰</p>
              <p className="mt-1 text-2xl font-semibold tabular-nums">{formatTokenCount(rangeTokens)}</p>
            </div>
            <div className="rounded-lg border border-border bg-card p-4 shadow-sm">
              <p className="text-xs font-medium text-muted-foreground">{periodPrefix} 성공률</p>
              <p className="mt-1 text-2xl font-semibold tabular-nums">
                {successRatePercent.toFixed(1)}%
              </p>
              <p className={`mt-2 text-xs tabular-nums ${errorSubStyle}`}>
                오류 {rangeErrors.toLocaleString("en-US")}건 / 총 {rangeRequests.toLocaleString("en-US")}건
              </p>
            </div>
          </section>

          {!hasMainData ? (
            <p className="mb-10 text-center text-sm text-muted-foreground">
              선택한 기간·공급사에 대한 사용 데이터가 없습니다
            </p>
          ) : null}

          <section className="mb-10 rounded-lg border border-border p-4 shadow-sm">
            <h2 className="mb-4 text-lg font-medium">{mainChartTitle}</h2>
            {periodMode === "today" && hourlyChart.length > 0 ? (
              <div className="h-[380px] min-h-[380px] w-full min-w-0">
                <ResponsiveContainer width="100%" height="100%">
                  <ComposedChart data={hourlyChart}>
                    <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                    <XAxis dataKey="label" tick={{ fontSize: 11 }} />
                    <YAxis yAxisId="left" tick={{ fontSize: 11 }} />
                    <YAxis yAxisId="right" orientation="right" tick={{ fontSize: 11 }} />
                    <Tooltip
                      formatter={(value, name) =>
                        name === "비용 (USD)"
                          ? formatUsd(tooltipNumericValue(value))
                          : tooltipNumericValue(value)
                      }
                    />
                    <Legend />
                    <Bar
                      yAxisId="left"
                      dataKey="requestCount"
                      name="요청 수"
                      fill="#a3a3a3"
                      radius={[4, 4, 0, 0]}
                    />
                    <Line
                      yAxisId="right"
                      type="monotone"
                      dataKey="cost"
                      name="비용 (USD)"
                      stroke="#0a0a0a"
                      strokeWidth={2}
                      dot={false}
                    />
                  </ComposedChart>
                </ResponsiveContainer>
              </div>
            ) : periodMode !== "today" && dailyChart.length > 0 ? (
              <div className="h-[380px] min-h-[380px] w-full min-w-0">
                <ResponsiveContainer width="100%" height="100%">
                  <ComposedChart data={dailyChart}>
                    <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                    <XAxis dataKey="date" tick={{ fontSize: 11 }} />
                    <YAxis yAxisId="left" tick={{ fontSize: 11 }} />
                    <YAxis yAxisId="right" orientation="right" tick={{ fontSize: 11 }} />
                    <Tooltip
                      formatter={(value, name) =>
                        name === "비용 (USD)"
                          ? formatUsd(tooltipNumericValue(value))
                          : tooltipNumericValue(value)
                      }
                    />
                    <Legend />
                    <Bar
                      yAxisId="left"
                      dataKey="requestCount"
                      name="요청 수"
                      fill="#a3a3a3"
                      radius={[4, 4, 0, 0]}
                    />
                    <Line
                      yAxisId="right"
                      type="monotone"
                      dataKey="cost"
                      name="비용 (USD)"
                      stroke="#0a0a0a"
                      strokeWidth={2}
                      dot={false}
                    />
                  </ComposedChart>
                </ResponsiveContainer>
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">표시할 시계열 데이터가 없습니다</p>
            )}
          </section>

          <div className="mb-10 grid gap-6 lg:grid-cols-2 lg:gap-8">
            <section className="rounded-lg border border-border p-4 shadow-sm">
              <h2 className="mb-4 text-lg font-medium">모델별 요청 비중</h2>
              {pieData.length === 0 ? (
                <p className="text-sm text-muted-foreground">집계 데이터 없음</p>
              ) : (
                <div className="h-[320px] min-h-[320px] w-full min-w-0">
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie
                        data={pieData}
                        dataKey="value"
                        nameKey="name"
                        cx="50%"
                        cy="50%"
                        innerRadius="52%"
                        outerRadius="80%"
                        paddingAngle={2}
                        label={({ name, percent }) => `${name} (${((percent ?? 0) * 100).toFixed(0)}%)`}
                      >
                        {pieData.map((entry, i) => (
                          <Cell
                            key={`m-${entry.fullName}-${i}`}
                            fill={colorForModel(entry.fullName, entry.provider)}
                          />
                        ))}
                      </Pie>
                      <Tooltip formatter={(value) => formatRequestCount(tooltipNumericValue(value))} />
                      <Legend />
                    </PieChart>
                  </ResponsiveContainer>
                </div>
              )}
            </section>

            <section className="rounded-lg border border-border p-4 shadow-sm">
              <h2 className="mb-4 text-lg font-medium">공급사별 요청 비중</h2>
              {providerPieData.length === 0 ? (
                <p className="text-sm text-muted-foreground">집계 데이터 없음</p>
              ) : (
                <div className="h-[320px] min-h-[320px] w-full min-w-0">
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie
                        data={providerPieData}
                        dataKey="value"
                        nameKey="name"
                        cx="50%"
                        cy="50%"
                        innerRadius="52%"
                        outerRadius="80%"
                        paddingAngle={2}
                        label={({ name, percent }) => `${name} (${((percent ?? 0) * 100).toFixed(0)}%)`}
                      >
                        {providerPieData.map((entry, i) => (
                          <Cell
                            key={`p-${entry.provider}-${i}`}
                            fill={PROVIDER_COLOR[entry.provider] ?? "#737373"}
                          />
                        ))}
                      </Pie>
                      <Tooltip formatter={(value) => formatRequestCount(tooltipNumericValue(value))} />
                      <Legend />
                    </PieChart>
                  </ResponsiveContainer>
                </div>
              )}
            </section>

            <section className="rounded-lg border border-border p-4 shadow-sm lg:col-span-2">
              <h2 className="mb-4 text-lg font-medium">모델별 요청 수 (가로)</h2>
              {modelBarRows.length === 0 ? (
                <p className="text-sm text-muted-foreground">집계 데이터 없음</p>
              ) : (
                <div className="h-[320px] min-h-[320px] w-full max-w-full min-w-0">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart layout="vertical" data={modelBarRows} margin={H_BAR_MARGIN}>
                      <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                      <XAxis type="number" />
                      <YAxis type="category" dataKey="label" width={128} tick={{ fontSize: 11 }} />
                      <Tooltip />
                      <Bar dataKey="requests" name="요청 수" radius={[0, 4, 4, 0]}>
                        {modelBarRows.map((row) => (
                          <Cell key={`req-${row.model}`} fill={colorForModel(row.model, row.provider)} />
                        ))}
                      </Bar>
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              )}
            </section>
          </div>

          <div className="mb-10 grid gap-6 lg:grid-cols-2 lg:gap-8">
            {modelBarRows.length === 0 ? (
              <section className="rounded-lg border border-border bg-card p-4 shadow-sm lg:col-span-2">
                <h2 className="mb-4 text-lg font-medium">모델별 입력·출력 토큰 (가로)</h2>
                <p className="text-sm text-muted-foreground">집계 데이터 없음</p>
              </section>
            ) : (
              <>
                <section className="rounded-lg border border-border bg-card p-4 shadow-sm min-w-0">
                  <h2 className="mb-4 text-lg font-medium">모델별 입력 토큰 (가로)</h2>
                  <div
                    className="w-full max-w-full min-w-0"
                    style={{ height: H_BAR_HEIGHT, minHeight: H_BAR_HEIGHT }}
                  >
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart layout="vertical" data={modelBarRows} margin={H_BAR_MARGIN}>
                        <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                        <XAxis type="number" tick={{ fontSize: 11 }} />
                        <YAxis type="category" dataKey="label" width={128} tick={{ fontSize: 11 }} />
                        <Tooltip
                          formatter={(v) => formatTokenCount(tooltipNumericValue(v))}
                          labelFormatter={(label) => String(label)}
                        />
                        <Bar dataKey="tokens" name="입력 토큰" radius={[0, 4, 4, 0]}>
                          {modelBarRows.map((row) => (
                            <Cell key={`in-${row.model}`} fill={colorForModel(row.model, row.provider)} />
                          ))}
                        </Bar>
                      </BarChart>
                    </ResponsiveContainer>
                  </div>
                </section>
                <section className="rounded-lg border border-border bg-card p-4 shadow-sm min-w-0">
                  <h2 className="mb-4 text-lg font-medium">모델별 출력 토큰 (가로)</h2>
                  <div
                    className="w-full max-w-full min-w-0"
                    style={{ height: H_BAR_HEIGHT, minHeight: H_BAR_HEIGHT }}
                  >
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart layout="vertical" data={modelBarRows} margin={H_BAR_MARGIN_NO_Y_LABEL}>
                        <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                        <XAxis type="number" tick={{ fontSize: 11 }} />
                        <YAxis type="category" dataKey="label" hide />
                        <Tooltip
                          formatter={(v) => formatTokenCount(tooltipNumericValue(v))}
                          labelFormatter={(label) => String(label)}
                        />
                        <Bar dataKey="outTokens" name="출력 토큰" radius={[0, 4, 4, 0]}>
                          {modelBarRows.map((row) => (
                            <Cell key={`out-${row.model}`} fill={colorForModel(row.model, row.provider)} />
                          ))}
                        </Bar>
                      </BarChart>
                    </ResponsiveContainer>
                  </div>
                </section>
              </>
            )}
          </div>

          <section className="mb-10 rounded-lg border border-border p-4 shadow-sm">
            <h2 className="mb-4 text-lg font-medium">월별 요청 수 (누적 추이)</h2>
            {monthlyChart.length === 0 || !monthlyHasActivity ? (
              <p className="text-sm text-muted-foreground">집계 데이터 없음</p>
            ) : (
              <div className="h-[360px] min-h-[360px] w-full min-w-0">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={monthlyChart}>
                    <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                    <XAxis dataKey="yearMonth" tick={{ fontSize: 11 }} />
                    <YAxis tick={{ fontSize: 11 }} />
                    <Tooltip formatter={(value) => formatRequestCount(tooltipNumericValue(value))} />
                    <Legend />
                    <Bar
                      dataKey="requestCount"
                      name="요청 수"
                      fill="#16a34a"
                      radius={[4, 4, 0, 0]}
                    />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}
          </section>
        </>
      )}
    </div>
  )
}
