"use client"

import * as React from "react"
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ComposedChart,
  LabelList,
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

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const AnyLegend = Legend as any

/** 공급사별 기본 색 — 모든 차트에서 동일 키에 동일 색 */
const PROVIDER_COLOR: Record<string, string> = {
  GOOGLE: "#F97316",
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
const MODEL_REQUESTS_TOP_N = 10
const OTHERS_LABEL = "기타 (Others)"
const OTHERS_BAR_COLOR = "#94a3b8"
const MODEL_COLOR_CACHE = new Map<string, string>()

const PROVIDER_MODEL_PALETTES: Record<string, string[]> = {
  GOOGLE: ["#9a3412", "#c2410c", "#ea580c", "#F97316", "#fb923c", "#fdba74"],
  OPENAI: ["#1e3a8a", "#1d4ed8", "#2563eb", "#3b82f6", "#60a5fa", "#93c5fd"],
  ANTHROPIC: ["#78350f", "#92400e", "#b45309", "#d97706", "#f59e0b", "#fbbf24"],
}

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
  const key = `${provider}::${model}`
  const cached = MODEL_COLOR_CACHE.get(key)
  if (cached) return cached
  const list = PROVIDER_MODEL_PALETTES[provider] ?? ["#64748b", "#94a3b8", "#cbd5e1", "#e2e8f0"]
  const idx = hashToUint(key) % list.length
  const picked = list[idx] ?? "#94a3b8"
  MODEL_COLOR_CACHE.set(key, picked)
  return picked
}

function labelForProviderCode(code: string): string {
  return PROVIDER_LABEL[code] ?? code
}

const HEX_6 = /^#?([0-9a-fA-F]{6})$/

function hexToRgb(hex: string): { r: number; g: number; b: number } | null {
  const m = hex.trim().match(HEX_6)
  if (!m) return null
  const n = parseInt(m[1]!, 16)
  return { r: (n >> 16) & 255, g: (n >> 8) & 255, b: n & 255 }
}

function rgbaFromHex(hex: string, alpha: number): string {
  const rgb = hexToRgb(hex)
  if (!rgb) return `rgba(115, 115, 115, ${alpha})`
  return `rgba(${rgb.r}, ${rgb.g}, ${rgb.b}, ${alpha})`
}

type TokenStackRow = {
  label: string
  model: string
  provider: string
  requests: number
  inputTokens: number
  estimatedReasoningTokens: number
  outputTokens: number
  totalTokens: number
  avgInputPerReq: number
  avgEstimatedReasoningPerReq: number
  avgOutputPerReq: number
  pctInputOfBar: number
  pctEstimatedReasoningOfBar: number
  pctOutputOfBar: number
  pctInputOfGrand: number
  pctEstimatedReasoningOfGrand: number
  pctOutputOfGrand: number
  fillInput: string
  fillEstimatedReasoning: string
  fillOutput: string
}

type ModelRequestRow = {
  label: string
  model: string
  provider: string
  requests: number
  isOthers: boolean
  members?: Array<{ model: string; provider: string; requests: number }>
}

const PLACEHOLDER_MODEL_BAR_ROW: ModelRequestRow[] = [
  { label: "—", model: "__empty__", provider: "GOOGLE", requests: 0, isOthers: false },
]

const EMPTY_TOKEN_STACK_DISPLAY_ROW: TokenStackRow = {
  label: "—",
  model: "__empty__",
  provider: "GOOGLE",
  requests: 0,
  inputTokens: 0,
  estimatedReasoningTokens: 0,
  outputTokens: 0,
  totalTokens: 0,
  avgInputPerReq: 0,
  avgEstimatedReasoningPerReq: 0,
  avgOutputPerReq: 0,
  pctInputOfBar: 0,
  pctEstimatedReasoningOfBar: 0,
  pctOutputOfBar: 0,
  pctInputOfGrand: 0,
  pctEstimatedReasoningOfGrand: 0,
  pctOutputOfGrand: 0,
  fillInput: "rgba(148, 163, 184, 0.3)",
  fillEstimatedReasoning: "rgba(148, 163, 184, 0.65)",
  fillOutput: "#94a3b8",
}

type TokenStackTooltipProps = {
  active?: boolean
  /** Recharts Tooltip passes `string | number` for category axis labels */
  label?: string | number
  /** Recharts passes `readonly TooltipPayload[]`, not a mutable array */
  payload?: readonly unknown[]
}

