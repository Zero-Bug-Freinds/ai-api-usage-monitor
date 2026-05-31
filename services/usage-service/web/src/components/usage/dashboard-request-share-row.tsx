"use client"

import * as React from "react"
import { Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip } from "recharts"
import { formatRequestCount } from "@/lib/usage/format"
import { colorForModel } from "@/lib/usage/model-colors"
import {
  aggregateProviderRequestShare,
  PROVIDER_COLOR,
  type ProviderShareDatum,
} from "@/lib/usage/provider-chart"

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const AnyLegend = Legend as any

const CHART_DATA_KEYS = {
  value: "value",
} as const

function truncateModelLabel(model: string, max = 36): string {
  if (model.length <= max) return model
  return `${model.slice(0, max - 1)}…`
}

type ShareRowInput = {
  model: string
  provider: string
  requestCount: number
}

type Props = {
  byModel?: ShareRowInput[]
  loading?: boolean
  emptyHint: string
  emptyAggregatedLabel: string
}

type ModelPieDatum = {
  name: string
  fullName: string
  provider: string
  value: number
  percent: number
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

export function DashboardRequestShareRow({
  byModel = [],
  loading = false,
  emptyHint,
  emptyAggregatedLabel,
}: Props) {
  const pieData = React.useMemo<ModelPieDatum[]>(() => {
    const totalReq = byModel.reduce((sum, row) => sum + row.requestCount, 0)
    if (totalReq <= 0) return []
    return byModel
      .filter((row) => row.requestCount > 0)
      .map((row) => ({
        name: truncateModelLabel(row.model),
        fullName: row.model,
        provider: row.provider,
        value: row.requestCount,
        percent: row.requestCount / totalReq,
      }))
  }, [byModel])

  const providerPieData = React.useMemo<ProviderShareDatum[]>(
    () => aggregateProviderRequestShare(byModel),
    [byModel]
  )

  const modelPieChartData = React.useMemo(
    () => (pieData.length > 0 ? pieData : [{ name: "—", fullName: "—", provider: "GOOGLE", value: 1, percent: 1 }]),
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

  const providerPieLegendPayload = React.useMemo(
    () =>
      providerPieData.map((entry) => ({
        value: entry.name,
        type: "square" as const,
        color: PROVIDER_COLOR[entry.provider] ?? "#737373",
      })),
    [providerPieData]
  )

  if (loading) {
    return (
      <div className="mb-8 grid gap-5 lg:grid-cols-3 lg:gap-6" aria-hidden="true">
        <section className="rounded-lg border border-border p-4 shadow-sm lg:col-span-2">
          <h2 className="mb-4 text-lg font-medium">모델별 요청 비중</h2>
          <div className="flex min-h-[320px] items-stretch gap-4">
            <div className="h-[320px] w-[320px] animate-pulse rounded-full bg-muted/45" />
            <div className="flex-1 space-y-2 py-1">
              {[0, 1, 2, 3, 4].map((i) => (
                <div key={i} className="h-4 w-full animate-pulse rounded bg-muted/45" />
              ))}
            </div>
          </div>
        </section>
        <section className="rounded-lg border border-border p-4 shadow-sm lg:col-span-1">
          <h2 className="mb-4 text-lg font-medium">공급사별 요청 비중</h2>
          <div className="h-[320px] w-full animate-pulse rounded-full bg-muted/45" />
        </section>
      </div>
    )
  }

  return (
    <div className="mb-8 grid gap-5 lg:grid-cols-3 lg:gap-6">
      <section className="rounded-lg border border-border p-4 shadow-sm lg:col-span-2">
        <h2 className="mb-4 text-lg font-medium">모델별 요청 비중</h2>
        <div className="flex min-h-[320px] items-stretch gap-4">
          <div className="flex h-[320px] w-[320px] shrink-0 items-center justify-center rounded-md border border-border/70 bg-card/30 p-2">
            <div className="h-[280px] w-[280px] shrink-0">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={modelPieChartData}
                    dataKey={CHART_DATA_KEYS.value}
                    nameKey="name"
                    cx="50%"
                    cy="50%"
                    innerRadius="52%"
                    outerRadius="80%"
                    paddingAngle={2}
                    isAnimationActive={!isModelPiePlaceholder}
                    label={isModelPiePlaceholder ? false : ({ index }) => String((index ?? 0) + 1)}
                  >
                    {modelPieChartData.map((entry, i) => (
                      <Cell
                        key={`m-${entry.fullName}-${i}`}
                        fill={isModelPiePlaceholder ? "var(--border)" : colorForModel(entry.fullName, entry.provider)}
                        fillOpacity={isModelPiePlaceholder ? 0.35 : 1}
                        style={{ cursor: "default" }}
                      />
                    ))}
                  </Pie>
                  <Tooltip content={ModelDonutTooltip} cursor={false} />
                </PieChart>
              </ResponsiveContainer>
            </div>
          </div>
          <div className="flex-1 max-h-[320px] overflow-y-auto p-1">
            {isModelPiePlaceholder ? (
              <p className="text-sm text-muted-foreground">{emptyAggregatedLabel}</p>
            ) : (
              <div className="space-y-1.5">
                {pieData.map((entry, i) => (
                  <div
                    key={`legend-model-${entry.fullName}-${i}`}
                    className="flex items-center gap-2 rounded px-1 py-1 text-sm"
                    title={entry.fullName}
                  >
                    <span className="w-5 shrink-0 text-right text-xs tabular-nums text-muted-foreground">{i + 1}.</span>
                    <span
                      className="h-2.5 w-2.5 shrink-0 rounded-sm"
                      style={{ backgroundColor: colorForModel(entry.fullName, entry.provider) }}
                    />
                    <span className="min-w-0 flex-1 truncate">{entry.fullName}</span>
                    <span className="shrink-0 text-xs tabular-nums text-muted-foreground">{(entry.percent * 100).toFixed(1)}%</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </section>

      <section className="rounded-lg border border-border p-4 shadow-sm lg:col-span-1">
        <h2 className="mb-4 text-lg font-medium">공급사별 요청 비중</h2>
        <div className="h-[320px] min-h-[320px] w-full min-w-0">
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Pie
                data={providerPieChartData}
                dataKey={CHART_DATA_KEYS.value}
                nameKey="name"
                cx="50%"
                cy="50%"
                innerRadius="52%"
                outerRadius="80%"
                paddingAngle={2}
                isAnimationActive={!isProviderPiePlaceholder}
                label={false}
              >
                {providerPieChartData.map((entry, i) => (
                  <Cell
                    key={`p-${entry.provider}-${i}`}
                    fill={isProviderPiePlaceholder ? "var(--border)" : PROVIDER_COLOR[entry.provider] ?? "#737373"}
                    fillOpacity={isProviderPiePlaceholder ? 0.35 : 1}
                    style={{ cursor: "default" }}
                  />
                ))}
              </Pie>
              <Tooltip content={ProviderDonutTooltip} cursor={false} />
              {!isProviderPiePlaceholder ? <AnyLegend payload={providerPieLegendPayload} /> : null}
            </PieChart>
          </ResponsiveContainer>
        </div>
        {isProviderPiePlaceholder ? (
          <p className="mt-2 text-center text-sm text-muted-foreground">{emptyHint}</p>
        ) : null}
      </section>
    </div>
  )
}
