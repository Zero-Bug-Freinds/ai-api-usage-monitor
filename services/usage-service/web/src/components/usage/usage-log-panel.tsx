"use client"

import * as React from "react"
import { CircleHelp, ChevronDown, ChevronRight, Filter, Loader2, RotateCcw, X } from "lucide-react"

import {
  Button,
  Input,
  Label,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@ai-usage/ui"
import { DashboardApiKeySelectMenu } from "@/components/usage/dashboard-api-key-select-menu"
import { buildUsageQuery, fetchUsageJson } from "@/lib/usage/fetch-usage"
import { DASHBOARD_API_KEY_ALL, DASHBOARD_API_KEY_NONE } from "@/lib/usage/dashboard-api-key-constants"
import {
  DASHBOARD_PROVIDER_ALL,
  filterTeamBffRowsByProvider,
  teamBffRowsToUsageMenuItems,
} from "@/lib/usage/dashboard-provider-api-keys"
import { useDashboardAggregateApiKeySync } from "@/lib/usage/use-dashboard-aggregate-api-key"
import { useTeamBffTeamsAndApiKeys } from "@/lib/usage/use-team-bff-teams-and-api-keys"
import { formatOccurredAtKst } from "@/lib/usage/format-occurred-at-kst"
import type {
  PagedLogsResponse,
  UsageLogApiKeyItemResponse,
  UsageLogEntryResponse,
  UsageProviderFilter,
} from "@/lib/usage/types"
import { addKstDays, formatKstIsoDate } from "@/lib/usage/kst-dates"
import {
  logDataTabToDataContext,
  persistLogDataTab,
  readStoredLogDataTab,
  type UsageLogDataTab,
} from "@/lib/usage/usage-log-tab-storage"

const LOGS_PAGE_SIZE = 20
const LOG_PROVIDER_ALL = "__all__"
const LOG_API_KEY_ALL = "__all__"
const LOG_REASONING_ALL = "__all__"
const LOG_SUCCESS_ALL = "__all__"

function logProviderToTeamDashboardFilter(provider: string): string {
  if (provider === LOG_PROVIDER_ALL) return DASHBOARD_PROVIDER_ALL
  return provider
}

function toLongOrZero(v: number | null | undefined): number {
  return typeof v === "number" && Number.isFinite(v) ? v : 0
}

function openAiDetailsSum(row: UsageLogEntryResponse): number {
  return (
    toLongOrZero(row.promptCachedTokens) +
    toLongOrZero(row.promptAudioTokens) +
    toLongOrZero(row.completionReasoningTokens) +
    toLongOrZero(row.completionAudioTokens) +
    toLongOrZero(row.completionAcceptedPredictionTokens) +
    toLongOrZero(row.completionRejectedPredictionTokens)
  )
}

function reasoningTokensTooltipContent() {
  return (
    <div className="space-y-1">
      <p className="font-medium text-foreground">추론 토큰 산출</p>
      <p>
        Google / OpenAI: 모델이 직접 응답 전문에 포함하여 제공한 실제 추론 수치입니다.
      </p>
      <p>Anthropic: 현재 사용 기록이 없어 추론 토큰 상세값이 없는 경우가 있습니다.</p>
      <p>공통: 모델의 사고 과정(Reasoning) 및 시스템 처리 비용을 포함합니다.</p>
    </div>
  )
}

function outputTokensTooltipContent() {
  return (
    <div className="space-y-1">
      <p className="font-medium text-foreground">출력 토큰 산출</p>
      <p>출력 토큰에서는 추론 토큰을 제외한 순수 응답량만 표시합니다.</p>
    </div>
  )
}

export function UsageLogPanel() {
  const [logDataTab, setLogDataTab] = React.useState<UsageLogDataTab>("personal")
  const [logs, setLogs] = React.useState<PagedLogsResponse | null>(null)
  const [logsLoading, setLogsLoading] = React.useState(true)
  const [logsError, setLogsError] = React.useState<string | null>(null)
  const [logsPage, setLogsPage] = React.useState(0)
  const [logProvider, setLogProvider] = React.useState<string>(LOG_PROVIDER_ALL)
  const [personalApiKeyFilter, setPersonalApiKeyFilter] = React.useState<string>(LOG_API_KEY_ALL)
  const [teamLogApiKeyFilter, setTeamLogApiKeyFilter] = React.useState<string>(DASHBOARD_API_KEY_ALL)
  const [reasoningFilter, setReasoningFilter] = React.useState<string>(LOG_REASONING_ALL)
  const [successFilter, setSuccessFilter] = React.useState<string>(LOG_SUCCESS_ALL)
  const [personalApiKeyOptions, setPersonalApiKeyOptions] = React.useState<UsageLogApiKeyItemResponse[]>([])
  const [modelDraft, setModelDraft] = React.useState("")
  const [logRefresh, setLogRefresh] = React.useState(0)
  const [openAiDetailsRow, setOpenAiDetailsRow] = React.useState<UsageLogEntryResponse | null>(null)
  const [showAdvancedFilters, setShowAdvancedFilters] = React.useState(false)

  const appliedModelMask = modelDraft.trim()

  React.useLayoutEffect(() => {
    setLogDataTab(readStoredLogDataTab())
  }, [])

  const onLogDataTabChange = React.useCallback((tab: UsageLogDataTab) => {
    persistLogDataTab(tab)
    setLogDataTab(tab)
    setLogsPage(0)
    if (tab === "team") {
      setLogProvider(LOG_PROVIDER_ALL)
      setTeamLogApiKeyFilter(DASHBOARD_API_KEY_ALL)
    }
  }, [])

  React.useEffect(() => {
    if (!openAiDetailsRow) return
    const prev = document.body.style.overflow
    document.body.style.overflow = "hidden"
    return () => {
      document.body.style.overflow = prev
    }
  }, [openAiDetailsRow])

  const closeOpenAiDetails = React.useCallback(() => setOpenAiDetailsRow(null), [])

  const providerParam =
    logProvider !== LOG_PROVIDER_ALL ? (logProvider as UsageProviderFilter) : undefined
  const requestSuccessfulParam =
    successFilter === "true" ? "true" : successFilter === "false" ? "false" : undefined
  const reasoningPresenceParam =
    reasoningFilter === "present" ? "present" : reasoningFilter === "absent" ? "absent" : undefined

  const logDataContext = logDataTabToDataContext(logDataTab)

  const teamBff = useTeamBffTeamsAndApiKeys(logDataTab === "team")

  const teamApiKeyMenuItems = React.useMemo(() => {
    if (logDataTab !== "team") return [] as UsageLogApiKeyItemResponse[]
    return teamBffRowsToUsageMenuItems(
      filterTeamBffRowsByProvider(
        teamBff.teamMemberRawApiKeyRows,
        logProviderToTeamDashboardFilter(logProvider),
      ),
    )
  }, [logDataTab, teamBff.teamMemberRawApiKeyRows, logProvider])

  useDashboardAggregateApiKeySync(
    teamApiKeyMenuItems,
    teamLogApiKeyFilter,
    setTeamLogApiKeyFilter,
    logDataTab === "team" && Boolean(teamBff.teamMemberTeamId),
  )

  const apiKeyOptions = logDataTab === "team" ? teamApiKeyMenuItems : personalApiKeyOptions

  React.useEffect(() => {
    if (logDataTab !== "personal") return
    let cancelled = false
    ;(async () => {
      try {
        const q = buildUsageQuery({ provider: providerParam, dataContext: logDataContext })
        const data = await fetchUsageJson<UsageLogApiKeyItemResponse[]>(`logs/api-keys${q}`)
        if (!cancelled) {
          setPersonalApiKeyOptions(Array.isArray(data) ? data : [])
        }
      } catch {
        if (!cancelled) setPersonalApiKeyOptions([])
      }
    })()
    return () => {
      cancelled = true
    }
  }, [logDataTab, logProvider, providerParam, logRefresh, logDataContext])

  React.useEffect(() => {
    let cancelled = false

    if (logDataTab === "team") {
      if (teamBff.memberTeamsLoading) {
        setLogsLoading(true)
        return () => {
          cancelled = true
        }
      }
      if (teamBff.memberTeams.length === 0) {
        if (!cancelled) {
          setLogsLoading(false)
          setLogsError(null)
          setLogs({
            content: [],
            page: 0,
            size: LOGS_PAGE_SIZE,
            totalElements: 0,
            totalPages: 1,
          })
        }
        return () => {
          cancelled = true
        }
      }
      if (!teamBff.teamMemberTeamId || teamBff.teamMemberKeysLoading) {
        setLogsLoading(true)
        return () => {
          cancelled = true
        }
      }
    }

    setLogsLoading(true)
    setLogsError(null)
    ;(async () => {
      try {
        const t = formatKstIsoDate()
        const f30 = addKstDays(t, -29)
        const apiKeyId =
          logDataTab === "team"
            ? teamLogApiKeyFilter !== DASHBOARD_API_KEY_ALL &&
                teamLogApiKeyFilter !== DASHBOARD_API_KEY_NONE
              ? teamLogApiKeyFilter
              : undefined
            : personalApiKeyFilter !== LOG_API_KEY_ALL && personalApiKeyFilter
              ? personalApiKeyFilter
              : undefined
        const teamId =
          logDataTab === "team" && teamBff.teamMemberTeamId ? teamBff.teamMemberTeamId : undefined
        const q = buildUsageQuery({
          from: f30,
          to: t,
          page: logsPage,
          size: LOGS_PAGE_SIZE,
          provider: providerParam,
          dataContext: logDataContext,
          apiKeyId,
          teamId,
          reasoningPresence: reasoningPresenceParam,
          requestSuccessful: requestSuccessfulParam,
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
  }, [
    logsPage,
    modelDraft,
    appliedModelMask,
    logProvider,
    providerParam,
    personalApiKeyFilter,
    teamLogApiKeyFilter,
    logDataTab,
    teamBff.memberTeamsLoading,
    teamBff.memberTeams.length,
    teamBff.teamMemberTeamId,
    teamBff.teamMemberKeysLoading,
    reasoningPresenceParam,
    requestSuccessfulParam,
    logRefresh,
    logDataContext,
  ])

  const hasAdvancedReasoning = reasoningFilter !== LOG_REASONING_ALL
  const hasAdvancedSuccess = successFilter !== LOG_SUCCESS_ALL
  const hasAnyAdvancedFilter = hasAdvancedReasoning || hasAdvancedSuccess

  const resetAllFilters = React.useCallback(() => {
    setLogsPage(0)
    setLogProvider(LOG_PROVIDER_ALL)
    setPersonalApiKeyFilter(LOG_API_KEY_ALL)
    setTeamLogApiKeyFilter(DASHBOARD_API_KEY_ALL)
    setReasoningFilter(LOG_REASONING_ALL)
    setSuccessFilter(LOG_SUCCESS_ALL)
    setModelDraft("")
    setLogRefresh((n) => n + 1)
  }, [])

  return (
    <div className="w-full max-w-none rounded-lg border border-border p-4 shadow-sm">
      <div
        className="mb-5 flex gap-0 border-b border-border"
        role="tablist"
        aria-label="로그 범위"
      >
        <button
          type="button"
          role="tab"
          aria-selected={logDataTab === "personal"}
          className={[
            "-mb-px border-b-2 px-4 py-2.5 text-sm font-medium transition-colors",
            logDataTab === "personal"
              ? "border-primary text-foreground"
              : "border-transparent text-muted-foreground hover:text-foreground",
          ].join(" ")}
          onClick={() => onLogDataTabChange("personal")}
        >
          개인 로그
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={logDataTab === "team"}
          className={[
            "-mb-px border-b-2 px-4 py-2.5 text-sm font-medium transition-colors",
            logDataTab === "team"
              ? "border-primary text-foreground"
              : "border-transparent text-muted-foreground hover:text-foreground",
          ].join(" ")}
          onClick={() => onLogDataTabChange("team")}
        >
          팀 로그
        </button>
      </div>

      <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm text-muted-foreground">발생 시각은 한국 표준시(KST)입니다.</p>
        <Button type="button" variant="outline" size="sm" onClick={() => setLogRefresh((n) => n + 1)}>
          새로고침
        </Button>
      </div>

      <div className="mb-4 flex flex-col gap-4 sm:flex-row sm:flex-wrap sm:items-end">
        {logDataTab === "team" ? (
          <div className="space-y-2 sm:w-52 min-w-[10rem]">
            <Label htmlFor="log-team">팀</Label>
            <Select
              value={
                teamBff.teamMemberTeamId &&
                teamBff.memberTeams.some((x) => x.id === teamBff.teamMemberTeamId)
                  ? teamBff.teamMemberTeamId
                  : undefined
              }
              onValueChange={(id) => {
                setLogsPage(0)
                teamBff.setTeamMemberTeamId(id)
                setLogProvider(LOG_PROVIDER_ALL)
                setTeamLogApiKeyFilter(DASHBOARD_API_KEY_ALL)
              }}
              disabled={teamBff.memberTeamsLoading || teamBff.memberTeams.length === 0}
            >
              <SelectTrigger id="log-team" className="w-full">
                <SelectValue
                  placeholder={
                    teamBff.memberTeamsLoading
                      ? "불러오는 중…"
                      : teamBff.memberTeams.length === 0
                        ? "소속 팀 없음"
                        : "팀 선택"
                  }
                />
              </SelectTrigger>
              <SelectContent>
                {teamBff.memberTeams.map((tm) => (
                  <SelectItem key={tm.id} value={tm.id}>
                    {tm.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {teamBff.memberTeamsErr ? (
              <p className="text-xs text-destructive">{teamBff.memberTeamsErr}</p>
            ) : null}
          </div>
        ) : null}

        <div className="space-y-2 sm:w-40">
          <Label htmlFor="log-provider">공급자</Label>
          <Select
            value={logProvider}
            onValueChange={(v) => {
              setLogsPage(0)
              setLogProvider(v)
              if (logDataTab === "team") {
                setTeamLogApiKeyFilter(DASHBOARD_API_KEY_ALL)
              } else {
                setPersonalApiKeyFilter(LOG_API_KEY_ALL)
              }
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
          <Label htmlFor="log-apikey">API Key</Label>
          <Select
            value={logDataTab === "team" ? teamLogApiKeyFilter : personalApiKeyFilter}
            onValueChange={(v) => {
              setLogsPage(0)
              if (logDataTab === "team") {
                setTeamLogApiKeyFilter(v)
              } else {
                setPersonalApiKeyFilter(v)
              }
            }}
          >
            <SelectTrigger id="log-apikey" className="w-full">
              <SelectValue placeholder="전체" />
            </SelectTrigger>
            <SelectContent className="max-h-[min(70vh,26rem)]">
              {logDataTab === "team" ? (
                <DashboardApiKeySelectMenu
                  items={apiKeyOptions}
                  allValue={DASHBOARD_API_KEY_ALL}
                  showAllOption
                  noneValue={DASHBOARD_API_KEY_NONE}
                  showNoneOption={apiKeyOptions.length === 0}
                />
              ) : (
                <DashboardApiKeySelectMenu
                  items={apiKeyOptions}
                  allValue={LOG_API_KEY_ALL}
                  showAllOption
                />
              )}
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-2 sm:w-56">
          <Label htmlFor="log-model">모델 (부분 일치)</Label>
          <Input
            id="log-model"
            value={modelDraft}
            onChange={(e) => {
              setLogsPage(0)
              setModelDraft(e.target.value)
            }}
            placeholder="예: gpt-4"
            autoComplete="off"
          />
        </div>

        <Button
          type="button"
          variant="outline"
          size="sm"
          className="gap-1.5 sm:self-end"
          onClick={() => setShowAdvancedFilters((v) => !v)}
        >
          <Filter className="h-4 w-4" />
          상세 필터
          <ChevronDown className={`h-4 w-4 transition-transform ${showAdvancedFilters ? "rotate-180" : ""}`} />
        </Button>

        <Button
          type="button"
          variant="ghost"
          size="sm"
          className="sm:self-end"
          onClick={resetAllFilters}
          aria-label="필터링 조건 해제"
          title="필터링 조건 해제"
        >
          <RotateCcw className="h-4 w-4" />
        </Button>
      </div>

      <div
        className={[
          "mb-4 overflow-hidden transition-all duration-300 ease-out",
          showAdvancedFilters ? "max-h-40 opacity-100" : "max-h-0 opacity-0",
        ].join(" ")}
      >
        <div className="flex flex-wrap gap-4">
          <div className="space-y-2 sm:w-40">
            <div className="flex items-center gap-1">
              <Label htmlFor="log-reasoning" className="mb-0">
                추론 토큰
              </Label>
              <TooltipProvider>
                <Tooltip>
                  <TooltipTrigger asChild>
                    <button
                      type="button"
                      id="log-reasoning-help"
                      className="inline-flex h-4 w-4 shrink-0 items-center justify-center rounded-full border border-muted-foreground/40 text-[10px] text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/60"
                      aria-label="추론 토큰 설명"
                    >
                      <CircleHelp className="h-3 w-3" />
                    </button>
                  </TooltipTrigger>
                  <TooltipContent side="top" align="start">
                    {reasoningTokensTooltipContent()}
                  </TooltipContent>
                </Tooltip>
              </TooltipProvider>
            </div>
            <Select
              value={reasoningFilter}
              onValueChange={(v) => {
                setLogsPage(0)
                setReasoningFilter(v)
              }}
            >
              <SelectTrigger id="log-reasoning" className="w-full">
                <SelectValue placeholder="전체" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={LOG_REASONING_ALL}>전체</SelectItem>
                <SelectItem value="present">있음</SelectItem>
                <SelectItem value="absent">없음</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-2 sm:w-40">
            <Label htmlFor="log-success">성공</Label>
            <Select
              value={successFilter}
              onValueChange={(v) => {
                setLogsPage(0)
                setSuccessFilter(v)
              }}
            >
              <SelectTrigger id="log-success" className="w-full">
                <SelectValue placeholder="전체" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={LOG_SUCCESS_ALL}>전체</SelectItem>
                <SelectItem value="true">예</SelectItem>
                <SelectItem value="false">아니오</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>
      </div>

      {hasAnyAdvancedFilter ? (
        <div className="mb-4 flex flex-wrap items-center gap-2">
          <p className="text-xs text-muted-foreground">적용된 필터링 조건</p>
          {hasAdvancedSuccess ? (
            <span className="inline-flex items-center gap-1 rounded-full border border-border bg-muted px-2.5 py-1 text-xs">
              성공: {successFilter === "true" ? "예" : "아니오"}
              <button
                type="button"
                className="inline-flex h-4 w-4 items-center justify-center rounded-full hover:bg-background"
                onClick={() => {
                  setLogsPage(0)
                  setSuccessFilter(LOG_SUCCESS_ALL)
                }}
                aria-label="성공 필터 해제"
              >
                <X className="h-3 w-3" />
              </button>
            </span>
          ) : null}
          {hasAdvancedReasoning ? (
            <span className="inline-flex items-center gap-1 rounded-full border border-border bg-muted px-2.5 py-1 text-xs">
              추론 토큰: {reasoningFilter === "present" ? "있음" : "없음"}
              <button
                type="button"
                className="inline-flex h-4 w-4 items-center justify-center rounded-full hover:bg-background"
                onClick={() => {
                  setLogsPage(0)
                  setReasoningFilter(LOG_REASONING_ALL)
                }}
                aria-label="추론 토큰 필터 해제"
              >
                <X className="h-3 w-3" />
              </button>
            </span>
          ) : null}
        </div>
      ) : null}

      {logsError ? (
        <p className="mb-4 text-sm text-destructive">{logsError}</p>
      ) : null}

      {logsLoading ? (
        <div
          className="space-y-3 rounded-md border border-border bg-muted/15 p-4"
          aria-busy="true"
          aria-label="로그 로딩 중"
        >
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2 className="h-4 w-4 shrink-0 animate-spin" aria-hidden />
            <span>로그를 불러오는 중입니다…</span>
          </div>
          <div className="space-y-2">
            <div className="h-3 max-w-xs w-[40%] rounded bg-muted animate-pulse" />
            <div className="h-40 rounded bg-muted/70 animate-pulse" />
          </div>
        </div>
      ) : !logs || logs.content.length === 0 ? (
        <p className="text-sm text-muted-foreground">사용 데이터가 없습니다</p>
      ) : (
        <>
          <div className="overflow-x-auto rounded-md border border-border">
            <table className="w-full min-w-[980px] table-auto text-left text-sm [&_th]:px-2.5 [&_td]:px-2.5">
              <thead className="border-b border-border bg-muted/40">
                <tr>
                  <th className="py-2 font-medium whitespace-nowrap">시각 (KST)</th>
                  <th className="py-2 font-medium whitespace-nowrap">공급자</th>
                  <th className="py-2 font-medium">별칭</th>
                  <th className="py-2 font-medium">모델</th>
                  <th className="py-2 font-medium whitespace-nowrap">입력 토큰</th>
                  <th className="py-2 font-medium whitespace-nowrap">
                    <span className="inline-flex items-center gap-1">
                      추론 토큰
                      <TooltipProvider>
                        <Tooltip>
                          <TooltipTrigger asChild>
                            <button
                              type="button"
                              className="inline-flex h-4 w-4 items-center justify-center rounded-full border border-muted-foreground/40 text-[10px] text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/60"
                              aria-label="추론 토큰 설명"
                            >
                              <CircleHelp className="h-3 w-3" />
                            </button>
                          </TooltipTrigger>
                          <TooltipContent side="top" align="start">
                            {reasoningTokensTooltipContent()}
                          </TooltipContent>
                        </Tooltip>
                      </TooltipProvider>
                    </span>
                  </th>
                  <th className="py-2 font-medium whitespace-nowrap">
                    <span className="inline-flex items-center gap-1">
                      출력 토큰
                      <TooltipProvider>
                        <Tooltip>
                          <TooltipTrigger asChild>
                            <button
                              type="button"
                              className="inline-flex h-4 w-4 items-center justify-center rounded-full border border-muted-foreground/40 text-[10px] text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/60"
                              aria-label="출력 토큰 설명"
                            >
                              <CircleHelp className="h-3 w-3" />
                            </button>
                          </TooltipTrigger>
                          <TooltipContent side="top" align="start">
                            {outputTokensTooltipContent()}
                          </TooltipContent>
                        </Tooltip>
                      </TooltipProvider>
                    </span>
                  </th>
                  <th className="py-2 font-medium whitespace-nowrap">합계</th>
                  <th className="py-2 font-medium text-right whitespace-nowrap">상세</th>
                </tr>
              </thead>
              <tbody>
                {logs.content.map((row: UsageLogEntryResponse) => {
                  const isOpenAi = row.provider === "OPENAI"
                  const hasOpenAiDetails = isOpenAi && openAiDetailsSum(row) > 0
                  const ert = row.estimatedReasoningTokens
                  const reasoningDisplay = !row.requestSuccessful
                    ? "-"
                    : typeof ert === "number" && Number.isFinite(ert)
                      ? String(ert)
                      : "0"
                  return (
                    <tr
                      key={row.eventId}
                      className={[
                        "border-b border-border last:border-0",
                        isOpenAi && hasOpenAiDetails ? "cursor-pointer hover:bg-muted/40" : "",
                      ].join(" ")}
                      onClick={() => {
                        if (!hasOpenAiDetails) return
                        setOpenAiDetailsRow(row)
                      }}
                      role={isOpenAi && hasOpenAiDetails ? "button" : undefined}
                      tabIndex={isOpenAi && hasOpenAiDetails ? 0 : -1}
                      onKeyDown={(e) => {
                        if (!hasOpenAiDetails) return
                        if (e.key === "Enter" || e.key === " ") {
                          e.preventDefault()
                          setOpenAiDetailsRow(row)
                        }
                      }}
                    >
                    <td className="py-2 font-mono text-xs whitespace-nowrap">
                      {formatOccurredAtKst(row.occurredAt)}
                    </td>
                    <td className="py-2 whitespace-nowrap">{row.provider}</td>
                    <td className="py-2 max-w-[180px] truncate" title={row.apiKeyAlias ?? undefined}>
                      {row.apiKeyAlias ?? "—"}
                    </td>
                    <td className="py-2 max-w-[220px] truncate font-mono text-xs" title={row.model}>
                      {row.model}
                    </td>
                    <td className="py-2 tabular-nums whitespace-nowrap">{row.promptTokens ?? "—"}</td>
                    <td className="py-2 tabular-nums whitespace-nowrap">{reasoningDisplay}</td>
                    <td className="py-2 tabular-nums whitespace-nowrap">{row.completionTokens ?? "—"}</td>
                    <td className="py-2 tabular-nums whitespace-nowrap">{row.totalTokens ?? "—"}</td>
                    <td className="py-2 text-right text-muted-foreground">
                      {isOpenAi && hasOpenAiDetails ? (
                        <ChevronRight className="inline-block h-4 w-4" aria-label="상세보기" />
                      ) : (
                        <span className="inline-block h-4 w-4" aria-hidden="true" />
                      )}
                    </td>
                  </tr>
                  )
                })}
              </tbody>
            </table>
          </div>

          <div className="mt-4 flex flex-wrap items-center justify-between gap-2 text-sm text-muted-foreground">
            <span>
              페이지 {logs.page + 1} / {Math.max(1, logs.totalPages)} · 총{" "}
              {logs.totalElements.toLocaleString("en-US")}건
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

      {openAiDetailsRow ? (
        <div className="fixed inset-0 z-50">
          <div className="absolute inset-0 bg-black/40" onClick={closeOpenAiDetails} />

          <aside
            className="absolute right-0 top-0 h-full w-full max-w-md overflow-y-auto border-l border-border bg-card shadow-xl"
            aria-label="OpenAI 상세 로그 패널"
          >
            <div className="p-4">
              <div className="flex items-start justify-between gap-3 border-b border-border pb-3">
                <div className="min-w-0">
                  <p className="text-sm font-semibold">OpenAI 상세 토큰</p>
                  <p className="mt-1 truncate text-xs text-muted-foreground">
                    {openAiDetailsRow.provider} / {openAiDetailsRow.model}
                  </p>
                </div>
                <Button type="button" variant="outline" size="sm" onClick={closeOpenAiDetails}>
                  닫기
                </Button>
              </div>

              {(() => {
                const cached = toLongOrZero(openAiDetailsRow.promptCachedTokens)
                const promptAudio = toLongOrZero(openAiDetailsRow.promptAudioTokens)
                const reasoning = toLongOrZero(openAiDetailsRow.completionReasoningTokens)
                const completionAudio = toLongOrZero(openAiDetailsRow.completionAudioTokens)
                const accepted = toLongOrZero(openAiDetailsRow.completionAcceptedPredictionTokens)
                const rejected = toLongOrZero(openAiDetailsRow.completionRejectedPredictionTokens)

                const bothPredZero = accepted === 0 && rejected === 0
                const predSum = accepted + rejected

                return (
                  <div className="mt-4 space-y-5">
                    <section className="space-y-3">
                      <h3 className="text-sm font-semibold">Prompt Details</h3>
                      <div className="grid gap-3 sm:grid-cols-2">
                        <div className="rounded-md border border-border/70 p-3">
                          <p className="text-xs text-muted-foreground">Cached Tokens</p>
                          <p className="mt-1 tabular-nums text-sm font-semibold">{cached.toLocaleString("en-US")}</p>
                        </div>
                        <div className="rounded-md border border-border/70 p-3">
                          <p className="text-xs text-muted-foreground">Audio Tokens</p>
                          <p className="mt-1 tabular-nums text-sm font-semibold">{promptAudio.toLocaleString("en-US")}</p>
                        </div>
                      </div>
                    </section>

                    <section className="space-y-3">
                      <h3 className="text-sm font-semibold">Completion Details</h3>
                      <div className="grid gap-3 sm:grid-cols-2">
                        <div className="rounded-md border border-border/70 p-3">
                          <p className="text-xs text-muted-foreground">Reasoning Tokens</p>
                          <p className="mt-1 tabular-nums text-sm font-semibold">{reasoning.toLocaleString("en-US")}</p>
                        </div>
                        <div className="rounded-md border border-border/70 p-3">
                          <p className="text-xs text-muted-foreground">Audio Tokens</p>
                          <p className="mt-1 tabular-nums text-sm font-semibold">{completionAudio.toLocaleString("en-US")}</p>
                        </div>
                      </div>

                      <div className="rounded-md border border-border/70 p-3">
                        <div className="flex items-start justify-between gap-3">
                          <div>
                            <p className="text-xs text-muted-foreground">Prediction Tokens</p>
                            <p className="mt-1 text-sm font-semibold tabular-nums">
                              Accepted {accepted.toLocaleString("en-US")} / Rejected {rejected.toLocaleString("en-US")}
                            </p>
                          </div>
                        </div>

                        <div className="mt-3">
                          {bothPredZero ? (
                            <p className="text-xs text-muted-foreground">두 값 모두 0</p>
                          ) : (
                            <>
                              <div className="flex h-2 overflow-hidden rounded bg-muted/30" aria-label="Accepted/Rejected prediction mini graph">
                                <div
                                  className="h-full bg-emerald-500"
                                  style={{ flexGrow: accepted }}
                                  aria-hidden="true"
                                />
                                <div
                                  className="h-full bg-rose-500"
                                  style={{ flexGrow: rejected }}
                                  aria-hidden="true"
                                />
                              </div>
                              <p className="mt-2 text-[11px] text-muted-foreground tabular-nums">
                                Accepted {Math.round((accepted / predSum) * 100)}% · Rejected {Math.round((rejected / predSum) * 100)}%
                              </p>
                            </>
                          )}
                        </div>
                      </div>
                    </section>
                  </div>
                )
              })()}
            </div>
          </aside>
        </div>
      ) : null}
    </div>
  )
}