function TokenStackTooltip({ active, payload, label }: TokenStackTooltipProps) {
  if (!active || !payload?.length) return null
  const row = (payload[0] as { payload?: TokenStackRow }).payload
  if (!row) return null
  return (
    <div className="rounded-md border border-border bg-card px-3 py-2 text-xs shadow-md">
      <p className="font-medium text-foreground">{String(label)}</p>
      <p className="mt-1 text-muted-foreground">
        입력: {formatTokenCount(row.inputTokens)}{" "}
        <span className="tabular-nums">({row.pctInputOfBar.toFixed(1)}% 막대)</span>
        {" · "}
        <span className="tabular-nums">전체 {row.pctInputOfGrand.toFixed(1)}%</span>
      </p>
      <p className="mt-0.5 text-muted-foreground">
        추정 추론: {formatTokenCount(row.estimatedReasoningTokens)}{" "}
        <span className="tabular-nums">({row.pctEstimatedReasoningOfBar.toFixed(1)}% 막대)</span>
        {" · "}
        <span className="tabular-nums">전체 {row.pctEstimatedReasoningOfGrand.toFixed(1)}%</span>
      </p>
      <p className="mt-0.5 text-muted-foreground">
        출력: {formatTokenCount(row.outputTokens)}{" "}
        <span className="tabular-nums">({row.pctOutputOfBar.toFixed(1)}% 막대)</span>
        {" · "}
        <span className="tabular-nums">전체 {row.pctOutputOfGrand.toFixed(1)}%</span>
      </p>
      <p className="mt-1 text-[11px] text-muted-foreground tabular-nums">
        요청당 평균 — in {Math.round(row.avgInputPerReq).toLocaleString("en-US")} / reason{" "}
        {Math.round(row.avgEstimatedReasoningPerReq).toLocaleString("en-US")} / out{" "}
        {Math.round(row.avgOutputPerReq).toLocaleString("en-US")}
      </p>
    </div>
  )
}

type TokenAvgLabelProps = {
  x?: number | string
  y?: number | string
  width?: number | string
  height?: number | string
  payload?: TokenStackRow
}

function TokenAvgLabelList(props: TokenAvgLabelProps) {
  const { x, y, width, height, payload } = props
  if (payload == null || payload.totalTokens <= 0) return null
  const text = `in:${Math.round(payload.avgInputPerReq).toLocaleString("en-US")} / reason:${Math.round(
    payload.avgEstimatedReasoningPerReq
  ).toLocaleString("en-US")} / out:${Math.round(payload.avgOutputPerReq).toLocaleString("en-US")}`
  const nx = typeof x === "number" ? x : Number(x)
  const ny = typeof y === "number" ? y : Number(y)
  const nw = typeof width === "number" ? width : Number(width)
  const nh = typeof height === "number" ? height : Number(height)
  if (!Number.isFinite(nx) || !Number.isFinite(ny)) return null
  return (
    <text
      x={nx + nw + 6}
      y={ny + nh / 2}
      fill="var(--muted-foreground)"
      fontSize={10}
      dominantBaseline="middle"
    >
      {text.length > 42 ? `${text.slice(0, 41)}…` : text}
    </text>
  )
}

type MainStabilityRow = {
  label: string
  requestCount: number
  successCount: number
  errorCount: number
  successRate: number
  errorRate: number
}

type MainStabilityTooltipProps = {
  active?: boolean
  label?: string | number
  payload?: readonly unknown[]
}

function MainStabilityTooltip({ active, label, payload }: MainStabilityTooltipProps) {
  if (!active || !payload?.length) return null
  const row = (payload[0] as { payload?: MainStabilityRow }).payload
  if (!row) return null
  return (
    <div className="rounded-md border border-border bg-card px-3 py-2 text-xs shadow-md">
      <p className="font-medium text-foreground">{String(label)}</p>
      <p className="mt-1 text-muted-foreground">총 요청 수: {formatRequestCount(row.requestCount)}</p>
      <p className="text-muted-foreground">성공 건수: {row.successCount.toLocaleString("en-US")}건</p>
      <p className="text-muted-foreground">오류 건수: {row.errorCount.toLocaleString("en-US")}건</p>
      <p className="mt-1 text-foreground tabular-nums">성공률: {row.successRate.toFixed(1)}%</p>
    </div>
  )
}

type DonutTooltipPayload = {
  name?: string
  value?: number
  payload?: { fullName?: string; value?: number; percent?: number; provider?: string }
}

type SimpleDonutTooltipProps = {
  active?: boolean
  payload?: readonly unknown[]
}

function ModelDonutTooltip({ active, payload }: SimpleDonutTooltipProps) {
  if (!active || !payload?.length) return null
  const first = payload[0] as DonutTooltipPayload
  const raw = first.payload
  if (!raw) return null
  const name = raw.fullName ?? first.name ?? "-"
  const pct = raw.percent ?? 0
  return (
    <div className="rounded-md border border-border bg-card px-3 py-2 text-xs shadow-md">
      <p className="font-medium text-foreground">{name}</p>
      <p className="mt-1 text-muted-foreground tabular-nums">전체 대비 비중: {(pct * 100).toFixed(1)}%</p>
    </div>
  )
}

