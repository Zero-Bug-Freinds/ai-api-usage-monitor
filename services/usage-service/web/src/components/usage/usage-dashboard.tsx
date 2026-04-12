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
import { formatRequestCount, formatTokenCount, formatUsd, toNumber } from "@/lib/usage/format"
import type {
  DailyUsagePoint,
  HourlyUsagePoint,
  ModelUsageAggregate,
  MonthlyUsagePoint,
  PeriodMode,
  UsageCostIntradayKpiResponse,
  UsageProviderFilter,
  UsageSummaryResponse,
} from "@/lib/usage/types"
import {
  loadDashboardFilters,
  saveDashboardFilters,
  type DashboardFilterSnapshot,
} from "@/lib/usage/dashboard-filter-storage"
import { addKstDays, formatKstIsoDate } from "@/lib/usage/kst-dates"

const CHART_COLORS = [
  "#0a0a0a",
  "#404040",
  "#737373",
  "#a3a3a3",
  "#d4d4d4",
]

const MONTHLY_LOOKBACK_DAYS = 365
const DASHBOARD_PROVIDER_ALL = "__all__"
/** 초기 진입 시 기본 공급사: Gemini (저장·API 값은 GOOGLE) */
const DASHBOARD_DEFAULT_PROVIDER: UsageProviderFilter = "GOOGLE"

function emptyDailySeriesForRange(
  fromIso: string,
  toIso: string
): { date: string; requestCount: number; cost: number }[] {
  const out: { date: string; requestCount: number; cost: number }[] = []
  let d = fromIso
  for (let i = 0; i < 400 && d <= toIso; i++) {
    out.push({ date: d, requestCount: 0, cost: 0 })
    if (d === toIso) break
    d = addKstDays(d, 1)
  }
  return out
}

const EMPTY_HOURLY_CHART = Array.from({ length: 24 }, (_, h) => ({
  label: `${h}시`,
  requestCount: 0,
  cost: 0,
}))

function tooltipNumericValue(value: unknown): number {
  if (typeof value === "number") return value
  if (typeof value === "string") return toNumber(value)
  return 0
}

function truncateModelLabel(model: string, max = 36) {
  if (model.length <= max) return model
  return `${model.slice(0, max - 1)}…`
}

function computeRange(
  todayIso: string,
  mode: PeriodMode,
  customFrom: string,
  customTo: string
): { from: string; to: string } {
  switch (mode) {
    case "today":
      return { from: todayIso, to: todayIso }
    case "7d":
      return { from: addKstDays(todayIso, -6), to: todayIso }
    case "30d":
      return { from: addKstDays(todayIso, -29), to: todayIso }
    case "custom":
      if (!customFrom || !customTo) return { from: todayIso, to: todayIso }
      return { from: customFrom, to: customTo }
    default:
      return { from: todayIso, to: todayIso }
  }
}

function providerQueryParam(v: string): UsageProviderFilter | undefined {
  return v !== DASHBOARD_PROVIDER_ALL ? (v as UsageProviderFilter) : undefined
}

