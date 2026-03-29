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

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { buildUsageQuery, fetchUsageJson } from "@/lib/usage/fetch-usage"
import { formatRequestCount, formatTokenCount, formatUsd, toNumber } from "@/lib/usage/format"
import type {
  DailyUsagePoint,
  ModelUsageAggregate,
  MonthlyUsagePoint,
  PagedLogsResponse,
  UsageLogEntryResponse,
  UsageProviderFilter,
  UsageSummaryResponse,
} from "@/lib/usage/types"
import { addUtcDays, formatUtcIsoDate } from "@/lib/usage/utc-dates"

const CHART_COLORS = [
  "#6366f1",
  "#22c55e",
  "#f97316",
  "#ec4899",
  "#06b6d4",
  "#eab308",
  "#a855f7",
  "#14b8a6",
]

const RANGE_DAYS = 30
const MONTHLY_LOOKBACK_DAYS = 365
const LOGS_PAGE_SIZE = 10

const LOG_PROVIDER_ALL = "__all__"

/** Recharts Tooltip `value`는 배열 등이 될 수 있어 숫자 집계용으로만 안전 변환한다. */
function tooltipNumericValue(value: unknown): number {
  if (typeof value === "number") return value
  if (typeof value === "string") return toNumber(value)
  return 0
}

function truncateModelLabel(model: string, max = 36) {
  if (model.length <= max) return model
  return `${model.slice(0, max - 1)}…`
}