function ProviderDonutTooltip({ active, payload }: SimpleDonutTooltipProps) {
  if (!active || !payload?.length) return null
  const first = payload[0] as DonutTooltipPayload
  const raw = first.payload
  if (!raw) return null
  const name = first.name ?? raw.provider ?? "-"
  const count = raw.value ?? first.value ?? 0
  const pct = raw.percent ?? 0
  return (
    <div className="rounded-md border border-border bg-card px-3 py-2 text-xs shadow-md">
      <p className="font-medium text-foreground">{name}</p>
      <p className="mt-1 text-muted-foreground">총 요청 수: {formatRequestCount(count)}</p>
      <p className="text-muted-foreground tabular-nums">전체 대비 비중: {(pct * 100).toFixed(1)}%</p>
    </div>
  )
}

type ModelRequestBarTooltipProps = {
  active?: boolean
  label?: string | number
  payload?: readonly unknown[]
}

function ModelRequestBarTooltip({ active, label, payload }: ModelRequestBarTooltipProps) {
  if (!active || !payload?.length) return null
  const row = (payload[0] as { payload?: ModelRequestRow }).payload
  if (!row) return null
  const title = row.model === "__empty__" ? "—" : row.model
  return (
    <div className="rounded-md border border-border bg-card px-3 py-2 text-xs shadow-md">
      <p className="font-medium text-foreground">{title}</p>
      <p className="mt-1 text-muted-foreground">총 요청 수: {formatRequestCount(row.requests)}</p>
      {row.model !== "__empty__" && row.model !== "__OTHERS__" ? (
        <p className="text-muted-foreground">{labelForProviderCode(row.provider)}</p>
      ) : row.model === "__OTHERS__" ? (
        <p className="text-muted-foreground">{String(label)}</p>
      ) : null}
    </div>
  )
}

type MonthlyBarRow = { yearMonth: string; requestCount: number }

type MonthlyRequestBarTooltipProps = {
  active?: boolean
  label?: string | number
  payload?: readonly unknown[]
}

function MonthlyRequestBarTooltip({ active, label, payload }: MonthlyRequestBarTooltipProps) {
  if (!active || !payload?.length) return null
  const row = (payload[0] as { payload?: MonthlyBarRow }).payload
  const requests =
    row?.requestCount ?? tooltipNumericValue((payload[0] as { value?: unknown }).value)
  const ym = row?.yearMonth ?? String(label)
  return (
    <div className="rounded-md border border-border bg-card px-3 py-2 text-xs shadow-md">
      <p className="font-medium text-foreground">{ym}</p>
      <p className="mt-1 text-muted-foreground">총 요청 수: {formatRequestCount(requests)}</p>
    </div>
  )
}

function stabilityRateDomain(rows: MainStabilityRow[]): [number, number] {
  if (rows.length === 0) return [90, 100]
  const rates = rows
    .filter((r) => r.requestCount > 0)
    .flatMap((r) => [r.successRate, r.errorRate])
  if (rates.length === 0) return [90, 100]
  const min = Math.min(...rates)
  const max = Math.max(...rates)
  const paddedMin = Math.floor((min - 0.5) * 10) / 10
  const paddedMax = Math.ceil((max + 0.5) * 10) / 10
  const lower = min >= 90 ? Math.max(90, paddedMin) : Math.max(0, paddedMin)
  const upper = Math.min(100, Math.max(lower + 1, paddedMax))
  return [lower, upper]
}

function emptyHourlyStabilityRows(): MainStabilityRow[] {
  const rows: MainStabilityRow[] = []
  for (let h = 0; h < 24; h++) {
    rows.push({
      label: `${h}시`,
      requestCount: 0,
      successCount: 0,
      errorCount: 0,
      successRate: 0,
      errorRate: 0,
    })
  }
  return rows
}

function emptyDailyStabilityRows(fromIso: string, toIso: string): MainStabilityRow[] {
  const n = kstDaysInclusive(fromIso, toIso)
  const rows: MainStabilityRow[] = []
  for (let i = 0; i < n; i++) {
    const dateStr = addKstDays(fromIso, i)
    rows.push({
      label: dateStr,
      requestCount: 0,
      successCount: 0,
      errorCount: 0,
      successRate: 0,
      errorRate: 0,
    })
  }
  return rows
}

