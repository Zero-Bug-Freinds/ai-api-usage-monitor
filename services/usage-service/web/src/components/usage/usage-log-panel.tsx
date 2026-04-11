"use client"

import * as React from "react"

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
import { formatUsd } from "@/lib/usage/format"
import type {
  PagedLogsResponse,
  UsageLogApiKeyItem,
  UsageLogEntryResponse,
  UsageProviderFilter,
} from "@/lib/usage/types"
import { addKstDays, formatKstIsoDate } from "@/lib/usage/kst-dates"

const LOGS_PAGE_SIZE = 10
const LOG_PROVIDER_ALL = "__all__"
const LOG_API_KEY_ALL = "__all__"

function logProviderQueryParam(v: string): UsageProviderFilter | undefined {
  return v !== LOG_PROVIDER_ALL ? (v as UsageProviderFilter) : undefined
}

function maskApiKeyId(id: string | null | undefined): string {
  if (!id) return "—"
  if (id.length <= 12) return id
  return `${id.slice(0, 12)}…`
}

export function UsageLogPanel() {
  const [logs, setLogs] = React.useState<PagedLogsResponse | null>(null)
  const [logsLoading, setLogsLoading] = React.useState(true)
  const [logsError, setLogsError] = React.useState<string | null>(null)
  const [logsPage, setLogsPage] = React.useState(0)
  const [logProvider, setLogProvider] = React.useState<string>(LOG_PROVIDER_ALL)
  const [logApiKey, setLogApiKey] = React.useState<string>(LOG_API_KEY_ALL)
  const [apiKeyChoices, setApiKeyChoices] = React.useState<UsageLogApiKeyItem[]>([])
  const [modelDraft, setModelDraft] = React.useState("")
  const [appliedModelMask, setAppliedModelMask] = React.useState("")

  const applyLogFilters = React.useCallback(() => {
    setLogsPage(0)
    setAppliedModelMask(modelDraft.trim())
  }, [modelDraft])

  React.useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const prov = logProviderQueryParam(logProvider)
        const q = buildUsageQuery({ provider: prov })
        const list = await fetchUsageJson<UsageLogApiKeyItem[]>(`logs/api-keys${q}`)
        if (cancelled) return
        setApiKeyChoices(Array.isArray(list) ? list : [])
        setLogApiKey((prev) => {
          if (prev === LOG_API_KEY_ALL) return prev
          if (list.some((k) => k.apiKeyId === prev)) return prev
          return LOG_API_KEY_ALL
        })
      } catch {
        if (!cancelled) {
          setApiKeyChoices([])
          setLogApiKey(LOG_API_KEY_ALL)
        }
      }
    })()
    return () => {
      cancelled = true
    }
  }, [logProvider])

  React.useEffect(() => {
    let cancelled = false
    setLogsLoading(true)
    setLogsError(null)
    ;(async () => {
      try {
        const t = formatKstIsoDate()
        const f30 = addKstDays(t, -29)
        const providerParam = logProviderQueryParam(logProvider)
        const apiKeyParam = logApiKey !== LOG_API_KEY_ALL ? logApiKey : undefined
        const q = buildUsageQuery({
          from: f30,
          to: t,
          page: logsPage,
          size: LOGS_PAGE_SIZE,
          provider: providerParam,
          apiKeyId: apiKeyParam,
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
  }, [logsPage, appliedModelMask, logProvider, logApiKey])

  return (
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

        <div className="space-y-2 sm:w-56">
          <Label htmlFor="log-api-key">API Key</Label>
          <Select
            value={logApiKey}
            onValueChange={(v) => {
              setLogsPage(0)
              setLogApiKey(v)
            }}
          >
            <SelectTrigger id="log-api-key" className="w-full">
              <SelectValue placeholder="전체" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={LOG_API_KEY_ALL}>전체</SelectItem>
              {apiKeyChoices.map((k) => (
                <SelectItem key={k.apiKeyId} value={k.apiKeyId}>
                  {maskApiKeyId(k.apiKeyId)}
                </SelectItem>
              ))}
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

      {logsError ? <p className="mb-4 text-sm text-destructive">{logsError}</p> : null}

      {logsLoading ? (
        <p className="text-sm text-muted-foreground">로그 불러오는 중…</p>
      ) : !logs || logs.content.length === 0 ? (
        <p className="text-sm text-muted-foreground">사용 데이터가 없습니다</p>
      ) : (
        <>
          <div className="overflow-x-auto rounded-md border border-border">
            <table className="w-full min-w-[880px] text-left text-sm">
              <thead className="border-b border-border bg-muted/40">
                <tr>
                  <th className="px-3 py-2 font-medium">시각 (KST)</th>
                  <th className="px-3 py-2 font-medium">공급자</th>
                  <th className="px-3 py-2 font-medium">API Key</th>
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
                    <td className="px-3 py-2 font-mono text-xs">{maskApiKeyId(row.apiKeyId)}</td>
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
  )
}
