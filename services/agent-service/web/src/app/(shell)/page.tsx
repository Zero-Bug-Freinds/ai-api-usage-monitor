"use client"

import { type ChangeEvent, useEffect, useMemo, useState } from "react"

type BudgetForecastResponse = {
  healthStatus: "HEALTHY" | "WARNING" | "CRITICAL" | string
  predictedRunOutDate: string
  daysUntilRunOut: number
  daysUntilBillingCycleEnd: number | null
  billingDateGapDays: number | null
  budgetUtilizationPercent: string | number
  assistantMessage: string
  recommendedActions: string[]
}

type AvailableKeyContext = {
  keyId: number
  /** 팀 키 분석 시에만 설정 (결제일 저장·요청 구분용) */
  teamIdForBilling?: number
  keyLabel: string
  provider: string
  monthlyBudgetUsd: number
  status: string
  budgetStats?: {
    currentSpendUsd: number
    remainingBudgetUsd: number
    budgetUsagePercent: number
    isBudgetExceeded: boolean
  }
  providerStats: {
    currentSpendUsd: number
    averageDailySpendUsd: number
    averageDailyTokenUsage: number
    recentDailySpendUsd: number[]
  }
}

type TeamBoardItem = {
  teamId: number
  teamName?: string
  teamApiKeyId: number
  ownerUserId?: string | null
  visibility?: string | null
  alias: string
  provider: string
  status: string
  monthlyBudgetUsd?: number
  budgetStats?: {
    currentSpendUsd: number
    remainingBudgetUsd: number
    budgetUsagePercent: number
    isBudgetExceeded: boolean
  }
  providerStats?: {
    currentSpendUsd: number
    averageDailySpendUsd: number
    averageDailyTokenUsage: number
    recentDailySpendUsd: number[]
  }
}

type AnalysisResult = {
  keyId: number
  keyLabel: string
  provider: string
  data?: BudgetForecastResponse
  recommendation?: RecommendationQueryResponse
  error?: string
  recommendationError?: string
  /** 이벤트/데이터가 없어 추정·대체한 항목 (막지 않고 안내용) */
  forecastGaps?: string[]
}

type TeamGroup = {
  teamId: number
  teamName: string
  keys: TeamBoardItem[]
}

type AnalysisScope = "PERSONAL" | "TEAM"

type ModelCatalogSnapshot = {
  source: string
  updatedAt: string
  lastAttemptAt?: string
  lastRefreshSucceeded?: boolean
  lastRefreshError?: string | null
  models: Array<{
    provider?: string | null
    modelName: string
    inputPricePer1mUsd: number
    outputPricePer1mUsd: number
    status?: string | null
  }>
}

type RecommendationQueryResponse = {
  keyId: string
  keyType: "PERSONAL" | "TEAM" | string
  status: "RECOMMENDATION_AVAILABLE" | "NO_RECOMMENDATION" | string
  generatedAt: string
  metricsContext?: {
    analysisWindowDays: number
    totalTokensUsed: number
    inputOutputRatio: string
    averageLatencyMs: number
    totalRequests: number
  } | null
  recommendationDetails?: {
    title: string
    reasonCode: string
    reasonMessage: string
    confidenceLevel: "HIGH" | "MEDIUM" | "LOW" | string
    disclaimer?: string | null
    estimatedSavingsPct: number | string
    candidates: Array<{
      modelName: string
      expectedCostDiffPct: number | string
      expectedMonthlyCostUsd: number | string
      keyFeature: string
    }>
  } | null
}

function parseInputOutputRatio(value: string | null | undefined): { input: number; output: number } | null {
  if (!value) return null
  const [inputRaw, outputRaw] = value.split(":")
  const input = Number(inputRaw)
  const output = Number(outputRaw)
  if (!Number.isFinite(input) || !Number.isFinite(output) || input < 0 || output < 0) return null
  return { input, output }
}

function estimateSavingsUsd(
  estimatedSavingsPct: number | string,
  recommendedMonthlyCostUsd: number | string,
): number | null {
  const pct = Number(estimatedSavingsPct)
  const recommended = Number(recommendedMonthlyCostUsd)
  if (!Number.isFinite(pct) || !Number.isFinite(recommended)) return null
  if (pct <= 0 || pct >= 100 || recommended < 0) return null
  const estimatedCurrent = recommended / (1 - pct / 100)
  const estimatedSavingsUsd = estimatedCurrent - recommended
  if (!Number.isFinite(estimatedSavingsUsd) || estimatedSavingsUsd < 0) return null
  return estimatedSavingsUsd
}

function latencyStatus(latencyMs: number): { label: string; className: string; progress: number } {
  if (latencyMs <= 600) {
    return { label: "빠름", className: "bg-emerald-100 text-emerald-700", progress: 25 }
  }
  if (latencyMs <= 1200) {
    return { label: "보통", className: "bg-amber-100 text-amber-700", progress: 55 }
  }
  if (latencyMs <= 2000) {
    return { label: "지연", className: "bg-orange-100 text-orange-700", progress: 78 }
  }
  return { label: "높은 지연", className: "bg-red-100 text-red-700", progress: 92 }
}