/** 월별 막대 차트 빈 상태에서 축·틀만 보이도록 하는 플레이스홀더 (종료 월 기준 12개월). */
function placeholderMonthlyChart(anchorDayIso: string, barCount = 12): { yearMonth: string; requestCount: number }[] {
  let y = Number(anchorDayIso.slice(0, 4))
  let m = Number(anchorDayIso.slice(5, 7))
  if (Number.isNaN(y) || Number.isNaN(m)) {
    const today = formatKstIsoDate()
    y = Number(today.slice(0, 4))
    m = Number(today.slice(5, 7))
  }
  const rows: { yearMonth: string; requestCount: number }[] = []
  for (let i = 0; i < barCount; i++) {
    rows.unshift({
      yearMonth: `${y}-${String(m).padStart(2, "0")}`,
      requestCount: 0,
    })
    m -= 1
    if (m < 1) {
      m = 12
      y -= 1
    }
  }
  return rows
}

function isAbortError(e: unknown): boolean {
  return (
    (typeof DOMException !== "undefined" && e instanceof DOMException && e.name === "AbortError") ||
    (e instanceof Error && e.name === "AbortError")
  )
}

const H_BAR_MARGIN = { left: 8, right: 16 }
/** 토큰 스택 막대: 행당 높이·차트 높이 상한 (가독성·스크롤 균형) */
const TOKEN_ROW_HEIGHT_PX = 36
const TOKEN_CHART_MIN_H = 280
const TOKEN_CHART_MAX_H = 520

