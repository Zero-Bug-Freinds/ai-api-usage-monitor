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

const RANGE_DAYS = 30
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

const H_BAR_MARGIN = { left: 8, right: 16 }
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
  const [summaryRange, setSummaryRange] = React.useState<UsageSummaryResponse | null>(null)
  const [summaryToday, setSummaryToday] = React.useState<UsageSummaryResponse | null>(null)
  const [daily, setDaily] = React.useState<DailyUsagePoint[]>([])
  const [monthly, setMonthly] = React.useState<MonthlyUsagePoint[]>([])
  const [byModel, setByModel] = React.useState<ModelUsageAggregate[]>([])

  const todayKst = formatKstIsoDate()
  const { from: rangeFrom, to: rangeTo } = rangeForPeriod(periodMode, todayKst, customFrom, customTo)

  React.useEffect(() => {
    let cancelled = false
    setMainLoading(true)
    setMainError(null)
    ;(async () => {
      try {
        const t = formatKstIsoDate()
        const f30 = addKstDays(t, -(RANGE_DAYS - 1))
        const fy = addKstDays(t, -MONTHLY_LOOKBACK_DAYS)
        const pq = buildUsageQuery({ provider: providerParam(dashProvider) })

        if (periodMode === "today") {
          const [kpi, h, st, s30, d, m, bm] = await Promise.all([
            fetchUsageJson<UsageCostIntradayKpiResponse>(`dashboard/kpi/cost-intraday${pq}`),
            fetchUsageJson<HourlyUsagePoint[]>(
              `dashboard/series/hourly${buildUsageQuery({ date: t, provider: providerParam(dashProvider) })}`
            ),
            fetchUsageJson<UsageSummaryResponse>(
              `dashboard/summary${buildUsageQuery({ from: t, to: t, provider: providerParam(dashProvider) })}`
            ),
            fetchUsageJson<UsageSummaryResponse>(
              `dashboard/summary${buildUsageQuery({ from: f30, to: t, provider: providerParam(dashProvider) })}`
            ),
            fetchUsageJson<DailyUsagePoint[]>(
              `dashboard/series/daily${buildUsageQuery({ from: f30, to: t, provider: providerParam(dashProvider) })}`
            ),
            fetchUsageJson<MonthlyUsagePoint[]>(
              `dashboard/series/monthly${buildUsageQuery({ from: fy, to: t, provider: providerParam(dashProvider) })}`
            ),
            fetchUsageJson<ModelUsageAggregate[]>(
              `dashboard/by-model${buildUsageQuery({ from: f30, to: t, provider: providerParam(dashProvider) })}`
            ),
          ])
          if (!cancelled) {
            setCostKpi(kpi)
            setHourly(Array.isArray(h) ? h : [])
            setSummaryToday(st)
            setSummaryRange(s30)
            setDaily(Array.isArray(d) ? d : [])
            setMonthly(Array.isArray(m) ? m : [])
            setByModel(Array.isArray(bm) ? bm : [])
          }
        } else {
          const [sr, d, m, bm] = await Promise.all([
            fetchUsageJson<UsageSummaryResponse>(
              `dashboard/summary${buildUsageQuery({ from: rangeFrom, to: rangeTo, provider: providerParam(dashProvider) })}`
            ),
            fetchUsageJson<DailyUsagePoint[]>(
              `dashboard/series/daily${buildUsageQuery({ from: rangeFrom, to: rangeTo, provider: providerParam(dashProvider) })}`
            ),
            fetchUsageJson<MonthlyUsagePoint[]>(
              `dashboard/series/monthly${buildUsageQuery({ from: fy, to: rangeTo, provider: providerParam(dashProvider) })}`
            ),
            fetchUsageJson<ModelUsageAggregate[]>(
              `dashboard/by-model${buildUsageQuery({ from: rangeFrom, to: rangeTo, provider: providerParam(dashProvider) })}`
            ),
          ])
          if (!cancelled) {
            setCostKpi(null)
            setHourly(null)
            setSummaryToday(null)
            setSummaryRange(sr)
            setDaily(Array.isArray(d) ? d : [])
            setMonthly(Array.isArray(m) ? m : [])
            setByModel(Array.isArray(bm) ? bm : [])
          }
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
  }, [mainRefresh, periodMode, rangeFrom, rangeTo, dashProvider])

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
    (summaryRange && summaryRange.totalRequests > 0) ||
    daily.length > 0 ||
    monthly.length > 0 ||
    byModel.some((m) => m.requestCount > 0) ||
    (hourly && hourly.some((h) => h.requestCount > 0 || toNumber(h.estimatedCostUsd) > 0))

  const rangeCost = summaryRange ? toNumber(summaryRange.totalEstimatedCost) : 0
  const rangeRequests = summaryRange?.totalRequests ?? 0
  const rangeTokens = summaryRange?.totalInputTokens ?? 0
  const rangeErrors = summaryRange?.totalErrors ?? 0
  const rangeSuccess =
    rangeRequests > 0 ? Math.max(0, rangeRequests - rangeErrors) : 0
  const successRatePercent =
    rangeRequests > 0 ? (100 * rangeSuccess) / rangeRequests : null

  const todayCostFromKpi = costKpi ? toNumber(costKpi.todayEstimatedCostUsd) : 0
  const todayRequests = summaryToday?.totalRequests ?? 0
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

      {mainLoading ? (
        <p className="mb-8 text-sm text-muted-foreground">불러오는 중…</p>
      ) : (
        <>
          <section className="mb-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {periodMode === "today" && costKpi ? (
              <>
                <div className="rounded-lg border border-border bg-card p-4 shadow-sm">
                  <p className="text-xs font-medium text-muted-foreground">오늘 예상 지출 (USD)</p>
                  <p className="mt-1 text-2xl font-semibold tabular-nums">{formatUsd(todayCostFromKpi)}</p>
                  <p className="mt-1 text-xs text-muted-foreground">
                    오늘 요청 {todayRequests.toLocaleString("en-US")}건
                  </p>
                </div>
                <div className="rounded-lg border border-border bg-card p-4 shadow-sm">
                  <p className="text-xs font-medium text-muted-foreground">전일 동기 대비</p>
                  {costKpi.changeRatePercent == null ? (
                    <p className="mt-1 text-sm text-muted-foreground">비교 불가 (전일 동일 구간 비용 없음)</p>
                  ) : (
                    <p className="mt-1 text-2xl font-semibold tabular-nums">
                      {toNumber(costKpi.changeRatePercent).toFixed(1)}%
                    </p>
                  )}
                  <p className="mt-1 text-xs text-muted-foreground">
                    기준 시각 {formatOccurredAtKst(costKpi.comparisonWindowEnd)} (KST)
                  </p>
                </div>
                <div className="rounded-lg border border-border bg-card p-4 shadow-sm">
                  <p className="text-xs font-medium text-muted-foreground">최근 {RANGE_DAYS}일 총 비용</p>
                  <p className="mt-1 text-2xl font-semibold tabular-nums">
                    {formatUsd(summaryRange ? toNumber(summaryRange.totalEstimatedCost) : 0)}
                  </p>
                </div>
                <div className="rounded-lg border border-border bg-card p-4 shadow-sm">
                  <p className="text-xs font-medium text-muted-foreground">최근 {RANGE_DAYS}일 요청</p>
                  <p className="mt-1 text-2xl font-semibold tabular-nums">
                    {formatRequestCount(summaryRange?.totalRequests ?? 0)}
                  </p>
                </div>
              </>
            ) : (
              <>
                <div className="rounded-lg border border-border bg-card p-4 shadow-sm">
                  <p className="text-xs font-medium text-muted-foreground">기간 총 비용 (USD)</p>
                  <p className="mt-1 text-2xl font-semibold tabular-nums">{formatUsd(rangeCost)}</p>
                </div>
                <div className="rounded-lg border border-border bg-card p-4 shadow-sm">
                  <p className="text-xs font-medium text-muted-foreground">기간 총 요청</p>
                  <p className="mt-1 text-2xl font-semibold tabular-nums">{formatRequestCount(rangeRequests)}</p>
                </div>
                <div className="rounded-lg border border-border bg-card p-4 shadow-sm">
                  <p className="text-xs font-medium text-muted-foreground">기간 입력 토큰</p>
                  <p className="mt-1 text-2xl font-semibold tabular-nums">{formatTokenCount(rangeTokens)}</p>
                </div>
                <div className="rounded-lg border border-border bg-card p-4 shadow-sm">
                  <p className="text-xs font-medium text-muted-foreground">성공률</p>
                  <p className="mt-1 text-2xl font-semibold tabular-nums">
                    {successRatePercent == null ? "—" : `${successRatePercent.toFixed(1)}%`}
                  </p>
                  <p className={`mt-1 text-xs tabular-nums ${errorSubStyle}`}>
                    오류 {rangeErrors.toLocaleString("en-US")}건 / 총 요청{" "}
                    {rangeRequests.toLocaleString("en-US")}건
                  </p>
                  <p className="mt-1 text-[11px] text-muted-foreground leading-snug">
                    성공률 = (총 요청 − 오류 건수) ÷ 총 요청 × 100. 오류는 요청 실패 또는 HTTP 4xx 이상으로
                    집계됩니다.
                  </p>
                </div>
              </>
            )}
          </section>

          {!hasMainData ? (
            <p className="mb-10 text-center text-sm text-muted-foreground">
              선택한 기간·공급사에 대한 사용 데이터가 없습니다
            </p>
          ) : null}

          <section className="mb-10 rounded-lg border border-border p-4 shadow-sm">
            <h2 className="mb-4 text-lg font-medium">{mainChartTitle}</h2>
            {periodMode === "today" && hourlyChart.length > 0 ? (
              <div className="h-[380px] w-full">
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
              <div className="h-[380px] w-full">
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
                <div className="h-[320px] w-full">
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
                <div className="h-[320px] w-full max-w-full">
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

          <section className="mb-10 rounded-lg border border-border p-4 shadow-sm">
            <h2 className="mb-4 text-lg font-medium">모델별 입력 토큰 (가로)</h2>
            {modelBarRows.length === 0 ? (
              <p className="text-sm text-muted-foreground">집계 데이터 없음</p>
            ) : (
              <div className="h-[320px] w-full max-w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart layout="vertical" data={modelBarRows} margin={H_BAR_MARGIN}>
                    <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                    <XAxis type="number" />
                    <YAxis type="category" dataKey="label" width={128} tick={{ fontSize: 11 }} />
                    <Tooltip formatter={(v) => formatTokenCount(tooltipNumericValue(v))} />
                    <Bar dataKey="tokens" name="입력 토큰" radius={[0, 4, 4, 0]}>
                      {modelBarRows.map((row) => (
                        <Cell key={`in-${row.model}`} fill={colorForModel(row.model, row.provider)} />
                      ))}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}
          </section>

          <section className="mb-10 rounded-lg border border-border p-4 shadow-sm">
            <h2 className="mb-4 text-lg font-medium">모델별 출력 토큰 (가로)</h2>
            {modelBarRows.length === 0 ? (
              <p className="text-sm text-muted-foreground">집계 데이터 없음</p>
            ) : (
              <div className="h-[320px] w-full max-w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart layout="vertical" data={modelBarRows} margin={H_BAR_MARGIN}>
                    <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                    <XAxis type="number" />
                    <YAxis type="category" dataKey="label" width={128} tick={{ fontSize: 11 }} />
                    <Tooltip formatter={(v) => formatTokenCount(tooltipNumericValue(v))} />
                    <Bar dataKey="outTokens" name="출력 토큰" radius={[0, 4, 4, 0]}>
                      {modelBarRows.map((row) => (
                        <Cell key={`out-${row.model}`} fill={colorForModel(row.model, row.provider)} />
                      ))}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}
          </section>

          <section className="mb-10 rounded-lg border border-border p-4 shadow-sm">
            <h2 className="mb-4 text-lg font-medium">월별 요청 수 (누적 추이)</h2>
            {monthlyChart.length === 0 || !monthlyHasActivity ? (
              <p className="text-sm text-muted-foreground">집계 데이터 없음</p>
            ) : (
              <div className="h-[360px] w-full">
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