export function UsageDashboard() {
  const [filtersHydrated, setFiltersHydrated] = React.useState(false)
  const [periodMode, setPeriodMode] = React.useState<PeriodMode>("today")
  const [dashboardProvider, setDashboardProvider] = React.useState<string>(DASHBOARD_DEFAULT_PROVIDER)
  const [customFrom, setCustomFrom] = React.useState(() => addKstDays(formatKstIsoDate(), -7))
  const [customTo, setCustomTo] = React.useState(() => formatKstIsoDate())

  const [summary, setSummary] = React.useState<UsageSummaryResponse | null>(null)
  const [kpi, setKpi] = React.useState<UsageCostIntradayKpiResponse | null>(null)
  const [hourly, setHourly] = React.useState<HourlyUsagePoint[]>([])
  const [dailyMain, setDailyMain] = React.useState<DailyUsagePoint[]>([])
  const [dailyAux30, setDailyAux30] = React.useState<DailyUsagePoint[]>([])
  const [monthly, setMonthly] = React.useState<MonthlyUsagePoint[]>([])
  const [byModel, setByModel] = React.useState<ModelUsageAggregate[]>([])
  const [mainLoading, setMainLoading] = React.useState(true)
  const [mainError, setMainError] = React.useState<string | null>(null)

  const [mainRefresh, setMainRefresh] = React.useState(0)

  React.useEffect(() => {
    const saved = loadDashboardFilters()
    if (saved) {
      setDashboardProvider(saved.provider)
      setPeriodMode(saved.periodMode)
      setCustomFrom(saved.customFrom)
      setCustomTo(saved.customTo)
    }
    setFiltersHydrated(true)
  }, [])

  React.useEffect(() => {
    if (!filtersHydrated) return

    const snapshot: DashboardFilterSnapshot = {
      provider: dashboardProvider,
      periodMode,
      customFrom,
      customTo,
    }
    saveDashboardFilters(snapshot)
  }, [filtersHydrated, dashboardProvider, periodMode, customFrom, customTo])

  React.useEffect(() => {
    if (!filtersHydrated) return

    let cancelled = false
    setMainLoading(true)
    setMainError(null)
    ;(async () => {
      try {
        const today = formatKstIsoDate()
        const { from, to } = computeRange(today, periodMode, customFrom, customTo)
        const prov = providerQueryParam(dashboardProvider)
        const qBase = buildUsageQuery({ from, to, provider: prov })
        const fy = addKstDays(today, -MONTHLY_LOOKBACK_DAYS)
        const f30 = addKstDays(today, -29)

        const summaryP = fetchUsageJson<UsageSummaryResponse>(`dashboard/summary${qBase}`)
        const dailyMainP = fetchUsageJson<DailyUsagePoint[]>(`dashboard/series/daily${qBase}`)
        const monthlyP = fetchUsageJson<MonthlyUsagePoint[]>(
          `dashboard/series/monthly${buildUsageQuery({ from: fy, to: today, provider: prov })}`
        )
        const byModelP = fetchUsageJson<ModelUsageAggregate[]>(
          `dashboard/by-model${buildUsageQuery({ from, to, provider: prov })}`
        )

        let kpiP: Promise<UsageCostIntradayKpiResponse | null> = Promise.resolve(null)
        let hourlyP: Promise<HourlyUsagePoint[]> = Promise.resolve([])
        let dailyAuxP: Promise<DailyUsagePoint[]> = Promise.resolve([])

        if (periodMode === "today") {
          kpiP = fetchUsageJson<UsageCostIntradayKpiResponse>(
            `dashboard/kpi/cost-intraday${buildUsageQuery({ provider: prov })}`
          )
          hourlyP = fetchUsageJson<HourlyUsagePoint[]>(
            `dashboard/series/hourly${buildUsageQuery({ date: today, provider: prov })}`
          )
          dailyAuxP = fetchUsageJson<DailyUsagePoint[]>(
            `dashboard/series/daily${buildUsageQuery({ from: f30, to: today, provider: prov })}`
          )
        }

        const [sum, dMain, m, bm, kp, hr, dAux] = await Promise.all([
          summaryP,
          dailyMainP,
          monthlyP,
          byModelP,
          kpiP,
          hourlyP,
          dailyAuxP,
        ])

        if (!cancelled) {
          setSummary(sum)
          setDailyMain(Array.isArray(dMain) ? dMain : [])
          setMonthly(Array.isArray(m) ? m : [])
          setByModel(Array.isArray(bm) ? bm : [])
          setKpi(kp)
          setHourly(Array.isArray(hr) ? hr : [])
          setDailyAux30(Array.isArray(dAux) ? dAux : [])
        }
      } catch (e) {
        if (!cancelled) {
          setMainError(e instanceof Error ? e.message : "데이터를 불러오지 못했습니다")
        }
      } finally {
        if (!cancelled) setMainLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [filtersHydrated, mainRefresh, periodMode, customFrom, customTo, dashboardProvider])

  const hourlyChart = React.useMemo(
    () =>
      hourly.map((row) => ({
        label: `${row.hour}시`,
        requestCount: row.requestCount,
        cost: toNumber(row.estimatedCost),
      })),
    [hourly]
  )

  const rangeForDisplay = React.useMemo(
    () => computeRange(formatKstIsoDate(), periodMode, customFrom, customTo),
    [periodMode, customFrom, customTo]
  )

  const displayHourlyChart = React.useMemo(
    () => (hourlyChart.length > 0 ? hourlyChart : EMPTY_HOURLY_CHART),
    [hourlyChart]
  )

  const dailyChart = React.useMemo(
    () =>
      dailyMain.map((row) => ({
        date: row.date,
        requestCount: row.requestCount,
        cost: toNumber(row.estimatedCost),
      })),
    [dailyMain]
  )

  const displayDailyChart = React.useMemo(() => {
    if (dailyChart.length > 0) return dailyChart
    if (periodMode === "today") return []
    return emptyDailySeriesForRange(rangeForDisplay.from, rangeForDisplay.to)
  }, [dailyChart, periodMode, rangeForDisplay.from, rangeForDisplay.to])

  /** API에 해당 기간·공급사 행이 없을 때 (플레이스홀더 0 시리즈와 구분) */
  const dailyChartHasRows = dailyChart.length > 0

  const dailyAuxChart = React.useMemo(
    () =>
      dailyAux30.map((row) => ({
        date: row.date,
        requestCount: row.requestCount,
        cost: toNumber(row.estimatedCost),
      })),
    [dailyAux30]
  )

  const monthlyChart = React.useMemo(
    () =>
      monthly.map((row) => ({
        yearMonth: row.yearMonth,
        requestCount: row.requestCount,
        cost: toNumber(row.estimatedCost),
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
        value: m.requestCount,
      }))
  }, [byModel])

  const modelBarRows = React.useMemo(() => {
    return [...byModel]
      .sort((a, b) => b.requestCount - a.requestCount)
      .map((m) => ({
        label: truncateModelLabel(m.model),
        requests: m.requestCount,
        tokens: m.inputTokens,
      }))
  }, [byModel])

  const totalCost = summary ? toNumber(summary.totalEstimatedCost) : 0
  const totalReq = summary?.totalRequests ?? 0
  const totalTok = summary?.totalInputTokens ?? 0
  const monthlyHasActivity = monthlyChart.some((r) => r.requestCount > 0 || r.cost > 0)

  const todayCostKpi = kpi ? toNumber(kpi.todayEstimatedCost) : totalCost
  const changeRate = kpi?.changeRatePercent != null ? toNumber(kpi.changeRatePercent) : null
  const windowEndLabel = kpi?.comparisonWindowEnd
    ? new Date(kpi.comparisonWindowEnd).toLocaleString("ko-KR", { timeZone: "Asia/Seoul" })
    : null

  const periodLabel =
    periodMode === "today"
      ? "오늘(KST)"
      : periodMode === "7d"
        ? "최근 7일(KST)"
        : periodMode === "30d"
          ? "최근 30일(KST)"
          : "사용자 지정(KST)"

  return (
    <div className="w-full min-h-full pb-8">
      <header className="mb-8 flex flex-col gap-4 border-b border-border pb-6 sm:flex-row sm:items-start sm:justify-between">
        <div className="space-y-2">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-2xl font-semibold tracking-tight">API 사용량</h1>
            <span className="rounded-full border border-border bg-muted/50 px-2 py-0.5 text-xs font-medium text-muted-foreground">
              개인
            </span>
          </div>
          <p className="text-sm text-muted-foreground">
            집계·차트 구간은 한국 표준시(KST) 기준입니다. 사용 로그 시각도 KST로 표시합니다.
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

      <div className="mb-8 flex flex-col gap-4 rounded-lg border border-border bg-muted/20 p-4 sm:flex-row sm:flex-wrap sm:items-end">
        <div className="space-y-2 sm:w-44">
          <Label htmlFor="dash-period">기간</Label>
          <Select
            value={periodMode}
            onValueChange={(v) => setPeriodMode(v as PeriodMode)}
          >
            <SelectTrigger id="dash-period" className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="today">오늘</SelectItem>
              <SelectItem value="7d">최근 7일</SelectItem>
              <SelectItem value="30d">최근 30일</SelectItem>
              <SelectItem value="custom">사용자 정의</SelectItem>
            </SelectContent>
          </Select>
        </div>

        {periodMode === "custom" ? (
          <div className="flex flex-wrap items-end gap-3">
            <div className="space-y-2">
              <Label htmlFor="dash-from">시작일</Label>
              <Input
                id="dash-from"
                type="date"
                value={customFrom}
                onChange={(e) => setCustomFrom(e.target.value)}
                className="w-[160px]"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="dash-to">종료일</Label>
              <Input
                id="dash-to"
                type="date"
                value={customTo}
                onChange={(e) => setCustomTo(e.target.value)}
                className="w-[160px]"
              />
            </div>
          </div>
        ) : null}

        <div className="space-y-2 sm:w-44">
          <Label htmlFor="dash-provider">공급사</Label>
          <Select value={dashboardProvider} onValueChange={setDashboardProvider}>
            <SelectTrigger id="dash-provider" className="w-full">
              <SelectValue placeholder="전체" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={DASHBOARD_PROVIDER_ALL}>전체</SelectItem>
              <SelectItem value="OPENAI">OpenAI</SelectItem>
              <SelectItem value="ANTHROPIC">Anthropic</SelectItem>
              <SelectItem value="GOOGLE">Gemini (Google)</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>

      {mainError ? (
        <p className="mb-6 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {mainError}
        </p>
      ) : null}

      {mainLoading ? (
        <p className="mb-8 text-sm text-muted-foreground">불러오는 중…</p>
      ) : (
        <>
          <section className="mb-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <div className="rounded-lg border border-border bg-card p-4 shadow-sm">
              <p className="text-xs font-medium text-muted-foreground">
                {periodMode === "today" ? "오늘의 예상 지출액" : `${periodLabel} 총 비용`}
              </p>
              <p className="mt-1 text-2xl font-semibold tabular-nums">
                {formatUsd(periodMode === "today" ? todayCostKpi : totalCost)}
              </p>
              <p className="mt-1 text-xs text-muted-foreground">
                {periodMode === "today"
                  ? `요청 ${(summary?.totalRequests ?? 0).toLocaleString("en-US")}건 · 기준 ${periodLabel}`
                  : `요청 ${totalReq.toLocaleString("en-US")}건`}
              </p>
            </div>

            <div className="rounded-lg border border-border bg-card p-4 shadow-sm">
              <p className="text-xs font-medium text-muted-foreground">
                {periodMode === "today" ? "전일 동기 대비 증감률" : "기간 총 요청"}
              </p>
              {periodMode === "today" ? (
                <>
                  <p className="mt-1 text-2xl font-semibold tabular-nums">
                    {changeRate == null ? "비교 불가" : `${changeRate >= 0 ? "+" : ""}${changeRate.toFixed(2)}%`}
                  </p>
                  <p className="mt-1 text-xs text-muted-foreground">
                    {changeRate == null
                      ? "전일 동일 구간 비용 합이 0이라 비율을 표시하지 않습니다."
                      : windowEndLabel
                        ? `비교 시각까지(KST): ${windowEndLabel}`
                        : "어제 동일 길이 구간과 비교"}
                  </p>
                </>
              ) : (
                <>
                  <p className="mt-1 text-2xl font-semibold tabular-nums">{formatRequestCount(totalReq)}</p>
                  <p className="mt-1 text-xs text-muted-foreground">증감률은 &quot;오늘&quot; 모드에서 제공됩니다.</p>
                </>
              )}
            </div>

            <div className="rounded-lg border border-border bg-card p-4 shadow-sm">
              <p className="text-xs font-medium text-muted-foreground">{periodLabel} 입력 토큰</p>
              <p className="mt-1 text-2xl font-semibold tabular-nums">{formatTokenCount(totalTok)}</p>
            </div>

            <div className="rounded-lg border border-border bg-card p-4 shadow-sm">
              <p className="text-xs font-medium text-muted-foreground">
                {periodMode === "today" ? "전일 동일 구간 비용(참고)" : `${periodLabel} 오류 건수`}
              </p>
              <p className="mt-1 text-2xl font-semibold tabular-nums">
                {periodMode === "today" && kpi
                  ? formatUsd(toNumber(kpi.yesterdaySameWindowEstimatedCost))
                  : (summary?.totalErrors ?? 0).toLocaleString("en-US")}
              </p>
              <p className="mt-1 text-xs text-muted-foreground">
                {periodMode === "today" ? "비교용(어제 동일 길이 구간)" : "실패·4xx·5xx 집계"}
              </p>
            </div>
          </section>

          <section className="mb-10 rounded-lg border border-border p-4 shadow-sm">
            <h2 className="mb-4 text-lg font-medium">
              {periodMode === "today" ? "시간별 요청·비용 (오늘)" : "일별 요청·비용 (선택 기간)"}
            </h2>
            {periodMode === "today" ? (
              <>
                <div className="h-[400px] w-full">
                  <ResponsiveContainer width="100%" height="100%">
                    <ComposedChart data={displayHourlyChart}>
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
                      <Bar yAxisId="left" dataKey="requestCount" name="요청 수" fill={CHART_COLORS[3]} radius={[4, 4, 0, 0]} />
                      <Line
                        yAxisId="right"
                        type="monotone"
                        dataKey="cost"
                        name="비용 (USD)"
                        stroke={CHART_COLORS[0]}
                        strokeWidth={2}
                        dot={false}
                      />
                    </ComposedChart>
                  </ResponsiveContainer>
                </div>
                {totalReq === 0 ? (
                  <p className="mt-2 text-xs text-muted-foreground">
                    선택한 공급사·기간에 집계된 사용량이 없습니다. 해당 공급사로 API를 호출한 뒤 새로고침하면 반영됩니다.
                  </p>
                ) : null}
              </>
            ) : displayDailyChart.length === 0 ? (
              <p className="text-sm text-muted-foreground">기간을 선택해 주세요</p>
            ) : (
              <div className="h-[400px] w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <ComposedChart data={displayDailyChart}>
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
                    <Bar yAxisId="left" dataKey="requestCount" name="요청 수" fill={CHART_COLORS[3]} radius={[4, 4, 0, 0]} />
                    <Line
                      yAxisId="right"
                      type="monotone"
                      dataKey="cost"
                      name="비용 (USD)"
                      stroke={CHART_COLORS[0]}
                      strokeWidth={2}
                      dot={false}
                    />
                  </ComposedChart>
                </ResponsiveContainer>
              </div>
            )}
            {periodMode !== "today" && !dailyChartHasRows && displayDailyChart.length > 0 ? (
              <p className="mt-2 text-xs text-muted-foreground">
                선택한 공급사·기간에 집계된 사용량이 없습니다. 해당 공급사로 API를 호출한 뒤 새로고침하면 반영됩니다.
              </p>
            ) : null}
          </section>

          <div className="mb-10 grid gap-8 lg:grid-cols-2">
            <section className="rounded-lg border border-border p-4 shadow-sm">
              <h2 className="mb-4 text-lg font-medium">모델별 요청 비중</h2>
              {pieData.length === 0 ? (
                <div className="flex h-[320px] items-center justify-center rounded-md border border-dashed border-border bg-muted/15 text-sm text-muted-foreground">
                  집계 데이터 없음
                </div>
              ) : (
                <div className="h-[320px] w-full">
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
                        {pieData.map((_, i) => (
                          <Cell key={`cell-${i}`} fill={CHART_COLORS[i % CHART_COLORS.length]} />
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
              <h2 className="mb-4 text-lg font-medium">모델별 요청 수 (가로)</h2>
              {modelBarRows.length === 0 ? (
                <div className="flex h-[320px] items-center justify-center rounded-md border border-dashed border-border bg-muted/15 text-sm text-muted-foreground">
                  집계 데이터 없음
                </div>
              ) : (
                <div className="h-[320px] w-full">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart layout="vertical" data={modelBarRows} margin={{ left: 8, right: 16 }}>
                      <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                      <XAxis type="number" />
                      <YAxis type="category" dataKey="label" width={120} tick={{ fontSize: 11 }} />
                      <Tooltip />
                      <Bar dataKey="requests" name="요청 수" fill={CHART_COLORS[1]} radius={[0, 4, 4, 0]} />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              )}
            </section>
          </div>

          <section className="mb-10 rounded-lg border border-border p-4 shadow-sm">
            <h2 className="mb-4 text-lg font-medium">모델별 입력 토큰 (가로)</h2>
            {modelBarRows.length === 0 ? (
              <div className="flex h-[320px] items-center justify-center rounded-md border border-dashed border-border bg-muted/15 text-sm text-muted-foreground">
                집계 데이터 없음
              </div>
            ) : (
              <div className="h-[320px] w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart layout="vertical" data={modelBarRows} margin={{ left: 8, right: 16 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                    <XAxis type="number" />
                    <YAxis type="category" dataKey="label" width={120} tick={{ fontSize: 11 }} />
                    <Tooltip formatter={(v) => formatTokenCount(tooltipNumericValue(v))} />
                    <Bar dataKey="tokens" name="입력 토큰" fill={CHART_COLORS[2]} radius={[0, 4, 4, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}
          </section>

          {periodMode === "today" && dailyAuxChart.length > 0 ? (
            <section className="mb-10 rounded-lg border border-border p-4 shadow-sm">
              <h2 className="mb-4 text-lg font-medium">일별 요청·비용 (최근 30일, 보조)</h2>
              <div className="h-[360px] w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <ComposedChart data={dailyAuxChart}>
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
                    <Bar yAxisId="left" dataKey="requestCount" name="요청 수" fill={CHART_COLORS[3]} radius={[4, 4, 0, 0]} />
                    <Line
                      yAxisId="right"
                      type="monotone"
                      dataKey="cost"
                      name="비용 (USD)"
                      stroke={CHART_COLORS[0]}
                      strokeWidth={2}
                      dot={false}
                    />
                  </ComposedChart>
                </ResponsiveContainer>
              </div>
            </section>
          ) : null}

          <section className="mb-10 rounded-lg border border-border p-4 shadow-sm">
            <h2 className="mb-4 text-lg font-medium">월별 요청·비용</h2>
            {monthlyChart.length === 0 || !monthlyHasActivity ? (
              <div className="flex h-[360px] items-center justify-center rounded-md border border-dashed border-border bg-muted/15 text-sm text-muted-foreground">
                집계 데이터 없음
              </div>
            ) : (
              <div className="h-[360px] w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <ComposedChart data={monthlyChart}>
                    <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                    <XAxis dataKey="yearMonth" tick={{ fontSize: 11 }} />
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
                    <Bar yAxisId="left" dataKey="requestCount" name="요청 수" fill={CHART_COLORS[2]} radius={[4, 4, 0, 0]} />
                    <Line
                      yAxisId="right"
                      type="monotone"
                      dataKey="cost"
                      name="비용 (USD)"
                      stroke={CHART_COLORS[0]}
                      strokeWidth={2}
                      dot={false}
                    />
                  </ComposedChart>
                </ResponsiveContainer>
              </div>
            )}
          </section>
        </>
      )}
    </div>
  )
}