export function UsageDashboard() {
  const [periodMode, setPeriodMode] = React.useState<PeriodMode>("today")
  const [customFrom, setCustomFrom] = React.useState("")
  const [customTo, setCustomTo] = React.useState("")
  const [dashProvider, setDashProvider] = React.useState<string>(DASHBOARD_PROVIDER_ALL)

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
  const [isOthersModalOpen, setIsOthersModalOpen] = React.useState(false)

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
    (): MainStabilityRow[] =>
      daily.map((row) => {
        const successCount = Math.max(0, row.requestCount - row.errorCount)
        const successRate = row.requestCount > 0 ? (100 * successCount) / row.requestCount : 0
        const errorRate = row.requestCount > 0 ? (100 * row.errorCount) / row.requestCount : 0
        return {
          label: row.date,
          requestCount: row.requestCount,
          successCount,
          errorCount: row.errorCount,
          successRate,
          errorRate,
        }
      }),
    [daily]
  )

  const hourlyChart = React.useMemo(
    (): MainStabilityRow[] =>
      (hourly ?? []).map((row) => ({
        label: `${row.hour}시`,
        requestCount: row.requestCount,
        successCount: Math.max(0, row.requestCount - row.errorCount),
        errorCount: row.errorCount,
        successRate: row.requestCount > 0 ? (100 * (row.requestCount - row.errorCount)) / row.requestCount : 0,
        errorRate: row.requestCount > 0 ? (100 * row.errorCount) / row.requestCount : 0,
      })),
    [hourly]
  )

  const mainStabilitySeries = React.useMemo((): MainStabilityRow[] => {
    if (periodMode === "today") {
      return hourlyChart.length > 0 ? hourlyChart : emptyHourlyStabilityRows()
    }
    return dailyChart.length > 0 ? dailyChart : emptyDailyStabilityRows(rangeFrom, rangeTo)
  }, [periodMode, hourlyChart, dailyChart, rangeFrom, rangeTo])

  const mainStabilityNoRequests = React.useMemo(() => {
    if (periodMode === "today") {
      return !(hourly ?? []).some((h) => h.requestCount > 0)
    }
    return !daily.some((d) => d.requestCount > 0)
  }, [periodMode, hourly, daily])

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
        percent: m.requestCount / totalReq,
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
    const total = [...acc.values()].reduce((s, v) => s + v, 0)
    return [...acc.entries()].map(([provider, value]) => ({
      name: labelForProviderCode(provider),
      provider,
      value,
      percent: total > 0 ? value / total : 0,
    }))
  }, [byModel])

  const modelPieChartData = React.useMemo(
    () =>
      pieData.length > 0
        ? pieData
        : [{ name: "—", fullName: "—", provider: "GOOGLE", value: 1, percent: 1 }],
    [pieData]
  )
  const isModelPiePlaceholder = pieData.length === 0

  const providerPieChartData = React.useMemo(
    () =>
      providerPieData.length > 0
        ? providerPieData
        : [{ name: "—", provider: "GOOGLE", value: 1, percent: 1 }],
    [providerPieData]
  )
  const isProviderPiePlaceholder = providerPieData.length === 0

  const modelPieLegendPayload = React.useMemo(
    () =>
      pieData.map((entry) => ({
        value: entry.name,
        type: "square" as const,
        color: colorForModel(entry.fullName, entry.provider),
      })),
    [pieData]
  )

  const providerPieLegendPayload = React.useMemo(
    () =>
      providerPieData.map((entry) => ({
        value: entry.name,
        type: "square" as const,
        color: PROVIDER_COLOR[entry.provider] ?? "#737373",
      })),
    [providerPieData]
  )

  const modelBarRows = React.useMemo((): ModelRequestRow[] => {
    const sorted = [...byModel]
      .filter((m) => m.requestCount > 0)
      .sort((a, b) => b.requestCount - a.requestCount)
    const top = sorted.slice(0, MODEL_REQUESTS_TOP_N).map((m) => ({
      label: truncateModelLabel(m.model),
      model: m.model,
      provider: m.provider,
      requests: m.requestCount,
      isOthers: false,
    }))
    const othersMembers = sorted.slice(MODEL_REQUESTS_TOP_N)
    if (othersMembers.length === 0) return top
    const othersRequests = othersMembers.reduce((sum, m) => sum + m.requestCount, 0)
    return [
      ...top,
      {
        label: OTHERS_LABEL,
        model: "__OTHERS__",
        provider: "OTHERS",
        requests: othersRequests,
        isOthers: true,
        members: othersMembers.map((m) => ({
          model: m.model,
          provider: m.provider,
          requests: m.requestCount,
        })),
      },
    ]
  }, [byModel])

  const othersRows = React.useMemo(
    () =>
      (modelBarRows.find((r) => r.isOthers)?.members ?? []).slice().sort((a, b) => b.requests - a.requests),
    [modelBarRows]
  )

  const modelBarDisplayRows = React.useMemo(
    () => (modelBarRows.length > 0 ? modelBarRows : PLACEHOLDER_MODEL_BAR_ROW),
    [modelBarRows]
  )

  React.useEffect(() => {
    if (!isOthersModalOpen) return
    const prev = document.body.style.overflow
    document.body.style.overflow = "hidden"
    return () => {
      document.body.style.overflow = prev
    }
  }, [isOthersModalOpen])

  const tokenStackRows = React.useMemo((): TokenStackRow[] => {
    const sorted = [...byModel].sort((a, b) => b.requestCount - a.requestCount)
    let grandIn = 0
    let grandEstimatedReasoning = 0
    let grandOut = 0
    for (const m of sorted) {
      grandIn += m.inputTokens
      grandEstimatedReasoning += m.estimatedReasoningTokens
      grandOut += m.outputTokens
    }
    const grandTotal = grandIn + grandEstimatedReasoning + grandOut
    return sorted.map((m) => {
      const base = colorForModel(m.model, m.provider)
      const rc = m.requestCount
      const inT = m.inputTokens
      const estimatedReasoningT = m.estimatedReasoningTokens
      const outT = m.outputTokens
      const total = inT + estimatedReasoningT + outT
      return {
        label: truncateModelLabel(m.model),
        model: m.model,
        provider: m.provider,
        requests: rc,
        inputTokens: inT,
        estimatedReasoningTokens: estimatedReasoningT,
        outputTokens: outT,
        totalTokens: total,
        avgInputPerReq: rc > 0 ? inT / rc : 0,
        avgEstimatedReasoningPerReq: rc > 0 ? estimatedReasoningT / rc : 0,
        avgOutputPerReq: rc > 0 ? outT / rc : 0,
        pctInputOfBar: total > 0 ? (100 * inT) / total : 0,
        pctEstimatedReasoningOfBar: total > 0 ? (100 * estimatedReasoningT) / total : 0,
        pctOutputOfBar: total > 0 ? (100 * outT) / total : 0,
        pctInputOfGrand: grandTotal > 0 ? (100 * inT) / grandTotal : 0,
        pctEstimatedReasoningOfGrand: grandTotal > 0 ? (100 * estimatedReasoningT) / grandTotal : 0,
        pctOutputOfGrand: grandTotal > 0 ? (100 * outT) / grandTotal : 0,
        fillInput: rgbaFromHex(base, 0.3),
        fillEstimatedReasoning: rgbaFromHex(base, 0.65),
        fillOutput: rgbaFromHex(base, 1),
      }
    })
  }, [byModel])

  const tokenStackDisplayRows = React.useMemo(
    () => (tokenStackRows.length > 0 ? tokenStackRows : [EMPTY_TOKEN_STACK_DISPLAY_ROW]),
    [tokenStackRows]
  )

  const tokenStackChartHeight = React.useMemo(() => {
    const n = tokenStackDisplayRows.length
    return Math.min(TOKEN_CHART_MAX_H, Math.max(TOKEN_CHART_MIN_H, n * TOKEN_ROW_HEIGHT_PX))
  }, [tokenStackDisplayRows.length])

  const tokenStackLegendPayload = React.useMemo(() => {
    const first = tokenStackDisplayRows[0]
    return [
      {
        value: "입력 토큰",
        type: "square" as const,
        color: first?.fillInput ?? "rgba(148, 163, 184, 0.3)",
      },
      {
        value: "추정 추론 토큰",
        type: "square" as const,
        color: first?.fillEstimatedReasoning ?? "rgba(148, 163, 184, 0.65)",
      },
      {
        value: "출력 토큰",
        type: "square" as const,
        color: first?.fillOutput ?? "#94a3b8",
      },
    ]
  }, [tokenStackDisplayRows])

  const hasMainData =
    (kpiSummary && kpiSummary.totalRequests > 0) ||
    daily.length > 0 ||
    monthly.length > 0 ||
    byModel.some((m) => m.requestCount > 0) ||
    (hourly && hourly.some((h) => h.requestCount > 0))

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

  const monthlyBarDisplayData = React.useMemo(
    () =>
      monthlyChart.length > 0 && monthlyHasActivity
        ? monthlyChart
        : placeholderMonthlyChart(rangeTo),
    [monthlyChart, monthlyHasActivity, rangeTo]
  )

  const mainChartTitle =
    periodMode === "today" ? "시간별 요청·성공률·오류율 (오늘 KST)" : "일별 요청·성공률·오류율 (선택 기간)"

  const rateAxisDomain = React.useMemo(
    () => stabilityRateDomain(mainStabilitySeries),
    [mainStabilitySeries]
  )

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

          <section className="mb-8 rounded-lg border border-border p-4 shadow-sm">
            <h2 className="mb-4 text-lg font-medium">{mainChartTitle}</h2>
            <div className="h-[380px] min-h-[380px] w-full min-w-0">
              <ResponsiveContainer width="100%" height="100%">
                <ComposedChart data={mainStabilitySeries}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                  <XAxis dataKey="label" tick={{ fontSize: 11 }} />
                  <YAxis
                    yAxisId="left"
                    tick={{ fontSize: 11 }}
                    label={{ value: "요청 수 (건)", angle: -90, position: "insideLeft", offset: 2 }}
                  />
                  <YAxis
                    yAxisId="right"
                    orientation="right"
                    domain={rateAxisDomain}
                    tick={{ fontSize: 11 }}
                    tickFormatter={(v) => `${Number(v).toFixed(1)}%`}
                    label={{ value: "성공/오류율 (%)", angle: 90, position: "insideRight", offset: 2 }}
                  />
                  <Tooltip content={MainStabilityTooltip} />
                  <AnyLegend />
                  <Bar
                    yAxisId="left"
                    dataKey="requestCount"
                    name="총 요청 수"
                    fill="#a3a3a3"
                    radius={[4, 4, 0, 0]}
                  />
                  <Line
                    yAxisId="right"
                    type="monotone"
                    dataKey="successRate"
                    name="성공률"
                    stroke="#10b981"
                    strokeWidth={2}
                    dot={false}
                  />
                  <Line
                    yAxisId="right"
                    type="monotone"
                    dataKey="errorRate"
                    name="오류율"
                    stroke="#f43f5e"
                    strokeWidth={2}
                    dot={false}
                  />
                </ComposedChart>
              </ResponsiveContainer>
            </div>
            {mainStabilityNoRequests ? (
              <p className="mt-2 text-center text-sm text-muted-foreground">집계 데이터 없음</p>
            ) : null}
          </section>

          <div className="mb-8 grid gap-5 lg:grid-cols-2 lg:gap-6">
            <section className="rounded-lg border border-border p-4 shadow-sm">
              <h2 className="mb-4 text-lg font-medium">모델별 요청 비중</h2>
              <div className="h-[320px] min-h-[320px] w-full min-w-0">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={modelPieChartData}
                      dataKey="value"
                      nameKey="name"
                      cx="50%"
                      cy="50%"
                      innerRadius="52%"
                      outerRadius="80%"
                      paddingAngle={2}
                      isAnimationActive={!isModelPiePlaceholder}
                      label={
                        isModelPiePlaceholder
                          ? false
                          : ({ name, percent }) => `${name} (${((percent ?? 0) * 100).toFixed(0)}%)`
                      }
                    >
                      {modelPieChartData.map((entry, i) => (
                        <Cell
                          key={`m-${entry.fullName}-${i}`}
                          fill={
                            isModelPiePlaceholder
                              ? "var(--border)"
                              : colorForModel(entry.fullName, entry.provider)
                          }
                          fillOpacity={isModelPiePlaceholder ? 0.35 : 1}
                          style={{ cursor: "default" }}
                        />
                      ))}
                    </Pie>
                    <Tooltip content={ModelDonutTooltip} />
                    {!isModelPiePlaceholder ? <AnyLegend payload={modelPieLegendPayload} /> : null}
                  </PieChart>
                </ResponsiveContainer>
              </div>
              {isModelPiePlaceholder ? (
                <p className="mt-2 text-center text-sm text-muted-foreground">집계 데이터 없음</p>
              ) : null}
            </section>

            <section className="rounded-lg border border-border p-4 shadow-sm">
              <h2 className="mb-4 text-lg font-medium">공급사별 요청 비중</h2>
              <div className="h-[320px] min-h-[320px] w-full min-w-0">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={providerPieChartData}
                      dataKey="value"
                      nameKey="name"
                      cx="50%"
                      cy="50%"
                      innerRadius="52%"
                      outerRadius="80%"
                      paddingAngle={2}
                      isAnimationActive={!isProviderPiePlaceholder}
                      label={
                        isProviderPiePlaceholder
                          ? false
                          : ({ name, percent }) => `${name} (${((percent ?? 0) * 100).toFixed(0)}%)`
                      }
                    >
                      {providerPieChartData.map((entry, i) => (
                        <Cell
                          key={`p-${entry.provider}-${i}`}
                          fill={
                            isProviderPiePlaceholder
                              ? "var(--border)"
                              : PROVIDER_COLOR[entry.provider] ?? "#737373"
                          }
                          fillOpacity={isProviderPiePlaceholder ? 0.35 : 1}
                          style={{ cursor: "default" }}
                        />
                      ))}
                    </Pie>
                    <Tooltip content={ProviderDonutTooltip} />
                    {!isProviderPiePlaceholder ? <AnyLegend payload={providerPieLegendPayload} /> : null}
                  </PieChart>
                </ResponsiveContainer>
              </div>
              {isProviderPiePlaceholder ? (
                <p className="mt-2 text-center text-sm text-muted-foreground">집계 데이터 없음</p>
              ) : null}
            </section>

            <section className="rounded-lg border border-border p-4 shadow-sm lg:col-span-2">
              <h2 className="mb-4 text-lg font-medium">모델별 요청 수 (가로)</h2>
              <div className="h-[320px] min-h-[320px] w-full max-w-full min-w-0">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart layout="vertical" data={modelBarDisplayRows} margin={H_BAR_MARGIN}>
                    <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                    <XAxis type="number" />
                    <YAxis type="category" dataKey="label" width={128} tick={{ fontSize: 11 }} />
                    <Tooltip content={ModelRequestBarTooltip} />
                    <Bar
                      dataKey="requests"
                      name="요청 수"
                      radius={[0, 4, 4, 0]}
                      isAnimationActive={false}
                      onClick={(_, index) => {
                        if (modelBarRows.length === 0) return
                        const row = modelBarDisplayRows[index]
                        if (row?.isOthers) setIsOthersModalOpen(true)
                      }}
                    >
                      {modelBarDisplayRows.map((row) => (
                        <Cell
                          key={`req-${row.model}`}
                          fill={
                            modelBarRows.length === 0
                              ? "var(--border)"
                              : row.isOthers
                                ? OTHERS_BAR_COLOR
                                : colorForModel(row.model, row.provider)
                          }
                          fillOpacity={modelBarRows.length === 0 ? 0.35 : 1}
                          style={{ cursor: row.isOthers ? "pointer" : "default" }}
                        />
                      ))}
                      <LabelList
                        dataKey="requests"
                        position="right"
                        formatter={(value: unknown) => formatRequestCount(tooltipNumericValue(value))}
                        style={{ fill: "var(--muted-foreground)", fontSize: 11 }}
                      />
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </div>
              {modelBarRows.length === 0 ? (
                <p className="mt-2 text-center text-sm text-muted-foreground">집계 데이터 없음</p>
              ) : null}
              {modelBarRows.length > 0 ? (
                <p className="mt-3 text-xs text-muted-foreground">
                  기타 막대를 클릭하여 전체 상세 내역을 확인하세요.
                </p>
              ) : null}
            </section>
          </div>

          <section className="mb-8 rounded-lg border border-border bg-card p-4 shadow-sm min-w-0">
            <h2 className="mb-4 text-lg font-medium">모델별 토큰 사용량</h2>
            <>
              <div
                className="w-full max-w-full min-w-0"
                style={{ height: tokenStackChartHeight, minHeight: tokenStackChartHeight }}
              >
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart
                    layout="vertical"
                    data={tokenStackDisplayRows}
                    margin={{ top: 8, left: 8, right: 120, bottom: 8 }}
                  >
                    <CartesianGrid
                      strokeDasharray="3 3"
                      stroke="var(--border)"
                      strokeOpacity={0.85}
                    />
                    <XAxis type="number" tick={{ fontSize: 11 }} tickCount={8} />
                    <YAxis type="category" dataKey="label" width={128} tick={{ fontSize: 11 }} />
                    <Tooltip content={TokenStackTooltip} cursor={{ fill: "var(--muted)", fillOpacity: 0.12 }} />
                    <AnyLegend payload={tokenStackLegendPayload} />
                    <Bar stackId="tokens" dataKey="inputTokens" name="입력 토큰" radius={[4, 0, 0, 4]}>
                      {tokenStackDisplayRows.map((row) => (
                        <Cell
                          key={`stk-in-${row.model}`}
                          fill={row.fillInput}
                          fillOpacity={tokenStackRows.length === 0 ? 0.35 : 1}
                        />
                      ))}
                    </Bar>
                    <Bar stackId="tokens" dataKey="estimatedReasoningTokens" name="추정 추론 토큰">
                      {tokenStackDisplayRows.map((row) => (
                        <Cell
                          key={`stk-reason-${row.model}`}
                          fill={row.fillEstimatedReasoning}
                          fillOpacity={tokenStackRows.length === 0 ? 0.35 : 1}
                        />
                      ))}
                    </Bar>
                    <Bar stackId="tokens" dataKey="outputTokens" name="출력 토큰" radius={[0, 4, 4, 0]}>
                      {tokenStackDisplayRows.map((row) => (
                        <Cell
                          key={`stk-out-${row.model}`}
                          fill={row.fillOutput}
                          fillOpacity={tokenStackRows.length === 0 ? 0.35 : 1}
                        />
                      ))}
                      <LabelList
                        position="right"
                        content={(p: unknown) => (
                          <TokenAvgLabelList {...(p as TokenAvgLabelProps)} />
                        )}
                      />
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </div>
              {tokenStackRows.length === 0 ? (
                <p className="mt-2 text-center text-sm text-muted-foreground">집계 데이터 없음</p>
              ) : null}
              {tokenStackRows.length > 0 ? (
                <p className="mt-3 text-xs text-muted-foreground">
                  ※ &apos;추정 추론 토큰&apos;은 API에서 별도로 구분되지 않는 시스템/추론 토큰의 합산 추정치입니다.
                </p>
              ) : null}
            </>
          </section>

          <section className="mb-8 rounded-lg border border-border p-4 shadow-sm">
            <h2 className="mb-4 text-lg font-medium">월별 요청 수 (누적 추이)</h2>
            <div className="h-[360px] min-h-[360px] w-full min-w-0">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={monthlyBarDisplayData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                  <XAxis dataKey="yearMonth" tick={{ fontSize: 11 }} />
                  <YAxis tick={{ fontSize: 11 }} />
                  <Tooltip content={MonthlyRequestBarTooltip} />
                  <AnyLegend />
                  <Bar
                    dataKey="requestCount"
                    name="요청 수"
                    fill={monthlyHasActivity ? "#64748b" : "var(--border)"}
                    fillOpacity={monthlyHasActivity ? 1 : 0.35}
                    radius={[4, 4, 0, 0]}
                  />
                </BarChart>
              </ResponsiveContainer>
            </div>
            {!monthlyHasActivity ? (
              <p className="mt-2 text-center text-sm text-muted-foreground">집계 데이터 없음</p>
            ) : null}
          </section>
        </>
      )}
      {isOthersModalOpen ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4"
          onClick={() => setIsOthersModalOpen(false)}
          role="presentation"
        >
          <div
            className="w-full max-w-2xl rounded-lg border border-border bg-card shadow-xl"
            role="dialog"
            aria-modal="true"
            aria-label="기타 모델 상세 내역"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between border-b border-border px-4 py-3">
              <h3 className="text-sm font-semibold">기타 모델 상세 내역</h3>
              <Button type="button" variant="outline" size="sm" onClick={() => setIsOthersModalOpen(false)}>
                닫기
              </Button>
            </div>
            <div className="max-h-[60vh] overflow-y-auto px-4 py-3">
              {othersRows.length === 0 ? (
                <p className="text-sm text-muted-foreground">표시할 상세 항목이 없습니다.</p>
              ) : (
                <div className="space-y-2">
                  {othersRows.map((row) => (
                    <div
                      key={`${row.provider}:${row.model}`}
                      className="flex items-center justify-between rounded-md border border-border/70 px-3 py-2 text-sm"
                    >
                      <div className="min-w-0">
                        <p className="truncate font-medium">{row.model}</p>
                        <p className="text-xs text-muted-foreground">{labelForProviderCode(row.provider)}</p>
                      </div>
                      <p className="tabular-nums text-muted-foreground">{formatRequestCount(row.requests)}</p>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      ) : null}
    </div>
  )
}
