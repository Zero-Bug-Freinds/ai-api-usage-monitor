"use client"

import { useMemo } from "react"
import {
  Bar,
  BarChart,
  CartesianGrid,
  ComposedChart,
  Line,
  ReferenceLine,
  ResponsiveContainer,
  Scatter,
  ScatterChart,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts"
import { TeamMemberAvatar } from "@/components/common/team-member-avatar"
import { formatRequestCount } from "@/lib/usage/format"
import { colorForModel } from "@/lib/usage/model-colors"

const MEMBER_MODEL_TOP = 4
const OTHERS_STACK_KEY = "__OTHERS__"
const OTHERS_COLOR = "#D1D5DB"
const MEMBER_AXIS_COMPACT_THRESHOLD = 12
const CHART_MIN_HEIGHT_PX = 320
/** Matches YAxis width; foreignObject uses same width and x offset -(width + 8). */
const MEMBER_Y_AXIS_LABEL_WIDTH_COMPACT_PX = 80
const MEMBER_Y_AXIS_LABEL_WIDTH_DEFAULT_PX = 100
/** Avatar 16px + gap + one text line; centered on Recharts category band midpoint. */
const MEMBER_Y_AXIS_TICK_HEIGHT_PX = 28

export type MemberModelAgg = {
  model: string
  provider: string
  requestCount: number
  inputTokens?: number
  outputTokens?: number
  estimatedReasoningTokens?: number
}

export type MemberUsageSummary = {
  totalRequests: number
  totalErrors: number
  totalInputTokens: number
  totalEstimatedCost?: number
  avgLatencyMs?: number | null
}

export type MemberProfileBrief = {
  userId: string
  displayName?: string
  role?: string
}

export type MemberRow = {
  profile: MemberProfileBrief
  byModel: MemberModelAgg[]
  summary?: MemberUsageSummary
}

function sumByModelRequests(byModel: MemberModelAgg[]): number {
  return byModel.reduce((s, m) => s + Math.max(0, m.requestCount), 0)
}

function totalRequestsForMember(row: MemberRow): number {
  const s = row.summary?.totalRequests
  if (typeof s === "number" && s > 0) return s
  return sumByModelRequests(row.byModel)
}

function totalTokensFromByModel(byModel: MemberModelAgg[]): number {
  let t = 0
  for (const m of byModel) {
    const it = typeof m.inputTokens === "number" ? m.inputTokens : 0
    const ot = typeof m.outputTokens === "number" ? m.outputTokens : 0
    const rt = typeof m.estimatedReasoningTokens === "number" ? m.estimatedReasoningTokens : 0
    t += it + ot + rt
  }
  return t
}

function segmentKey(provider: string, model: string): string {
  return `${provider}::${model}`
}

function parseSegmentKey(key: string): { provider: string; model: string } {
  const i = key.indexOf("::")
  if (i < 0) return { provider: "", model: key }
  return { provider: key.slice(0, i), model: key.slice(i + 2) }
}

type StackedMemberRow = {
  userId: string
  displayName: string
  segmentsForTooltip: Array<{ modelKey: string; model: string; provider: string; requests: number }>
} & Record<string, number | string | unknown>

function buildModelShareChartData(memberRows: MemberRow[]): {
  rows: StackedMemberRow[]
  stackKeys: string[]
} {
  type Seg = { key: string; model: string; provider: string; requests: number }
  const perMember: { userId: string; displayName: string; segments: Seg[]; tooltipList: Seg[] }[] = []
  const teamKeyTotals = new Map<string, number>()

  for (const row of memberRows) {
    const userId = row.profile.userId
    const displayName = row.profile.displayName?.trim() || userId
    const sorted = [...row.byModel]
      .map((m) => ({
        key: segmentKey(m.provider, m.model),
        model: m.model,
        provider: m.provider,
        requests: Math.max(0, m.requestCount),
      }))
      .filter((x) => x.requests > 0)
      .sort((a, b) => b.requests - a.requests)

    const tooltipList = sorted.map((s) => ({ ...s, modelKey: s.key }))
    const top = sorted.slice(0, MEMBER_MODEL_TOP)
    const rest = sorted.slice(MEMBER_MODEL_TOP)
    const othersReq = rest.reduce((s, x) => s + x.requests, 0)
    const segments: Seg[] = [...top]
    if (othersReq > 0) {
      segments.push({
        key: OTHERS_STACK_KEY,
        model: "기타 (Others)",
        provider: "OTHERS",
        requests: othersReq,
      })
    }

    for (const s of segments) {
      teamKeyTotals.set(s.key, (teamKeyTotals.get(s.key) ?? 0) + s.requests)
    }
    perMember.push({ userId, displayName, segments, tooltipList })
  }

  const stackKeys = [...teamKeyTotals.entries()]
    .sort((a, b) => b[1] - a[1])
    .map(([k]) => k)

  const rows: StackedMemberRow[] = perMember.map((pm) => {
    const total = pm.segments.reduce((s, x) => s + x.requests, 0)
    const pctByKey = new Map<string, number>()
    if (total <= 0) {
      for (const k of stackKeys) pctByKey.set(k, 0)
    } else {
      let acc = 0
      for (let i = 0; i < pm.segments.length; i++) {
        const s = pm.segments[i]!
        const isLast = i === pm.segments.length - 1
        if (isLast) {
          pctByKey.set(s.key, Math.max(0, 100 - acc))
        } else {
          const raw = (100 * s.requests) / total
          const rounded = Math.round(raw * 100) / 100
          pctByKey.set(s.key, rounded)
          acc += rounded
        }
      }
    }

    const out: StackedMemberRow = {
      userId: pm.userId,
      displayName: pm.displayName,
      segmentsForTooltip: pm.tooltipList.map((t) => ({
        modelKey: t.key,
        model: t.model,
        provider: t.provider,
        requests: t.requests,
      })),
    }
    for (const k of stackKeys) {
      out[k] = pctByKey.get(k) ?? 0
    }
    return out
  })

  return { rows, stackKeys }
}

function stackKeyColor(key: string): string {
  if (key === OTHERS_STACK_KEY) return OTHERS_COLOR
  const { provider, model } = parseSegmentKey(key)
  return colorForModel(model, provider)
}

type TooltipContentArgs<TPayload> = {
  active?: boolean
  payload?: ReadonlyArray<{ payload?: TPayload }>
}

function ModelShareTooltip({ active, payload }: TooltipContentArgs<StackedMemberRow>) {
  if (!active || !payload?.length) return null
  const row = payload[0]?.payload as StackedMemberRow | undefined
  if (!row?.segmentsForTooltip) return null
  const sorted = [...row.segmentsForTooltip].sort((a, b) => b.requests - a.requests)
  return (
    <div className="rounded-md border border-border bg-card px-3 py-2 text-xs shadow-sm max-h-64 overflow-y-auto">
      <p className="font-semibold text-foreground">{row.displayName}</p>
      <p className="mt-1 text-muted-foreground">모델별 요청 수</p>
      <ul className="mt-1 space-y-0.5">
        {sorted.map((s) => (
          <li key={s.modelKey} className="flex justify-between gap-4 tabular-nums">
            <span className="min-w-0 truncate" title={`${s.provider} ${s.model}`}>
              {s.provider} · {s.model}
            </span>
            <span>{formatRequestCount(s.requests)}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}

function MemberYAxisTick(props: {
  // Recharts tick payload may provide string/number depending on formatter pipeline.
  x?: number | string
  y?: number | string
  payload?: { value?: string | number }
  rowsByUserId: Map<string, StackedMemberRow>
  compact: boolean
  labelWidth: number
}) {
  const { x = 0, y = 0, payload, rowsByUserId, compact, labelWidth } = props
  const userId = String(payload?.value ?? "")
  const row = rowsByUserId.get(userId)
  const label = row?.displayName ?? userId
  const short = compact && label.length > 10 ? `${label.slice(0, 9)}…` : label
  const foY = -MEMBER_Y_AXIS_TICK_HEIGHT_PX / 2
  const foX = -(labelWidth + 8)
  return (
    <g transform={`translate(${x},${y})`}>
      <foreignObject
        x={foX}
        y={foY}
        width={labelWidth}
        height={MEMBER_Y_AXIS_TICK_HEIGHT_PX}
        className="overflow-visible"
      >
        <div
          xmlns="http://www.w3.org/1999/xhtml"
          className="flex h-full w-full items-center gap-1.5 pr-1"
          style={{ fontSize: 11, lineHeight: 1.2 }}
        >
          <TeamMemberAvatar userId={userId} size={16} className="ring-0" />
          <span className="min-w-0 flex-1 truncate text-foreground" title={label}>
            {short}
          </span>
        </div>
      </foreignObject>
    </g>
  )
}

function MemberModelShareChart({ memberRows }: { memberRows: MemberRow[] }) {
  const { rows, stackKeys } = useMemo(() => buildModelShareChartData(memberRows), [memberRows])
  const rowsByUserId = useMemo(() => new Map(rows.map((r) => [r.userId, r])), [rows])
  const compact = memberRows.length > MEMBER_AXIS_COMPACT_THRESHOLD
  const yAxisLabelWidth = compact ? MEMBER_Y_AXIS_LABEL_WIDTH_COMPACT_PX : MEMBER_Y_AXIS_LABEL_WIDTH_DEFAULT_PX

  if (rows.length === 0) return null

  return (
    <section className="rounded-lg border border-border p-4 shadow-sm">
      <h3 className="mb-3 text-base font-medium">멤버별 모델 비중 (100% 누적)</h3>
      <div className="h-[360px] min-h-[320px] w-full" style={{ minHeight: CHART_MIN_HEIGHT_PX }}>
        <ResponsiveContainer width="100%" height="100%">
          <BarChart layout="vertical" data={rows} margin={{ top: 8, right: 16, left: 8, bottom: 8 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
            <XAxis type="number" domain={[0, 100]} tick={{ fontSize: 11 }} tickFormatter={(v) => `${v}%`} />
            <YAxis
              type="category"
              dataKey="userId"
              width={yAxisLabelWidth}
              interval={0}
              tick={(p) => (
                <MemberYAxisTick
                  {...p}
                  rowsByUserId={rowsByUserId}
                  compact={compact}
                  labelWidth={yAxisLabelWidth}
                />
              )}
            />
            <Tooltip content={<ModelShareTooltip />} cursor={{ fill: "transparent" }} />
            {stackKeys.map((k) => (
              <Bar
                key={k}
                dataKey={k}
                stackId="mix"
                fill={stackKeyColor(k)}
                isAnimationActive={false}
                name={k === OTHERS_STACK_KEY ? "기타 (Others)" : parseSegmentKey(k).model}
              />
            ))}
          </BarChart>
        </ResponsiveContainer>
      </div>
    </section>
  )
}

type ScatterPoint = {
  userId: string
  displayName: string
  totalRequests: number
  avgTokensPerReq: number
}

function buildScatterData(memberRows: MemberRow[]): ScatterPoint[] {
  const out: ScatterPoint[] = []
  for (const row of memberRows) {
    const totalRequests = totalRequestsForMember(row)
    if (totalRequests <= 0) continue
    const tokens = totalTokensFromByModel(row.byModel)
    const denom = Math.max(1, totalRequests)
    const avgTokensPerReq = tokens / denom
    const userId = row.profile.userId
    out.push({
      userId,
      displayName: row.profile.displayName?.trim() || userId,
      totalRequests,
      avgTokensPerReq,
    })
  }
  return out
}

function ScatterTooltip({ active, payload }: TooltipContentArgs<ScatterPoint>) {
  if (!active || !payload?.length) return null
  const p = payload[0]?.payload as ScatterPoint | undefined
  if (!p) return null
  return (
    <div className="rounded-md border border-border bg-card px-3 py-2 text-xs shadow-sm">
      <div className="flex items-center gap-2">
        <TeamMemberAvatar userId={p.userId} size={28} />
        <div>
          <p className="font-semibold text-foreground">{p.displayName}</p>
          <p className="text-muted-foreground">요청 수 {formatRequestCount(p.totalRequests)}</p>
          <p className="text-muted-foreground">평균 총 토큰/요청 {Math.round(p.avgTokensPerReq).toLocaleString()}</p>
        </div>
      </div>
    </div>
  )
}

function ScatterAvatarShape(props: { cx?: number; cy?: number; payload?: ScatterPoint }) {
  const { cx, cy, payload } = props
  if (cx == null || cy == null || !payload) return null
  const size = 28
  const half = size / 2
  return (
    <g transform={`translate(${cx - half},${cy - half})`}>
      <foreignObject x={0} y={0} width={size} height={size} className="overflow-visible">
        <div
          xmlns="http://www.w3.org/1999/xhtml"
          className="flex h-full w-full items-center justify-center overflow-hidden rounded-full bg-muted"
        >
          <TeamMemberAvatar userId={payload.userId} size={size} className="ring-0" />
        </div>
      </foreignObject>
    </g>
  )
}

function MemberTokenScatterChart({ memberRows }: { memberRows: MemberRow[] }) {
  const data = useMemo(() => buildScatterData(memberRows), [memberRows])

  if (data.length === 0) {
    return (
      <section className="rounded-lg border border-border p-4 shadow-sm">
        <h3 className="mb-3 text-base font-medium">토큰 효율성 (산점도)</h3>
        <p className="text-sm text-muted-foreground">표시할 요청 데이터가 없습니다.</p>
      </section>
    )
  }

  return (
    <section className="rounded-lg border border-border p-4 shadow-sm">
      <h3 className="mb-3 text-base font-medium">토큰 효율성 (산점도)</h3>
      <p className="mb-2 text-xs text-muted-foreground">X: 요청 수 · Y: 요청당 평균 총 토큰 (입력+출력+추론 추정 합 / 요청 수)</p>
      <div className="h-[360px] min-h-[320px] w-full" style={{ minHeight: CHART_MIN_HEIGHT_PX }}>
        <ResponsiveContainer width="100%" height="100%">
          <ScatterChart margin={{ top: 8, right: 16, left: 8, bottom: 8 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
            <XAxis type="number" dataKey="totalRequests" name="요청 수" tick={{ fontSize: 11 }} />
            <YAxis type="number" dataKey="avgTokensPerReq" name="평균 토큰" tick={{ fontSize: 11 }} />
            <Tooltip content={<ScatterTooltip />} cursor={{ stroke: "transparent" }} />
            <Scatter data={data} shape={<ScatterAvatarShape />} isAnimationActive={false} />
          </ScatterChart>
        </ResponsiveContainer>
      </div>
    </section>
  )
}

type ComboRow = {
  userId: string
  displayName: string
  /** Bar height; omitted when latency is unknown so the segment is skipped. */
  avgLatencyMsBar?: number
  avgLatencyMs: number | null
  successRate: number
}

function buildComboData(memberRows: MemberRow[], manyMembers: boolean): ComboRow[] {
  return memberRows.map((row) => {
    const userId = row.profile.userId
    let label = row.profile.displayName?.trim() || userId
    if (manyMembers && label.length > 8) {
      label = `${label.slice(0, 7)}…`
    }
    const tr = totalRequestsForMember(row)
    const te = typeof row.summary?.totalErrors === "number" ? row.summary.totalErrors : 0
    const successRate = tr > 0 ? ((tr - te) / tr) * 100 : 0
    const raw = row.summary?.avgLatencyMs
    const avgLatencyMs = typeof raw === "number" && Number.isFinite(raw) ? raw : null
    return {
      userId,
      displayName: label,
      avgLatencyMsBar: avgLatencyMs != null ? avgLatencyMs : undefined,
      avgLatencyMs,
      successRate,
    }
  })
}

function ComboTooltip({
  active,
  payload,
  memberNameById,
}: TooltipContentArgs<ComboRow> & { memberNameById: Record<string, string> }) {
  if (!active || !payload?.length) return null
  const p = payload[0]?.payload as ComboRow | undefined
  if (!p) return null
  const fullName = memberNameById[p.userId] ?? p.displayName
  const lat = p.avgLatencyMs
  return (
    <div className="rounded-md border border-border bg-card px-3 py-2 text-xs shadow-sm">
      <div className="flex items-center gap-2">
        <TeamMemberAvatar userId={p.userId} size={28} />
        <div>
          <p className="font-semibold text-foreground">{fullName}</p>
          <p className="text-muted-foreground">
            평균 지연: {lat != null ? `${Math.round(lat).toLocaleString()} ms` : "측정 없음 (latency 미기록)"}
          </p>
          <p className="text-muted-foreground">성공률: {p.successRate.toFixed(1)}%</p>
        </div>
      </div>
    </div>
  )
}

function MemberPerfComboChart({
  memberRows,
  memberNameById,
}: {
  memberRows: MemberRow[]
  memberNameById: Record<string, string>
}) {
  const manyMembers = memberRows.length > MEMBER_AXIS_COMPACT_THRESHOLD
  const data = useMemo(() => buildComboData(memberRows, manyMembers), [memberRows, manyMembers])
  const hasAnyLatency = data.some((d) => d.avgLatencyMs != null)

  return (
    <section className="rounded-lg border border-border p-4 shadow-sm">
      <h3 className="mb-3 text-base font-medium">성능 (지연 · 성공률)</h3>
      <p className="mb-2 text-xs text-muted-foreground">
        막대: 평균 지연(ms, 로그 latency 기준) · 선: 성공률 % · 점선: 2000 ms 참고선.
        {!hasAnyLatency ? " 현재 기간에 latency가 없으면 막대가 비어 있을 수 있습니다." : null}
      </p>
      <div className="h-[380px] min-h-[340px] w-full">
        <ResponsiveContainer width="100%" height="100%">
          <ComposedChart data={data} margin={{ top: 8, right: 16, left: 8, bottom: manyMembers ? 96 : 72 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
            <XAxis dataKey="displayName" tick={{ fontSize: 10 }} angle={-90} textAnchor="end" interval={0} height={manyMembers ? 88 : 72} />
            <YAxis yAxisId="left" tick={{ fontSize: 11 }} label={{ value: "ms", angle: -90, position: "insideLeft" }} />
            <YAxis yAxisId="right" orientation="right" domain={[0, 100]} tick={{ fontSize: 11 }} label={{ value: "%", angle: 90, position: "insideRight" }} />
            <Tooltip content={<ComboTooltip memberNameById={memberNameById} />} cursor={{ stroke: "transparent", fill: "transparent" }} />
            <ReferenceLine yAxisId="left" y={2000} stroke="#ef4444" strokeDasharray="4 4" />
            {hasAnyLatency ? (
              <Bar
                yAxisId="left"
                dataKey="avgLatencyMsBar"
                name="평균 지연(ms)"
                fill="#64748b"
                fillOpacity={0.7}
                isAnimationActive={false}
                radius={[2, 2, 0, 0]}
              />
            ) : null}
            <Line
              yAxisId="right"
              type="monotone"
              dataKey="successRate"
              name="성공률 %"
              stroke="#22c55e"
              strokeWidth={2}
              dot={{ r: 3 }}
              isAnimationActive={false}
            />
          </ComposedChart>
        </ResponsiveContainer>
      </div>
    </section>
  )
}

export function MemberAnalyticsCharts({
  memberRows,
  memberNameById,
}: {
  memberRows: MemberRow[]
  memberNameById: Record<string, string>
}) {
  return (
    <div className="space-y-8">
      <MemberModelShareChart memberRows={memberRows} />
      <MemberTokenScatterChart memberRows={memberRows} />
      <MemberPerfComboChart memberRows={memberRows} memberNameById={memberNameById} />
    </div>
  )
}