export function UsageDashboard() {
  const [summaryToday, setSummaryToday] = React.useState<UsageSummaryResponse | null>(null)
  const [summary30, setSummary30] = React.useState<UsageSummaryResponse | null>(null)
  const [daily, setDaily] = React.useState<DailyUsagePoint[]>([])
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
        const t = formatUtcIsoDate()
        const f30 = addUtcDays(t, -(RANGE_DAYS - 1))
        const fy = addUtcDays(t, -MONTHLY_LOOKBACK_DAYS)

        const [st, s30, d, m, bm] = await Promise.all([
          fetchUsageJson<UsageSummaryResponse>(
            `dashboard/summary${buildUsageQuery({ from: t, to: t })}`
          ),
          fetchUsageJson<UsageSummaryResponse>(
            `dashboard/summary${buildUsageQuery({ from: f30, to: t })}`
          ),
          fetchUsageJson<DailyUsagePoint[]>(
            `dashboard/series/daily${buildUsageQuery({ from: f30, to: t })}`
          ),
          fetchUsageJson<MonthlyUsagePoint[]>(
            `dashboard/series/monthly${buildUsageQuery({ from: fy, to: t })}`
          ),
          fetchUsageJson<ModelUsageAggregate[]>(
            `dashboard/by-model${buildUsageQuery({ from: f30, to: t })}`
          ),
        ])
        if (!cancelled) {
          setSummaryToday(st)
          setSummary30(s30)
          setDaily(Array.isArray(d) ? d : [])
          setMonthly(Array.isArray(m) ? m : [])
          setByModel(Array.isArray(bm) ? bm : [])
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
  }, [mainRefresh])

  React.useEffect(() => {
    let cancelled = false
    setLogsLoading(true)
    setLogsError(null)
    ;(async () => {
      try {
        const t = formatUtcIsoDate()
        const f30 = addUtcDays(t, -(RANGE_DAYS - 1))
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

  const dailyChart = React.useMemo(
    () =>
      daily.map((row) => ({
        date: row.date,
        requestCount: row.requestCount,
        cost: toNumber(row.estimatedCost),
      })),
    [daily]
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

  const hasMainData =
    (summary30 && summary30.totalRequests > 0) ||
    daily.length > 0 ||
    monthly.length > 0 ||
    byModel.some((m) => m.requestCount > 0)

  const todayCost = summaryToday ? toNumber(summaryToday.totalEstimatedCost) : 0
  const s30Requests = summary30?.totalRequests ?? 0
  const s30Tokens = summary30?.totalInputTokens ?? 0
  const s30Cost = summary30 ? toNumber(summary30.totalEstimatedCost) : 0
  const monthlyHasActivity = monthlyChart.some((r) => r.requestCount > 0 || r.cost > 0)

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
            집계 구간은 UTC 기준 YYYY-MM-DD입니다. (최근 {RANGE_DAYS}일 / 월별 최대 {MONTHLY_LOOKBACK_DAYS}일)
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
              <p className="text-xs font-medium text-muted-foreground">오늘(UTC) 총 비용</p>
              <p className="mt-1 text-2xl font-semibold tabular-nums">{formatUsd(todayCost)}</p>
              <p className="mt-1 text-xs text-muted-foreground">
                오늘(UTC) API 요청 {(summaryToday?.totalRequests ?? 0).toLocaleString("en-US")}건
              </p>
            </div>
            <div className="rounded-lg border border-border bg-card p-4 shadow-sm">
              <p className="text-xs font-medium text-muted-foreground">최근 {RANGE_DAYS}일 요청 수</p>
              <p className="mt-1 text-2xl font-semibold tabular-nums">{formatRequestCount(s30Requests)}</p>
            </div>
            <div className="rounded-lg border border-border bg-card p-4 shadow-sm">
              <p className="text-xs font-medium text-muted-foreground">최근 {RANGE_DAYS}일 입력 토큰</p>
              <p className="mt-1 text-2xl font-semibold tabular-nums">{formatTokenCount(s30Tokens)}</p>
            </div>
            <div className="rounded-lg border border-border bg-card p-4 shadow-sm">
              <p className="text-xs font-medium text-muted-foreground">최근 {RANGE_DAYS}일 총 비용</p>
              <p className="mt-1 text-2xl font-semibold tabular-nums">{formatUsd(s30Cost)}</p>
            </div>
          </section>

          {!hasMainData ? (
            <p className="mb-10 text-center text-sm text-muted-foreground">사용 데이터가 없습니다</p>
          ) : null}

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
                      <CartesianGrid strokeDasharray="3 3" />
                      <XAxis type="number" />
                      <YAxis type="category" dataKey="label" width={120} tick={{ fontSize: 11 }} />
                      <Tooltip />
                      <Bar dataKey="requests" name="요청 수" fill={CHART_COLORS[0]} radius={[0, 4, 4, 0]} />
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
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis type="number" />
                    <YAxis type="category" dataKey="label" width={120} tick={{ fontSize: 11 }} />
                    <Tooltip formatter={(v) => formatTokenCount(tooltipNumericValue(v))} />
                    <Bar dataKey="tokens" name="입력 토큰" fill={CHART_COLORS[1]} radius={[0, 4, 4, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}
          </section>

          <section className="mb-10 rounded-lg border border-border p-4 shadow-sm">
            <h2 className="mb-4 text-lg font-medium">일별 요청·비용 (최근 {RANGE_DAYS}일)</h2>
            {dailyChart.length === 0 ? (
              <p className="text-sm text-muted-foreground">사용 데이터가 없습니다</p>
            ) : (
              <div className="h-[360px] w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <ComposedChart data={dailyChart}>
                    <CartesianGrid strokeDasharray="3 3" />
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
                    <Bar yAxisId="left" dataKey="requestCount" name="요청 수" fill={CHART_COLORS[2]} radius={[4, 4, 0, 0]} />
                    <Line
                      yAxisId="right"
                      type="monotone"
                      dataKey="cost"
                      name="비용 (USD)"
                      stroke={CHART_COLORS[3]}
                      strokeWidth={2}
                      dot={false}
                    />
                  </ComposedChart>
                </ResponsiveContainer>
              </div>
            )}
          </section>

          <section className="mb-10 rounded-lg border border-border p-4 shadow-sm">
            <h2 className="mb-4 text-lg font-medium">월별 요청·비용</h2>
            {monthlyChart.length === 0 || !monthlyHasActivity ? (
              <p className="text-sm text-muted-foreground">사용 데이터가 없습니다</p>
            ) : (
              <div className="h-[360px] w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <ComposedChart data={monthlyChart}>
                    <CartesianGrid strokeDasharray="3 3" />
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
                    <Bar yAxisId="left" dataKey="requestCount" name="요청 수" fill={CHART_COLORS[4]} radius={[4, 4, 0, 0]} />
                    <Line
                      yAxisId="right"
                      type="monotone"
                      dataKey="cost"
                      name="비용 (USD)"
                      stroke={CHART_COLORS[5]}
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
        <h2 className="mb-4 text-lg font-medium">사용 로그</h2>

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
                    <th className="px-3 py-2 font-medium">시각 (UTC)</th>
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
                      <td className="px-3 py-2 font-mono text-xs whitespace-nowrap">{row.occurredAt}</td>
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