function ratioDominance(ratio: { input: number; output: number } | null): {
  label: string
  className: string
  inputPct: number
  outputPct: number
} {
  if (!ratio) {
    return { label: "지표 없음", className: "bg-muted text-muted-foreground", inputPct: 0, outputPct: 0 }
  }
  const total = ratio.input + ratio.output
  if (total <= 0) {
    return { label: "지표 없음", className: "bg-muted text-muted-foreground", inputPct: 0, outputPct: 0 }
  }
  const inputPct = (ratio.input / total) * 100
  const outputPct = 100 - inputPct
  if (inputPct >= 80) {
    return { label: "입력 중심", className: "bg-blue-100 text-blue-700", inputPct, outputPct }
  }
  if (outputPct >= 80) {
    return { label: "출력 중심", className: "bg-violet-100 text-violet-700", inputPct, outputPct }
  }
  return { label: "균형형", className: "bg-slate-100 text-slate-700", inputPct, outputPct }
}

type AvailableContextKeyPayload = {
  keyId: number
  alias: string
  provider: string
  monthlyBudgetUsd: number
  status: string
  budgetStats?: {
    currentSpendUsd: number
    remainingBudgetUsd: number
    budgetUsagePercent: number
    isBudgetExceeded: boolean
  }
  providerStats: {
    currentSpendUsd: number
    averageDailySpendUsd: number
    averageDailyTokenUsage: number
    recentDailySpendUsd: number[]
  }
}

type AvailableContextPayload = {
  currentUserId?: number | null
  teamBoard?: TeamBoardItem[]
  teamGroups?: TeamGroup[]
  data?: AvailableContextKeyPayload[]
}

function formatBillingMetric(value: number | null | undefined): string {
  if (value == null) {
    return "표시 불가 — 결제일 미입력"
  }
  return `${value}일`
}

const MANUAL_BILLING_STORAGE_PREFIX = "agent.manualBillingCycleEnd."

function storagePathPersonalKey(keyId: number): string {
  return `${MANUAL_BILLING_STORAGE_PREFIX}personal.${keyId}`
}

function storagePathTeamKey(teamId: number, teamApiKeyId: number): string {
  return `${MANUAL_BILLING_STORAGE_PREFIX}team.${teamId}.${teamApiKeyId}`
}

function ledgerKeyPersonal(keyId: number): string {
  return `p:${keyId}`
}

function ledgerKeyTeam(teamId: number, teamApiKeyId: number): string {
  return `t:${teamId}:${teamApiKeyId}`
}

function readBillingFromStorage(path: string): string {
  if (typeof window === "undefined") return ""
  try {
    return window.localStorage.getItem(path) ?? ""
  } catch {
    return ""
  }
}

function writeBillingToStorage(path: string, isoDate: string): void {
  if (typeof window === "undefined") return
  try {
    if (isoDate.trim() === "") {
      window.localStorage.removeItem(path)
    } else {
      window.localStorage.setItem(path, isoDate.trim())
    }
  } catch {
    // ignore quota / private mode
  }
}

function resolveForecastInputs(
  stats: AvailableKeyContext["providerStats"],
  monthlyBudgetUsd: number,
): {
  averageDailySpendUsd: number
  averageDailyTokenUsage: number
  remainingTokens: number
  recentDailySpendUsd: number[]
  gaps: string[]
  sufficientForForecast: boolean
} {
  const gaps: string[] = []
  const spendFromPrediction = stats.averageDailySpendUsd
  const tokensFromPrediction = stats.averageDailyTokenUsage
  const billedSpend = stats.currentSpendUsd

  let averageDailySpendUsd = spendFromPrediction
  if (averageDailySpendUsd <= 0 && billedSpend > 0) {
    averageDailySpendUsd = billedSpend / 7
  } else if (averageDailySpendUsd <= 0 && billedSpend <= 0 && monthlyBudgetUsd > 0) {
    averageDailySpendUsd = monthlyBudgetUsd / 30
  } else if (averageDailySpendUsd <= 0) {
    averageDailySpendUsd = 0.01
  }

  const averageDailyTokenUsage = tokensFromPrediction > 0 ? tokensFromPrediction : 1
  const remainingBudgetUsd = Math.max(monthlyBudgetUsd - billedSpend, 0)
  const estimatedDaysByBudget =
    averageDailySpendUsd > 0 ? Math.max(remainingBudgetUsd / averageDailySpendUsd, 0) : 0
  const remainingTokens = Math.max(Math.round(averageDailyTokenUsage * estimatedDaysByBudget), 1)

  const recentDailySpendUsd = (stats.recentDailySpendUsd ?? [])
    .map((value) => Number(value))
    .filter((value) => Number.isFinite(value) && value >= 0)

  const sufficientForForecast = true

  return {
    averageDailySpendUsd,
    averageDailyTokenUsage,
    remainingTokens,
    recentDailySpendUsd,
    gaps,
    sufficientForForecast,
  }
}

