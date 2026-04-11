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
  PagedLogsResponse,
  PeriodMode,
  UsageCostIntradayKpiResponse,
  UsageLogEntryResponse,
  UsageProviderFilter,
  UsageSummaryResponse,
} from "@/lib/usage/types"
import { addKstDays, formatKstIsoDate } from "@/lib/usage/kst-dates"

const CHART_COLORS = [
  "#0a0a0a",
  "#404040",
  "#737373",
  "#a3a3a3",
  "#d4d4d4",
]

const MONTHLY_LOOKBACK_DAYS = 365
const LOGS_PAGE_SIZE = 10
const LOG_PROVIDER_ALL = "__all__"
const DASHBOARD_PROVIDER_ALL = "__all__"

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
  const [periodMode, setPeriodMode] = React.useState<PeriodMode>("today")
  const [dashboardProvider, setDashboardProvider] = React.useState<string>(DASHBOARD_PROVIDER_ALL)
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

  const [logs, setLogs] = React.useState<PagedLogsResponse | null>(null)
  const [logsLoading, setLogsLoading] = React.useState(true)
  const [logsError, setLogsError] = React.useState<string | null>(null)
  const [logsPage, setLogsPage] = React.useState(0)
  const [logProvider, setLogProvider] = React.useState<string>(LOG_PROVIDER_ALL)
  const [modelDraft, setModelDraft] = React.useState("")
  const [appliedModelMask, setAppliedModelMask] = React.useState("")
  const [mainRefresh, setMainRefresh] = React.useState(0)

  const applyLogFilters = React.useCallback(() => {
    setLogsPage(0)
    setAppliedModelMask(modelDraft.trim())
  }, [modelDraft])

  React.useEffect(() => {
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
  }, [mainRefresh, periodMode, customFrom, customTo, dashboardProvider])

  React.useEffect(() => {
    let cancelled = false
    setLogsLoading(true)
    setLogsError(null)
    ;(async () => {
      try {
        const t = formatKstIsoDate()
        const f30 = addKstDays(t, -29)
        const providerParam =
          logProvider !== LOG_PROVIDER_ALL ? (logProvider as UsageProviderFilter) : undefined
        const q = buildUsageQuery({
          from: f30,
          to: t,
          page: logsPage,
          size: LOGS_PAGE_SIZE,
          provider: providerParam,
          model: appliedModelMask || undefined,
        })
        const data = await fetchUsageJson<PagedLogsResponse>(`logs${q}`)
        if (!cancelled) setLogs(data)
      } catch (e) {
        if (!cancelled) {
          setLogsError(e instanceof Error ? e.message : "로그를 불러오지 못했습니다")
        }
      } finally {
        if (!cancelled) setLogsLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [logsPage, appliedModelMask, logProvider])

  const hourlyChart = React.useMemo(
    () =>
      hourly.map((row) => ({
        label: `${row.hour}시`,
        requestCount: row.requestCount,
        cost: toNumber(row.estimatedCost),
      })),
    [hourly]
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

  const hasMainData =
    totalReq > 0 || dailyMain.length > 0 || hourly.length > 0 || byModel.some((m) => m.requestCount > 0)

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
              <SelectItem value="GOOGLE">Google</SelectItem>
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

          {!hasMainData ? (
            <p className="mb-10 text-center text-sm text-muted-foreground">사용 데이터가 없습니다</p>
          ) : null}

          <section className="mb-10 rounded-lg border border-border p-4 shadow-sm">
            <h2 className="mb-4 text-lg font-medium">
              {periodMode === "today" ? "시간별 요청·비용 (오늘)" : "일별 요청·비용 (선택 기간)"}
            </h2>
            {periodMode === "today" ? (
              hourlyChart.length === 0 ? (
                <p className="text-sm text-muted-foreground">사용 데이터가 없습니다</p>
              ) : (
                <div className="h-[400px] w-full">
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
              )
            ) : dailyChart.length === 0 ? (
              <p className="text-sm text-muted-foreground">사용 데이터가 없습니다</p>
            ) : (
              <div className="h-[400px] w-full">
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
          </section>

          <div className="mb-10 grid gap-8 lg:grid-cols-2">
            <section className="rounded-lg border border-border p-4 shadow-sm">
              <h2 className="mb-4 text-lg font-medium">모델별 요청 비중</h2>
              {pieData.length === 0 ? (
                <p className="text-sm text-muted-foreground">사용 데이터가 없습니다</p>
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
                <p className="text-sm text-muted-foreground">사용 데이터가 없습니다</p>
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
              <p className="text-sm text-muted-foreground">사용 데이터가 없습니다</p>
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
              <p className="text-sm text-muted-foreground">사용 데이터가 없습니다</p>
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

      <section className="rounded-lg border border-border p-4 shadow-sm">
        <div className="mb-4 space-y-1">
          <h2 className="text-lg font-medium">사용 로그</h2>
          <p className="text-sm text-muted-foreground">발생 시각은 한국 표준시(KST)입니다.</p>
        </div>

        <div className="mb-4 flex flex-col gap-4 sm:flex-row sm:flex-wrap sm:items-end">
          <div className="space-y-2 sm:w-48">
            <Label htmlFor="log-provider">공급자</Label>
            <Select
              value={logProvider}
              onValueChange={(v) => {
                setLogsPage(0)
                setLogProvider(v)
              }}
            >
              <SelectTrigger id="log-provider" className="w-full">
                <SelectValue placeholder="전체" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={LOG_PROVIDER_ALL}>전체</SelectItem>
                <SelectItem value="OPENAI">OpenAI</SelectItem>
                <SelectItem value="ANTHROPIC">Anthropic</SelectItem>
                <SelectItem value="GOOGLE">Google</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div className="min-w-0 flex-1 space-y-2">
            <Label htmlFor="log-model">모델 (부분 일치)</Label>
            <Input
              id="log-model"
              value={modelDraft}
              onChange={(e) => setModelDraft(e.target.value)}
              onBlur={applyLogFilters}
              onKeyDown={(e) => {
                if (e.key === "Enter") applyLogFilters()
              }}
              placeholder="예: gpt-4"
              autoComplete="off"
            />
          </div>

          <Button type="button" variant="secondary" size="sm" className="sm:self-end" onClick={applyLogFilters}>
            필터 적용
          </Button>
        </div>

        {logsError ? (
          <p className="mb-4 text-sm text-destructive">{logsError}</p>
        ) : null}

        {logsLoading ? (
          <p className="text-sm text-muted-foreground">로그 불러오는 중…</p>
        ) : !logs || logs.content.length === 0 ? (
          <p className="text-sm text-muted-foreground">사용 데이터가 없습니다</p>
        ) : (
          <>
            <div className="overflow-x-auto rounded-md border border-border">
              <table className="w-full min-w-[720px] text-left text-sm">
                <thead className="border-b border-border bg-muted/40">
                  <tr>
                    <th className="px-3 py-2 font-medium">시각 (KST)</th>
                    <th className="px-3 py-2 font-medium">공급자</th>
                    <th className="px-3 py-2 font-medium">모델</th>
                    <th className="px-3 py-2 font-medium">토큰</th>
                    <th className="px-3 py-2 font-medium">비용</th>
                    <th className="px-3 py-2 font-medium">성공</th>
                  </tr>
                </thead>
                <tbody>
                  {logs.content.map((row: UsageLogEntryResponse) => (
                    <tr key={row.eventId} className="border-b border-border last:border-0">
                      <td className="px-3 py-2 font-mono text-xs whitespace-nowrap">
                        {formatOccurredAtKst(row.occurredAt)}
                      </td>
                      <td className="px-3 py-2">{row.provider}</td>
                      <td className="px-3 py-2 font-mono text-xs">{row.model}</td>
                      <td className="px-3 py-2 tabular-nums">{row.totalTokens ?? "—"}</td>
                      <td className="px-3 py-2 tabular-nums">
                        {row.estimatedCost != null ? formatUsd(row.estimatedCost) : "—"}
                      </td>
                      <td className="px-3 py-2">{row.requestSuccessful ? "예" : "아니오"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="mt-4 flex flex-wrap items-center justify-between gap-2 text-sm text-muted-foreground">
              <span>
                페이지 {logs.page + 1} / {Math.max(1, logs.totalPages)} · 총 {logs.totalElements.toLocaleString("en-US")}건
              </span>
              <div className="flex gap-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={logsPage <= 0}
                  onClick={() => setLogsPage((p) => Math.max(0, p - 1))}
                >
                  이전
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={logsPage + 1 >= logs.totalPages}
                  onClick={() => setLogsPage((p) => p + 1)}
                >
                  다음
                </Button>
              </div>
            </div>
          </>
        )}
      </section>
    </div>
  )
}