function statusClassName(status: string): string {
  if (status === "CRITICAL") return "bg-red-100 text-red-700"
  if (status === "WARNING") return "bg-amber-100 text-amber-700"
  return "bg-emerald-100 text-emerald-700"
}

function localizedHealthStatus(status: string): string {
  if (status === "CRITICAL") return "위험"
  if (status === "WARNING") return "주의"
  if (status === "HEALTHY") return "양호"
  return status
}

function localizeAssistantMessage(message: string): string {
  return message
    .replaceAll("CRITICAL", "위험")
    .replaceAll("WARNING", "주의")
    .replaceAll("HEALTHY", "양호")
}

export default function AgentPage() {
  const [loading, setLoading] = useState<boolean>(false)
  const [loadingMessage, setLoadingMessage] = useState<string>("")
  const [results, setResults] = useState<AnalysisResult[]>([])
  const [keys, setKeys] = useState<AvailableKeyContext[]>([])
  const [teamBoard, setTeamBoard] = useState<TeamBoardItem[]>([])
  const [teamGroups, setTeamGroups] = useState<TeamGroup[]>([])
  const [selectedTeamId, setSelectedTeamId] = useState<number | null>(null)
  const [showTeamList, setShowTeamList] = useState<boolean>(true)
  const [bootstrapError, setBootstrapError] = useState<string>("")
  const [currentUserId, setCurrentUserId] = useState<number | null>(null)
  const [modelCatalog, setModelCatalog] = useState<ModelCatalogSnapshot | null>(null)
  const modelCatalogStats = useMemo(() => {
    if (!modelCatalog) return null
    const activeModels = modelCatalog.models.filter((model: ModelCatalogSnapshot["models"][number]) => {
      return (model.status ?? "ACTIVE").toUpperCase() === "ACTIVE"
    })
    const byProvider = activeModels.reduce<Record<string, number>>((acc: Record<string, number>, model: ModelCatalogSnapshot["models"][number]) => {
      const provider = (model.provider ?? "UNKNOWN").toUpperCase()
      acc[provider] = (acc[provider] ?? 0) + 1
      return acc
    }, {})
    return {
      activeCount: activeModels.length,
      providerCounts: byProvider,
    }
  }, [modelCatalog])

  /** `p:{keyId}` 개인 키, `t:{teamId}:{teamApiKeyId}` 팀 키 → yyyy-MM-dd */
  const [billingByLedgerKey, setBillingByLedgerKey] = useState<Record<string, string>>({})

  const selectedTeamKeys = useMemo(
    () => (selectedTeamId == null ? [] : teamBoard.filter((item: TeamBoardItem) => item.teamId === selectedTeamId)),
    [teamBoard, selectedTeamId],
  )
  const selectedTeamLabel = useMemo(
    () => teamGroups.find((group: TeamGroup) => group.teamId === selectedTeamId)?.teamName ?? "",
    [teamGroups, selectedTeamId],
  )

  useEffect(() => {
    void loadAvailableContext()
  }, [])

  useEffect(() => {
    if (typeof window === "undefined") return
    setBillingByLedgerKey((prev: Record<string, string>) => {
      const next = { ...prev }
      for (const k of keys) {
        const lk = ledgerKeyPersonal(k.keyId)
        if (!(lk in next)) {
          const fromStore = readBillingFromStorage(storagePathPersonalKey(k.keyId))
          if (fromStore) next[lk] = fromStore
        }
      }
      for (const t of teamBoard) {
        const lk = ledgerKeyTeam(t.teamId, t.teamApiKeyId)
        if (!(lk in next)) {
          const fromStore = readBillingFromStorage(storagePathTeamKey(t.teamId, t.teamApiKeyId))
          if (fromStore) next[lk] = fromStore
        }
      }
      return next
    })
  }, [keys, teamBoard])

  const loadAvailableContext = async () => {
    setBootstrapError("")
    try {
      try {
        const catalogResponse = await fetch("/agent/api/v1/agents/model-catalog", { cache: "no-store" })
        if (catalogResponse.ok) {
          const catalogPayload = (await catalogResponse.json()) as ModelCatalogSnapshot
          setModelCatalog(catalogPayload)
        }
      } catch {
        // ignore model catalog fetch failure
      }

      let payload: AvailableContextPayload = {}
      for (let attempt = 0; attempt < 3; attempt += 1) {
        const response = await fetch("/agent/api/v1/agents/available-context", { cache: "no-store" })
        if (!response.ok) {
          const message = await response.text()
          throw new Error(message || "컨텍스트 조회 실패")
        }
        payload = (await response.json()) as AvailableContextPayload
        const hasPersonalKeys = (payload.data?.length ?? 0) > 0
        const hasTeamData =
          (payload.teamBoard?.length ?? 0) > 0 || (payload.teamGroups?.length ?? 0) > 0
        if (hasPersonalKeys || hasTeamData || attempt === 2) {
          break
        }
        await new Promise((resolve) => setTimeout(resolve, 800))
      }

      const normalized = (payload.data ?? []).map((item: AvailableContextKeyPayload) => ({
        keyId: item.keyId,
        keyLabel: item.alias,
        provider: item.provider,
        monthlyBudgetUsd: item.monthlyBudgetUsd,
        status: item.status,
        budgetStats: item.budgetStats,
        providerStats: item.providerStats,
      }))
      setKeys(normalized)
      setCurrentUserId(payload.currentUserId ?? null)
      const nextTeamBoard = payload.teamBoard ?? []
      const nextTeamGroups = payload.teamGroups ?? []
      setTeamBoard(nextTeamBoard)
      setTeamGroups(nextTeamGroups)
      setSelectedTeamId((prev: number | null) => {
        if (prev != null && nextTeamGroups.some((group: TeamGroup) => group.teamId === prev)) return prev
        return nextTeamGroups[0]?.teamId ?? null
      })
    } catch (error) {
      const message = error instanceof Error ? error.message : "컨텍스트 조회 실패"
      setBootstrapError(message)
    }
  }

  const runAnalysis = async (scope: AnalysisScope) => {
    if (scope === "TEAM" && selectedTeamId == null) {
      return
    }
    const targetKeys: AvailableKeyContext[] =
      scope === "PERSONAL"
        ? keys
        : selectedTeamKeys.map((item: TeamBoardItem) => ({
            keyId: item.teamApiKeyId,
            teamIdForBilling: item.teamId,
            keyLabel: item.alias,
            provider: item.provider,
            monthlyBudgetUsd: item.monthlyBudgetUsd ?? 0,
            status: item.status,
            providerStats: item.providerStats ?? {
              currentSpendUsd: 0,
              averageDailySpendUsd: 0,
              averageDailyTokenUsage: 0,
              recentDailySpendUsd: [],
            },
          }))
    if (targetKeys.length === 0) return

    setLoading(true)
    setResults([])
    const nextResults: AnalysisResult[] = []
    try {
      for (let i = 0; i < targetKeys.length; i += 1) {
        const keyItem = targetKeys[i]
        setLoadingMessage(
          scope === "PERSONAL"
            ? `개인 API 키 사용량을 분석 중입니다... (${i + 1}/${targetKeys.length})`
            : `${selectedTeamLabel || `Team ${selectedTeamId}`} 키 사용량을 분석 중입니다... (${i + 1}/${targetKeys.length})`,
        )

        try {
          if (scope === "PERSONAL" && currentUserId == null) {
            nextResults.push({
              keyId: keyItem.keyId,
              keyLabel: keyItem.keyLabel,
              provider: keyItem.provider,
              error:
                "개인 키 분석에는 사용자 식별이 필요합니다. (헤더/스냅샷에 userId 없음) 팀 키 분석은 팀 선택 후 시도해 주세요.",
            })
            continue
          }
          const forecast = resolveForecastInputs(keyItem.providerStats, keyItem.monthlyBudgetUsd)
          const resolvedTeamId = keyItem.teamIdForBilling ?? selectedTeamId ?? null
          if (scope === "TEAM" && resolvedTeamId == null) {
            nextResults.push({
              keyId: keyItem.keyId,
              keyLabel: keyItem.keyLabel,
              provider: keyItem.provider,
              error: "팀 식별 정보를 확인할 수 없어 팀 키 분석을 진행할 수 없습니다.",
            })
            continue
          }
          const resolvedTeamIdNumber = resolvedTeamId ?? 0
          const billingLedgerKey =
            scope === "PERSONAL"
              ? ledgerKeyPersonal(keyItem.keyId)
              : ledgerKeyTeam(resolvedTeamIdNumber, keyItem.keyId)
          const billingCycleIso = (billingByLedgerKey[billingLedgerKey] ?? "").trim()
          const response = await fetch("/agent/api/v1/agents/budget-forecast-assistant", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              userId: scope === "PERSONAL" ? String(currentUserId) : String(resolvedTeamId),
              teamId: scope === "PERSONAL" ? null : resolvedTeamIdNumber,
              keyId: keyItem.keyId,
              provider: keyItem.provider,
              model: keyItem.provider,
              monthlyBudgetUsd: keyItem.monthlyBudgetUsd,
              currentSpendUsd: keyItem.providerStats.currentSpendUsd,
              remainingTokens: forecast.remainingTokens,
              averageDailyTokenUsage: forecast.averageDailyTokenUsage,
              averageDailySpendUsd: forecast.averageDailySpendUsd,
              billingCycleEndDate: billingCycleIso !== "" ? billingCycleIso : null,
              recentDailySpendUsd: forecast.recentDailySpendUsd,
            }),
          })

          if (!response.ok) {
            const text = await response.text()
            throw new Error(text || `요청 실패 (${response.status})`)
          }

          const data = (await response.json()) as BudgetForecastResponse
          let recommendation: RecommendationQueryResponse | undefined
          let recommendationError: string | undefined
          const recommendationScopeType = scope
          const recommendationScopeId =
            scope === "PERSONAL" ? String(currentUserId) : String(resolvedTeamIdNumber)
          try {
            const analyzeResponse = await fetch("/agent/api/v1/agents/policy-recommendations/analyze", {
              method: "POST",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify({
                scopeType: recommendationScopeType,
                scopeId: recommendationScopeId,
                keyId: String(keyItem.keyId),
                windowDays: 7,
                triggeredBy: "WEB_DASHBOARD",
              }),
            })
            if (!analyzeResponse.ok) {
              const text = await analyzeResponse.text()
              throw new Error(text || `추천 분석 실패 (${analyzeResponse.status})`)
            }

            const recommendationResponse = await fetch(
              `/agent/api/v1/agents/policy-recommendations/${keyItem.keyId}?scopeType=${encodeURIComponent(
                recommendationScopeType,
              )}&scopeId=${encodeURIComponent(recommendationScopeId)}`,
              { cache: "no-store" },
            )
            if (!recommendationResponse.ok) {
              const text = await recommendationResponse.text()
              throw new Error(text || `추천 조회 실패 (${recommendationResponse.status})`)
            }
            recommendation = (await recommendationResponse.json()) as RecommendationQueryResponse
          } catch (recommendationRequestError) {
            recommendationError =
              recommendationRequestError instanceof Error
                ? recommendationRequestError.message
                : "추천 시스템 호출 실패"
          }
          nextResults.push({
            keyId: keyItem.keyId,
            keyLabel: keyItem.keyLabel,
            provider: keyItem.provider,
            data,
            recommendation,
            recommendationError,
            forecastGaps: forecast.gaps.length > 0 ? forecast.gaps : undefined,
          })
        } catch (error) {
          const message = error instanceof Error ? error.message : "분석 요청 실패"
          nextResults.push({
            keyId: keyItem.keyId,
            keyLabel: keyItem.keyLabel,
            provider: keyItem.provider,
            error: message,
          })
        }
      }

      setResults(nextResults)
    } finally {
      setLoading(false)
      setLoadingMessage("")
    }
  }

  return (
    <div className="grid min-h-[70vh] gap-4 p-4 md:grid-cols-12">
      <aside className="space-y-4 rounded-xl border bg-card p-4 md:col-span-3">
        <div className="space-y-2">
          <h3 className="text-sm font-semibold">개인 API 키</h3>
          <p className="text-[11px] leading-snug text-muted-foreground">
            키마다 콘솔의 다음 결제·청구일을 넣으면 해당 키 예측에만 반영됩니다. 이 브라우저에만 저장됩니다.
          </p>
          {bootstrapError ? (
            <div className="rounded-md border border-red-200 bg-red-50 px-2 py-1 text-xs text-red-700">{bootstrapError}</div>
          ) : null}
          <ul className="space-y-1 text-sm text-muted-foreground">
            {keys.map((item: AvailableKeyContext) => (
              <li key={item.keyId} className="rounded-md border px-2 py-1">
                {item.keyLabel}
                <span className="text-xs"> ({item.provider})</span>
                <div className="text-xs text-muted-foreground">
                  남은 예산 ${((item.budgetStats?.remainingBudgetUsd ?? item.monthlyBudgetUsd) || 0).toFixed(2)} / 사용률{" "}
                  {((item.budgetStats?.budgetUsagePercent ?? 0) || 0).toFixed(1)}%
                </div>
                <div className="mt-1 flex flex-col gap-1 border-t border-border/60 pt-1">
                  <label className="text-[11px] text-muted-foreground" htmlFor={`billing-p-${item.keyId}`}>
                    다음 결제일
                  </label>
                  <div className="flex flex-wrap items-center gap-1">
                    <input
                      id={`billing-p-${item.keyId}`}
                      type="date"
                      className="min-w-0 flex-1 rounded border bg-background px-1 py-0.5 text-xs"
                      value={billingByLedgerKey[ledgerKeyPersonal(item.keyId)] ?? ""}
                      onChange={(event: ChangeEvent<HTMLInputElement>) => {
                        const value = event.target.value
                        const lk = ledgerKeyPersonal(item.keyId)
                        setBillingByLedgerKey((prev: Record<string, string>) => ({ ...prev, [lk]: value }))
                        writeBillingToStorage(storagePathPersonalKey(item.keyId), value)
                      }}
                    />
                    {(billingByLedgerKey[ledgerKeyPersonal(item.keyId)] ?? "").trim() ? (
                      <button
                        type="button"
                        className="shrink-0 text-[11px] text-muted-foreground underline"
                        onClick={() => {
                          const lk = ledgerKeyPersonal(item.keyId)
                          setBillingByLedgerKey((prev: Record<string, string>) => ({ ...prev, [lk]: "" }))
                          writeBillingToStorage(storagePathPersonalKey(item.keyId), "")
                        }}
                      >
                        지우기
                      </button>
                    ) : null}
                  </div>
                </div>
              </li>
            ))}
            {keys.length === 0 ? (
              <li className="rounded-md border border-dashed px-2 py-1 text-xs">표시할 개인 API 키가 없습니다.</li>
            ) : null}
          </ul>
        </div>

        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-semibold">팀 보드</h3>
            <button
              type="button"
              className="rounded-md border px-2 py-1 text-xs"
              onClick={() => setShowTeamList((prev: boolean) => !prev)}
            >
              {showTeamList ? "팀 키 숨기기" : "팀 키 보기"}
            </button>
          </div>
          <label className="text-xs text-muted-foreground" htmlFor="team-selector">
            팀 선택
          </label>
          <select
            id="team-selector"
            className="w-full rounded-md border bg-background px-2 py-1 text-sm"
            value={selectedTeamId ?? ""}
            onChange={(event: ChangeEvent<HTMLSelectElement>) => {
              const value = event.target.value
              setSelectedTeamId(value ? Number(value) : null)
            }}
            disabled={teamGroups.length === 0}
          >
            <option value="">팀을 선택하세요</option>
            {teamGroups.map((group: TeamGroup) => (
              <option key={group.teamId} value={group.teamId}>
                {group.teamName}
              </option>
            ))}
          </select>
          {showTeamList ? (
            <ul className="space-y-1 text-sm text-muted-foreground">
              {selectedTeamKeys.map((item: TeamBoardItem) => (
                <li key={`${item.teamId}-${item.teamApiKeyId}`} className="rounded-md border px-2 py-1">
                  {item.alias}
                  <div className="text-xs text-muted-foreground">
                    남은 예산 ${((item.budgetStats?.remainingBudgetUsd ?? item.monthlyBudgetUsd ?? 0) || 0).toFixed(2)} / 사용률{" "}
                    {((item.budgetStats?.budgetUsagePercent ?? 0) || 0).toFixed(1)}%
                  </div>
                  <div className="mt-1 flex flex-col gap-1 border-t border-border/60 pt-1">
                    <label
                      className="text-[11px] text-muted-foreground"
                      htmlFor={`billing-t-${item.teamId}-${item.teamApiKeyId}`}
                    >
                      다음 결제일
                    </label>
                    <div className="flex flex-wrap items-center gap-1">
                      <input
                        id={`billing-t-${item.teamId}-${item.teamApiKeyId}`}
                        type="date"
                        className="min-w-0 flex-1 rounded border bg-background px-1 py-0.5 text-xs"
                        value={billingByLedgerKey[ledgerKeyTeam(item.teamId, item.teamApiKeyId)] ?? ""}
                        onChange={(event: ChangeEvent<HTMLInputElement>) => {
                          const value = event.target.value
                          const lk = ledgerKeyTeam(item.teamId, item.teamApiKeyId)
                          setBillingByLedgerKey((prev: Record<string, string>) => ({ ...prev, [lk]: value }))
                          writeBillingToStorage(storagePathTeamKey(item.teamId, item.teamApiKeyId), value)
                        }}
                      />
                      {(billingByLedgerKey[ledgerKeyTeam(item.teamId, item.teamApiKeyId)] ?? "").trim() ? (
                        <button
                          type="button"
                          className="shrink-0 text-[11px] text-muted-foreground underline"
                          onClick={() => {
                            const lk = ledgerKeyTeam(item.teamId, item.teamApiKeyId)
                            setBillingByLedgerKey((prev: Record<string, string>) => ({ ...prev, [lk]: "" }))
                            writeBillingToStorage(storagePathTeamKey(item.teamId, item.teamApiKeyId), "")
                          }}
                        >
                          지우기
                        </button>
                      ) : null}
                    </div>
                  </div>
                </li>
              ))}
              {selectedTeamId != null && selectedTeamKeys.length === 0 ? (
                <li className="rounded-md border border-dashed px-2 py-1 text-xs">선택된 팀의 키가 없습니다.</li>
              ) : null}
              {selectedTeamId == null ? (
                <li className="rounded-md border border-dashed px-2 py-1 text-xs">팀을 선택하면 팀 키를 보여줍니다.</li>
              ) : null}
              {teamGroups.length === 0 ? (
                <li className="rounded-md border border-dashed px-2 py-1 text-xs">표시할 팀이 없습니다.</li>
              ) : null}
            </ul>
          ) : (
            <p className="rounded-md border border-dashed px-2 py-1 text-xs text-muted-foreground">팀 키 목록이 숨겨져 있습니다.</p>
          )}
        </div>

        <div className="grid gap-2">
          <button
            type="button"
            className="w-full rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:opacity-60"
            onClick={() => void runAnalysis("PERSONAL")}
            disabled={loading || keys.length === 0}
          >
            {loading ? "분석 중..." : "개인 키 분석 시작"}
          </button>
          <button
            type="button"
            className="w-full rounded-md border bg-background px-4 py-2 text-sm font-medium disabled:opacity-60"
            onClick={() => void runAnalysis("TEAM")}
            disabled={loading || selectedTeamId == null || selectedTeamKeys.length === 0}
          >
            {loading ? "분석 중..." : "선택 팀 키 분석 시작"}
          </button>
        </div>
        {modelCatalog ? (
          <div className="rounded-md border border-dashed bg-muted/30 p-2 text-xs text-muted-foreground">
            <p>
              모델 카탈로그: <span className="font-medium">{modelCatalog.source || "unknown"}</span>
            </p>
            <p>모델 수: {modelCatalog.models?.length ?? 0}개</p>
            <p>ACTIVE 모델 수: {modelCatalogStats?.activeCount ?? 0}개</p>
            <p>갱신 시각: {modelCatalog.updatedAt ? new Date(modelCatalog.updatedAt).toLocaleString() : "-"}</p>
            {modelCatalog.lastRefreshSucceeded === false ? (
              <p className="mt-1 rounded border border-amber-300 bg-amber-50 px-2 py-1 text-[11px] text-amber-800">
                갱신 실패 경고: 최근 카탈로그 갱신에 실패했습니다.
                {modelCatalog.lastRefreshError ? ` (원인: ${modelCatalog.lastRefreshError})` : ""}
              </p>
            ) : null}
          </div>
        ) : null}
      </aside>

      <section className="space-y-4 md:col-span-9">
        {loading ? (
          <div className="flex min-h-[260px] flex-col items-center justify-center gap-3 rounded-xl border bg-card p-6 text-center">
            <div className="h-10 w-10 animate-spin rounded-full border-4 border-muted border-t-primary" />
            <p className="text-sm text-muted-foreground">
              {loadingMessage || "개인 API 키 사용량을 분석 중입니다..."}
            </p>
          </div>
        ) : null}

        {!loading && results.length > 0
          ? results.map((result: AnalysisResult) => (
              <article key={result.keyId} className="space-y-3 rounded-xl border bg-card p-4">
                <div className="flex items-center justify-between gap-2">
                  <h2 className="text-lg font-semibold">{result.keyLabel} ({result.provider})</h2>
                  {result.data ? (
                    <span className={`rounded-full px-2 py-1 text-xs font-semibold ${statusClassName(result.data.healthStatus)}`}>
                      {localizedHealthStatus(result.data.healthStatus)}
                    </span>
                  ) : null}
                </div>

                {result.error ? (
                  <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">{result.error}</div>
                ) : null}

                {result.forecastGaps != null && result.forecastGaps.length > 0 ? (
                  <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
                    <p className="font-medium">일부는 이벤트가 없어 표시·추정이 제한됩니다.</p>
                    <ul className="mt-2 list-disc space-y-1 pl-5">
                      {result.forecastGaps.map((line: string) => (
                        <li key={`${result.keyId}-gap-${line}`}>{line}</li>
                      ))}
                    </ul>
                  </div>
                ) : null}

                {result.data ? (
                  <>
                    <div className="grid gap-2 text-sm md:grid-cols-2">
                      <p>예상 소진일: {result.data.predictedRunOutDate}</p>
                      <p>소진까지 남은 일수: {result.data.daysUntilRunOut}일</p>
                      <p>결제일까지 남은 일수: {formatBillingMetric(result.data.daysUntilBillingCycleEnd)}</p>
                      <p>결제일 차이(결제일-소진일): {formatBillingMetric(result.data.billingDateGapDays)}</p>
                      <p>
                        예산 사용률:{" "}
                        {typeof result.data.budgetUtilizationPercent === "number"
                          ? result.data.budgetUtilizationPercent.toFixed(2)
                          : result.data.budgetUtilizationPercent}
                        %
                      </p>
                    </div>
                    <p className="text-xs text-muted-foreground">
                      소진까지 남은 일수 = 하루에 얼마나 쓰는지(소모 속도, velocity)로 미래를 예측한 값
                    </p>

                    <div className="rounded-md bg-muted p-3 text-sm">
                      {localizeAssistantMessage(result.data.assistantMessage)}
                    </div>

                    <ul className="list-disc space-y-1 pl-5 text-sm">
                      {result.data.recommendedActions.map((action: string) => (
                        <li key={`${result.keyId}-${action}`}>{action}</li>
                      ))}
                    </ul>

                    {result.recommendationError ? (
                      <div className="rounded-md border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
                        모델 추천 조회에 실패했습니다: {result.recommendationError}
                      </div>
                    ) : null}

                    {result.recommendation?.status === "RECOMMENDATION_AVAILABLE" &&
                    result.recommendation.recommendationDetails ? (
                      <div className="space-y-2 rounded-md border bg-background p-3">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <p className="text-sm font-semibold">{result.recommendation.recommendationDetails.title}</p>
                          <span className="rounded-full bg-blue-100 px-2 py-1 text-xs font-medium text-blue-700">
                            신뢰도 {result.recommendation.recommendationDetails.confidenceLevel}
                          </span>
                        </div>
                        <p className="text-sm text-muted-foreground">{result.recommendation.recommendationDetails.reasonMessage}</p>
                        <p className="text-sm">
                          예상 절감률:{" "}
                          {typeof result.recommendation.recommendationDetails.estimatedSavingsPct === "number"
                            ? result.recommendation.recommendationDetails.estimatedSavingsPct.toFixed(2)
                            : result.recommendation.recommendationDetails.estimatedSavingsPct}
                          %
                        </p>
                        {(() => {
                          const primaryCandidate = result.recommendation?.recommendationDetails?.candidates?.[0]
                          const estimatedSavingsUsd = primaryCandidate
                            ? estimateSavingsUsd(
                                result.recommendation.recommendationDetails.estimatedSavingsPct,
                                primaryCandidate.expectedMonthlyCostUsd,
                              )
                            : null
                          return estimatedSavingsUsd != null ? (
                            <p className="text-sm font-medium text-emerald-700">
                              예상 절감액(월): ${estimatedSavingsUsd.toFixed(2)}
                            </p>
                          ) : null
                        })()}
                        {result.recommendation.recommendationDetails.disclaimer ? (
                          <p className="text-xs text-amber-700">{result.recommendation.recommendationDetails.disclaimer}</p>
                        ) : null}
                        {result.recommendation.metricsContext ? (
                          <div className="space-y-2 rounded-md border border-dashed bg-muted/30 p-2">
                            <p className="text-xs font-medium text-muted-foreground">추천 근거 지표</p>
                            <div className="grid gap-2 md:grid-cols-2">
                              {(() => {
                                const ratio = parseInputOutputRatio(result.recommendation?.metricsContext?.inputOutputRatio)
                                const dominance = ratioDominance(ratio)
                                return (
                                  <div className="space-y-1 rounded border bg-background px-2 py-1.5">
                                    <div className="flex items-center justify-between">
                                      <p className="text-xs font-medium">입출력 비율</p>
                                      <span className={`rounded-full px-2 py-0.5 text-[10px] font-medium ${dominance.className}`}>
                                        {dominance.label}
                                      </span>
                                    </div>
                                    <p className="text-xs text-muted-foreground">
                                      {result.recommendation?.metricsContext?.inputOutputRatio ?? "N/A"}
                                    </p>
                                    <div className="h-1.5 w-full overflow-hidden rounded bg-muted">
                                      <div className="h-full bg-blue-500" style={{ width: `${dominance.inputPct}%` }} />
                                    </div>
                                    <p className="text-[10px] text-muted-foreground">
                                      input {dominance.inputPct.toFixed(0)}% / output {dominance.outputPct.toFixed(0)}%
                                    </p>
                                  </div>
                                )
                              })()}
                              {(() => {
                                const latency = Number(result.recommendation?.metricsContext?.averageLatencyMs ?? 0)
                                const latencyMeta = latencyStatus(latency)
                                return (
                                  <div className="space-y-1 rounded border bg-background px-2 py-1.5">
                                    <div className="flex items-center justify-between">
                                      <p className="text-xs font-medium">최근 평균 지연</p>
                                      <span className={`rounded-full px-2 py-0.5 text-[10px] font-medium ${latencyMeta.className}`}>
                                        {latencyMeta.label}
                                      </span>
                                    </div>
                                    <p className="text-xs text-muted-foreground">{latency.toFixed(0)} ms</p>
                                    <div className="h-1.5 w-full overflow-hidden rounded bg-muted">
                                      <div className="h-full bg-amber-500" style={{ width: `${latencyMeta.progress}%` }} />
                                    </div>
                                  </div>
                                )
                              })()}
                            </div>
                          </div>
                        ) : null}
                        <ul className="space-y-1 text-sm">
                          {result.recommendation.recommendationDetails.candidates.map((candidate) => (
                            <li key={`${result.keyId}-${candidate.modelName}`} className="rounded border px-2 py-1">
                              <p className="font-medium">{candidate.modelName}</p>
                              <p className="text-xs text-muted-foreground">{candidate.keyFeature}</p>
                              <p className="text-xs text-muted-foreground">
                                예상 월 비용 ${Number(candidate.expectedMonthlyCostUsd).toFixed(2)} / 변화율{" "}
                                {typeof candidate.expectedCostDiffPct === "number"
                                  ? candidate.expectedCostDiffPct.toFixed(2)
                                  : candidate.expectedCostDiffPct}
                                %
                              </p>
                            </li>
                          ))}
                        </ul>
                      </div>
                    ) : null}
                  </>
                ) : null}
              </article>
            ))
          : null}
      </section>
    </div>
  )
}
