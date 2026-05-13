"use client"

import { type ChangeEvent, useEffect, useMemo, useRef, useState } from "react"
import {
  type AnalysisScope,
  type AvailableKeyContext,
  type TeamBoardItem,
  type TeamGroup,
  teamBoardItemToAvailableKeyContext,
} from "./agent-key-shared"
import type { AnalysisHistorySnapshot, AnalysisResult } from "./agent-result-shared"
import { AnalysisResultArticles } from "./analysis-result-articles"
import { runBudgetAnalysisFlow } from "./analysis-flow"
import { runRecommendationFlow } from "./recommendation-flow"
import type { RecommendationPriority } from "./recommendation-service"

type AnalysisAction = "ANALYSIS" | "RECOMMENDATION"

type LoadingTarget = {
  scope: AnalysisScope
  keyId: number
  action: AnalysisAction
}

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


type AvailableContextKeyPayload = {
  keyId: number
  mergedKeyIds?: number[]
  alias: string
  provider: string
  monthlyBudgetUsd: number
  status: string
  budgetStats?: {
    currentSpendUsd: number
    lifetimeSpendUsd?: number
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

/** 과금 웹 `formatUsd`와 동일 규칙(소액 4자리, 미만은 임계 라벨). */
function formatAgentUsd(value: number): string {
  if (!Number.isFinite(value)) {
    return "—"
  }
  const decimals = 4
  const minLabel = 0.0001
  if (value > 0 && value < minLabel) {
    return `<$${minLabel.toFixed(decimals)}`
  }
  return `$${value.toFixed(decimals)}`
}

function isCredentialDeletedStatus(status: string): boolean {
  const u = (status ?? "").toUpperCase()
  return u === "DELETED" || u === "DELETION_REQUESTED"
}

function AgentKeyBudgetSummary({
  monthlyBudgetUsd,
  budgetStats,
}: {
  monthlyBudgetUsd: number
  budgetStats?: AvailableKeyContext["budgetStats"]
}) {
  const monthSpend = budgetStats?.currentSpendUsd ?? 0
  const lifetimeSpend = budgetStats?.lifetimeSpendUsd ?? 0
  const monthly = Number.isFinite(monthlyBudgetUsd) ? monthlyBudgetUsd : 0
  const remaining = budgetStats?.remainingBudgetUsd ?? Math.max(0, monthly - monthSpend)
  const pct =
    budgetStats?.budgetUsagePercent ??
    (monthly > 0 && Number.isFinite(monthSpend) ? (monthSpend / monthly) * 100 : 0)

  if (monthly <= 0) {
    return (
      <div className="space-y-0.5 text-xs text-muted-foreground">
        <p className="leading-snug">
          누적 지출(최근 400일) {formatAgentUsd(lifetimeSpend)}
          <span className="text-[10px] text-muted-foreground"> · 월 예산 미설정</span>
        </p>
        <p className="text-[10px] leading-snug">
          당월 지출 {formatAgentUsd(monthSpend)} (진행률·잔여는 월 예산이 있을 때만 계산)
        </p>
        <p className="text-[10px] leading-snug">월 예산은 identity·과금 요약에 값이 있으면 여기에도 맞춰집니다.</p>
      </div>
    )
  }

  return (
    <div className="space-y-0.5 text-xs text-muted-foreground">
      <p className="leading-snug">
        누적 지출(최근 400일) {formatAgentUsd(lifetimeSpend)} / 월 예산 ${monthly.toFixed(2)} (당월 잔여 ${remaining.toFixed(2)})
      </p>
      <p className="text-[10px] leading-snug text-muted-foreground">
        당월 지출 {formatAgentUsd(monthSpend)} · 진행률은 당월 기준
      </p>
      <div className="flex items-center justify-between gap-2 text-[11px]">
        <span className="text-muted-foreground">진행률(당월)</span>
        <span className="tabular-nums font-medium text-foreground">{pct.toFixed(1)}%</span>
      </div>
    </div>
  )
}

const MANUAL_BILLING_STORAGE_PREFIX = "agent.manualBillingCycleEnd."
const ANALYSIS_RESULTS_STORAGE_KEY = "agent.analysisResults.v1"

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

function readAnalysisResultsFromStorage(): AnalysisResult[] {
  if (typeof window === "undefined") return []
  try {
    const raw = window.localStorage.getItem(ANALYSIS_RESULTS_STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw) as AnalysisResult[]
    if (!Array.isArray(parsed)) return []
    return parsed
  } catch {
    return []
  }
}

function writeAnalysisResultsToStorage(results: AnalysisResult[]): void {
  if (typeof window === "undefined") return
  try {
    if (results.length === 0) {
      window.localStorage.removeItem(ANALYSIS_RESULTS_STORAGE_KEY)
      return
    }
    window.localStorage.setItem(ANALYSIS_RESULTS_STORAGE_KEY, JSON.stringify(results))
  } catch {
    // ignore quota / private mode
  }
}

const ANALYSIS_HISTORY_STORAGE_KEY = "agent.analysisHistory.v1"
const MAX_ANALYSIS_HISTORY_SNAPSHOTS = 80

function toLocalDateKey(iso: string): string {
  const d = new Date(iso)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, "0")
  const day = String(d.getDate()).padStart(2, "0")
  return `${y}-${m}-${day}`
}

function formatDateKeyForDisplay(dateKey: string): string {
  const parts = dateKey.split("-").map((p) => Number(p))
  const y = parts[0]
  const m = parts[1]
  const day = parts[2]
  if (!y || !m || !day) return dateKey
  const dt = new Date(y, m - 1, day)
  return dt.toLocaleDateString("ko-KR", { weekday: "short", year: "numeric", month: "long", day: "numeric" })
}

function readAnalysisHistoryFromStorage(): AnalysisHistorySnapshot[] {
  if (typeof window === "undefined") return []
  try {
    const raw = window.localStorage.getItem(ANALYSIS_HISTORY_STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw) as AnalysisHistorySnapshot[]
    if (!Array.isArray(parsed)) return []
    return parsed.filter(
      (row) =>
        row != null &&
        typeof row.id === "string" &&
        typeof row.savedAt === "string" &&
        typeof row.dateKey === "string" &&
        Array.isArray(row.results),
    )
  } catch {
    return []
  }
}

function writeAnalysisHistoryToStorage(rows: AnalysisHistorySnapshot[]): void {
  if (typeof window === "undefined") return
  try {
    if (rows.length === 0) {
      window.localStorage.removeItem(ANALYSIS_HISTORY_STORAGE_KEY)
      return
    }
    window.localStorage.setItem(ANALYSIS_HISTORY_STORAGE_KEY, JSON.stringify(rows))
  } catch {
    // ignore quota / private mode
  }
}

function createAnalysisHistorySnapshot(results: AnalysisResult[]): AnalysisHistorySnapshot {
  const savedAt = new Date().toISOString()
  return {
    id: `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`,
    savedAt,
    dateKey: toLocalDateKey(savedAt),
    results: JSON.parse(JSON.stringify(results)) as AnalysisResult[],
  }
}

function mergeAnalysisResults(prev: AnalysisResult[], nextResult: AnalysisResult): AnalysisResult[] {
  const previous = prev.find((item: AnalysisResult) => item.keyId === nextResult.keyId)
  if (!previous) {
    return [...prev, nextResult]
  }
  const merged: AnalysisResult = {
    ...previous,
    ...nextResult,
    data: nextResult.error ? nextResult.data : (nextResult.data ?? previous.data),
    recommendation: nextResult.recommendationError
      ? nextResult.recommendation
      : (nextResult.recommendation ?? previous.recommendation),
    forecastGaps: nextResult.error ? nextResult.forecastGaps : (nextResult.forecastGaps ?? previous.forecastGaps),
  }
  return prev.map((item: AnalysisResult) => (item.keyId === nextResult.keyId ? merged : item))
}

function resolveForecastInputs(
  stats: AvailableKeyContext["providerStats"],
  monthlyBudgetUsd: number,
): {
  averageDailySpendUsd: number
  averageDailyTokenUsage: number
  remainingTokens: number
  recentDailySpendUsd: number[]
  recentDailyTokenUsage7d: number[]
  modelUsageDistribution7d: Array<{ model: string; percentage: number }>
  hourlyTokenUsage24h: number[]
  gaps: string[]
  insufficientForForecast: boolean
} {
  const gaps: string[] = []
  const spendFromPrediction = stats.averageDailySpendUsd
  const tokensFromPrediction = stats.averageDailyTokenUsage
  const billedSpend = stats.currentSpendUsd
  const recentDailySpendUsd = (stats.recentDailySpendUsd ?? [])
    .map((value) => Number(value))
    .filter((value) => Number.isFinite(value) && value >= 0)
    .slice(-7)
  const hasUsageEvidence =
    spendFromPrediction > 0 || tokensFromPrediction > 0 || billedSpend > 0 || recentDailySpendUsd.some((value) => value > 0)
  const insufficientForForecast = !hasUsageEvidence
  if (insufficientForForecast) {
    gaps.push("사용량 이벤트가 없어 소진 예측을 계산할 수 없습니다. 키를 사용한 뒤 다시 시도해 주세요.")
  }

  let averageDailySpendUsd = spendFromPrediction
  if (averageDailySpendUsd <= 0 && billedSpend > 0) {
    averageDailySpendUsd = billedSpend / 7
  } else if (averageDailySpendUsd <= 0) {
    averageDailySpendUsd = 0.000001
  }

  const averageDailyTokenUsage = tokensFromPrediction > 0 ? tokensFromPrediction : 1
  const remainingBudgetUsd = Math.max(monthlyBudgetUsd - billedSpend, 0)
  const estimatedDaysByBudget =
    averageDailySpendUsd > 0 ? Math.max(remainingBudgetUsd / averageDailySpendUsd, 0) : 0
  const remainingTokens = Math.max(Math.round(averageDailyTokenUsage * estimatedDaysByBudget), 1)

  const recentDailyTokenUsage7d = Array.from({ length: 7 }, (_, index) => {
    const spend = recentDailySpendUsd[index] ?? averageDailySpendUsd
    const spendRatio = averageDailySpendUsd > 0 ? spend / averageDailySpendUsd : 1
    return Math.max(1, Math.round(averageDailyTokenUsage * spendRatio))
  })
  const modelUsageDistribution7d = [
    { model: `${stats.currentSpendUsd > monthlyBudgetUsd * 0.8 ? "gpt-4o-mini" : "gemini-2.5-flash"}`, percentage: 70 },
    { model: "claude-3-haiku", percentage: 30 },
  ]
  const hourlyTokenUsage24h = Array.from({ length: 24 }, (_, hour) => {
    const peakHours = hour >= 9 && hour <= 18
    const multiplier = peakHours ? 1.35 : 0.65
    return Math.max(0, Math.round((averageDailyTokenUsage / 24) * multiplier))
  })

  return {
    averageDailySpendUsd,
    averageDailyTokenUsage,
    remainingTokens,
    recentDailySpendUsd,
    recentDailyTokenUsage7d,
    modelUsageDistribution7d,
    hourlyTokenUsage24h,
    gaps,
    insufficientForForecast,
  }
}

export default function AgentPage() {
  const [loadingTarget, setLoadingTarget] = useState<LoadingTarget | null>(null)
  const [loadingMessage, setLoadingMessage] = useState<string>("")
  const [results, setResults] = useState<AnalysisResult[]>([])
  const [keys, setKeys] = useState<AvailableKeyContext[]>([])
  const [teamBoard, setTeamBoard] = useState<TeamBoardItem[]>([])
  const [teamGroups, setTeamGroups] = useState<TeamGroup[]>([])
  const [selectedTeamId, setSelectedTeamId] = useState<number | null>(null)
  const [showTeamList, setShowTeamList] = useState<boolean>(true)
  const [bootstrapError, setBootstrapError] = useState<string>("")
  const [currentUserId, setCurrentUserId] = useState<number | null>(null)
  const [recommendationPriority, setRecommendationPriority] = useState<RecommendationPriority>("BALANCED")
  const [hideDeletedKeys, setHideDeletedKeys] = useState<boolean>(false)
  const [modelCatalog, setModelCatalog] = useState<ModelCatalogSnapshot | null>(null)
  const [resultsHydrated, setResultsHydrated] = useState<boolean>(false)
  const [contextRefreshing, setContextRefreshing] = useState<boolean>(false)
  const [analysisHistory, setAnalysisHistory] = useState<AnalysisHistorySnapshot[]>([])
  const [mainResultsTab, setMainResultsTab] = useState<"current" | "history">("current")
  const [historySelectedDateKey, setHistorySelectedDateKey] = useState<string | null>(null)
  const [historySelectedSnapshotId, setHistorySelectedSnapshotId] = useState<string | null>(null)
  const resultsHistoryBaselineCapturedRef = useRef(false)
  const resultsHistorySigRef = useRef<string>("")
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
  const visiblePersonalKeys = useMemo(
    () =>
      hideDeletedKeys ? keys.filter((item: AvailableKeyContext) => !isCredentialDeletedStatus(item.status)) : keys,
    [hideDeletedKeys, keys],
  )
  const visibleSelectedTeamKeys = useMemo(
    () =>
      hideDeletedKeys
        ? selectedTeamKeys.filter((item: TeamBoardItem) => !isCredentialDeletedStatus(item.status))
        : selectedTeamKeys,
    [hideDeletedKeys, selectedTeamKeys],
  )
  const selectedTeamLabel = useMemo(
    () => teamGroups.find((group: TeamGroup) => group.teamId === selectedTeamId)?.teamName ?? "",
    [teamGroups, selectedTeamId],
  )

  const historyByDate = useMemo(() => {
    const map = new Map<string, AnalysisHistorySnapshot[]>()
    for (const row of analysisHistory) {
      const list = map.get(row.dateKey) ?? []
      list.push(row)
      map.set(row.dateKey, list)
    }
    for (const list of map.values()) {
      list.sort((a: AnalysisHistorySnapshot, b: AnalysisHistorySnapshot) => b.savedAt.localeCompare(a.savedAt))
    }
    return map
  }, [analysisHistory])

  const historyDateKeys = useMemo(
    () => Array.from(historyByDate.keys()).sort((a: string, b: string) => b.localeCompare(a)),
    [historyByDate],
  )

  const snapshotsForSelectedDate = useMemo(() => {
    if (!historySelectedDateKey) return []
    return historyByDate.get(historySelectedDateKey) ?? []
  }, [historyByDate, historySelectedDateKey])

  const selectedHistorySnapshot = useMemo(() => {
    if (!historySelectedSnapshotId) return null
    return analysisHistory.find((s: AnalysisHistorySnapshot) => s.id === historySelectedSnapshotId) ?? null
  }, [analysisHistory, historySelectedSnapshotId])

  useEffect(() => {
    setResults(readAnalysisResultsFromStorage())
    setAnalysisHistory(readAnalysisHistoryFromStorage())
    setResultsHydrated(true)
  }, [])

  useEffect(() => {
    void loadAvailableContext()
  }, [])

  useEffect(() => {
    if (!resultsHydrated) return
    writeAnalysisResultsToStorage(results)
  }, [results, resultsHydrated])

  useEffect(() => {
    if (!resultsHydrated) return
    const sig = JSON.stringify(results)
    if (!resultsHistoryBaselineCapturedRef.current) {
      resultsHistoryBaselineCapturedRef.current = true
      resultsHistorySigRef.current = sig
      return
    }
    if (resultsHistorySigRef.current === sig) return
    resultsHistorySigRef.current = sig
    if (results.length === 0) return
    setAnalysisHistory((prev: AnalysisHistorySnapshot[]) => {
      const snap = createAnalysisHistorySnapshot(results)
      const next = [...prev, snap].slice(-MAX_ANALYSIS_HISTORY_SNAPSHOTS)
      writeAnalysisHistoryToStorage(next)
      return next
    })
  }, [results, resultsHydrated])

  useEffect(() => {
    if (mainResultsTab !== "history") return
    if (historyDateKeys.length === 0) return
    setHistorySelectedDateKey((d: string | null) => (d && historyByDate.has(d) ? d : historyDateKeys[0]))
  }, [mainResultsTab, historyDateKeys, historyByDate])

  useEffect(() => {
    if (!historySelectedDateKey) return
    const snaps = historyByDate.get(historySelectedDateKey) ?? []
    setHistorySelectedSnapshotId((id: string | null) =>
      id && snaps.some((s: AnalysisHistorySnapshot) => s.id === id) ? id : snaps[0]?.id ?? null,
    )
  }, [historySelectedDateKey, historyByDate])

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
        mergedKeyIds: item.mergedKeyIds,
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

  const refreshAvailableContext = async () => {
    if (contextRefreshing) {
      return
    }
    setContextRefreshing(true)
    try {
      await loadAvailableContext()
    } finally {
      setContextRefreshing(false)
    }
  }

  const runAnalysisForKey = async (scope: AnalysisScope, targetKey: AvailableKeyContext, action: AnalysisAction) => {
    if (scope === "TEAM" && selectedTeamId == null) {
      return
    }
    const targetKeys: AvailableKeyContext[] = [targetKey]
    const teamIdForFlow =
      scope === "TEAM" ? (targetKey.teamIdForBilling ?? selectedTeamId) : selectedTeamId
    const teamLabelForFlow =
      scope === "TEAM"
        ? teamGroups.find((group: TeamGroup) => group.teamId === teamIdForFlow)?.teamName ?? selectedTeamLabel
        : selectedTeamLabel

    setLoadingTarget({ scope, keyId: targetKey.keyId, action })
    try {
      const nextResults =
        action === "ANALYSIS"
          ? await runBudgetAnalysisFlow({
              scope,
              targetKeys,
              currentUserId,
              selectedTeamId: teamIdForFlow,
              selectedTeamLabel: teamLabelForFlow,
              billingByLedgerKey,
              personalLedgerKey: ledgerKeyPersonal,
              teamLedgerKey: ledgerKeyTeam,
              resolveForecastInputs,
              setLoadingMessage,
            })
          : await runRecommendationFlow({
              scope,
              targetKeys,
              currentUserId,
              selectedTeamId: teamIdForFlow,
              selectedTeamLabel: teamLabelForFlow,
              recommendationPriority,
              setLoadingMessage,
            })

      const nextResult = nextResults[0]
      if (nextResult) {
        setResults((prev: AnalysisResult[]) => mergeAnalysisResults(prev, nextResult))
      }
    } finally {
      setLoadingTarget(null)
      setLoadingMessage("")
    }
  }

  const isRowLoading = (scope: AnalysisScope, keyId: number, action: AnalysisAction): boolean =>
    loadingTarget?.scope === scope && loadingTarget.keyId === keyId && loadingTarget.action === action

  const isAnyLoading = loadingTarget != null
  const contextActionsDisabled = contextRefreshing || isAnyLoading
  return (
    <div className="grid min-h-[70vh] gap-4 p-4 md:grid-cols-12">
      <aside className="space-y-4 rounded-xl border bg-card p-4 md:col-span-3">
        {isAnyLoading ? (
          <div
            className="rounded-lg border border-primary/30 bg-primary/5 px-3 py-2 text-xs shadow-sm"
            role="status"
            aria-live="polite"
          >
            <p className="font-semibold text-foreground">
              {loadingTarget?.action === "ANALYSIS" ? "예산·사용량 분석 중" : "모델 추천 분석 중"}
            </p>
            <p className="mt-1 text-[11px] leading-snug text-muted-foreground">
              {loadingMessage || "잠시만 기다려 주세요. 완료되면 오른쪽에 결과가 표시됩니다."}
            </p>
          </div>
        ) : null}
        <div className="space-y-1 rounded-md border border-border/70 bg-muted/20 p-2">
          <label htmlFor="recommendation-priority" className="text-[11px] font-medium text-foreground">
            모델 추천 우선순위
          </label>
          <select
            id="recommendation-priority"
            className="h-8 w-full rounded border bg-background px-2 text-xs"
            value={recommendationPriority}
            onChange={(event: ChangeEvent<HTMLSelectElement>) =>
              setRecommendationPriority(event.target.value as RecommendationPriority)
            }
            disabled={isAnyLoading}
          >
            <option value="BALANCED">균형</option>
            <option value="COST">비용 절감 우선</option>
            <option value="QUALITY">품질/추론 우선</option>
            <option value="LATENCY">응답 속도 우선</option>
          </select>
        </div>

        <div className="flex items-center justify-end py-1.5">
          <button
            type="button"
            className="rounded-md border border-border bg-background px-2 py-1 text-[11px] font-medium text-foreground disabled:opacity-60"
            disabled={isAnyLoading}
            onClick={() => setHideDeletedKeys((prev: boolean) => !prev)}
          >
            {hideDeletedKeys ? "삭제된 키 표시" : "삭제된 키 숨기기"}
          </button>
        </div>

        <div className="space-y-2">
          <div className="flex items-center justify-between gap-2">
            <h3 className="text-sm font-semibold">개인 API 키</h3>
            <button
              type="button"
              className="shrink-0 rounded-md border border-border bg-background px-2 py-1 text-[11px] font-medium text-foreground hover:bg-muted/60 disabled:opacity-60"
              disabled={contextActionsDisabled}
              onClick={() => void refreshAvailableContext()}
            >
              {contextRefreshing ? "불러오는 중…" : "목록 새로고침"}
            </button>
          </div>
          <p className="text-[11px] leading-snug text-muted-foreground">
            키마다 콘솔의 다음 결제·청구일을 넣으면 해당 키 예측에만 반영됩니다. 이 브라우저에만 저장됩니다.
          </p>
          {bootstrapError ? (
            <div className="rounded-md border border-red-200 bg-red-50 px-2 py-1 text-xs text-red-700">{bootstrapError}</div>
          ) : null}
          <ul className="space-y-1 text-sm text-muted-foreground">
            {visiblePersonalKeys.map((item: AvailableKeyContext) => (
              <li key={item.keyId} className="rounded-md border px-2 py-1">
                <div className="flex flex-wrap items-baseline gap-1">
                  <span>
                    {item.keyLabel}
                    <span className="text-xs"> ({item.provider})</span>
                  </span>
                  {isCredentialDeletedStatus(item.status) ? (
                    <span className="rounded bg-slate-200 px-1.5 py-0.5 text-[10px] font-semibold text-slate-700 dark:bg-slate-700 dark:text-slate-200">
                      삭제된 키
                    </span>
                  ) : null}
                </div>
                {item.mergedKeyIds != null && item.mergedKeyIds.length > 1 ? (
                  <p className="mt-0.5 text-[10px] leading-snug text-muted-foreground">
                    동일 키 해시(동일 시크릿)·또는 동일 제공자·별칭으로 병합 (키 ID: {item.mergedKeyIds.join(", ")}) — 아래 누적·당월 수치는 병합 합산입니다.
                  </p>
                ) : null}
                <AgentKeyBudgetSummary monthlyBudgetUsd={item.monthlyBudgetUsd} budgetStats={item.budgetStats} />
                <div className="mt-1 flex flex-col gap-1 border-t border-border/60 pt-1">
                  <div className="flex items-center justify-between gap-2">
                    <label className="text-[11px] text-muted-foreground" htmlFor={`billing-p-${item.keyId}`}>
                      다음 결제일
                    </label>
                    <span
                      className={`shrink-0 rounded px-1.5 py-0.5 text-[10px] font-medium ${
                        (billingByLedgerKey[ledgerKeyPersonal(item.keyId)] ?? "").trim()
                          ? "bg-emerald-100 text-emerald-800"
                          : "bg-muted text-muted-foreground"
                      }`}
                    >
                      {(billingByLedgerKey[ledgerKeyPersonal(item.keyId)] ?? "").trim() ? "선택됨" : "미선택"}
                    </span>
                  </div>
                  <div className="flex flex-wrap items-center gap-1">
                    <input
                      id={`billing-p-${item.keyId}`}
                      type="date"
                      title="달력에서 결제일 선택"
                      aria-label={`${item.keyLabel} 다음 결제일 선택`}
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
                <div className="mt-2 flex flex-wrap gap-1">
                  <button
                    type="button"
                    className="rounded-md bg-primary px-2 py-1 text-[11px] font-medium text-primary-foreground disabled:opacity-60"
                    disabled={isAnyLoading}
                    onClick={() => void runAnalysisForKey("PERSONAL", item, "ANALYSIS")}
                  >
                    {isRowLoading("PERSONAL", item.keyId, "ANALYSIS") ? "분석 중..." : "분석"}
                  </button>
                  <button
                    type="button"
                    className="rounded-md border border-border bg-background px-2 py-1 text-[11px] font-medium disabled:opacity-60"
                    disabled={isAnyLoading}
                    onClick={() => void runAnalysisForKey("PERSONAL", item, "RECOMMENDATION")}
                  >
                    {isRowLoading("PERSONAL", item.keyId, "RECOMMENDATION") ? "추천 중..." : "추천"}
                  </button>
                </div>
              </li>
            ))}
            {visiblePersonalKeys.length === 0 ? (
              <li className="rounded-md border border-dashed px-2 py-1 text-xs">
                {keys.length === 0
                  ? "표시할 개인 API 키가 없습니다."
                  : "삭제된 키만 있어 목록이 비었습니다. 위에서 「삭제된 키 표시」를 눌러 보세요."}
              </li>
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
              {visibleSelectedTeamKeys.map((item: TeamBoardItem) => (
                <li key={`${item.teamId}-${item.teamApiKeyId}`} className="rounded-md border px-2 py-1">
                  <div className="flex flex-wrap items-baseline gap-1">
                    <span>{item.alias}</span>
                    {isCredentialDeletedStatus(item.status) ? (
                      <span className="rounded bg-slate-200 px-1.5 py-0.5 text-[10px] font-semibold text-slate-700 dark:bg-slate-700 dark:text-slate-200">
                        삭제된 키
                      </span>
                    ) : null}
                  </div>
                  {item.mergedTeamApiKeyIds != null && item.mergedTeamApiKeyIds.length > 1 ? (
                    <p className="mt-0.5 text-[10px] leading-snug text-muted-foreground">
                      동일 키 해시(동일 시크릿)·또는 동일 팀·제공자·별칭으로 병합 (팀 키 ID: {item.mergedTeamApiKeyIds.join(", ")}) — 아래 수치는 병합 합산입니다.
                    </p>
                  ) : null}
                  <AgentKeyBudgetSummary
                    monthlyBudgetUsd={item.monthlyBudgetUsd ?? 0}
                    budgetStats={item.budgetStats}
                  />
                  <div className="mt-1 flex flex-col gap-1 border-t border-border/60 pt-1">
                    <div className="flex items-center justify-between gap-2">
                      <label
                        className="text-[11px] text-muted-foreground"
                        htmlFor={`billing-t-${item.teamId}-${item.teamApiKeyId}`}
                      >
                        다음 결제일
                      </label>
                      <span
                        className={`shrink-0 rounded px-1.5 py-0.5 text-[10px] font-medium ${
                          (billingByLedgerKey[ledgerKeyTeam(item.teamId, item.teamApiKeyId)] ?? "").trim()
                            ? "bg-emerald-100 text-emerald-800"
                            : "bg-muted text-muted-foreground"
                        }`}
                      >
                        {(billingByLedgerKey[ledgerKeyTeam(item.teamId, item.teamApiKeyId)] ?? "").trim()
                          ? "선택됨"
                          : "미선택"}
                      </span>
                    </div>
                    <div className="flex flex-wrap items-center gap-1">
                      <input
                        id={`billing-t-${item.teamId}-${item.teamApiKeyId}`}
                        type="date"
                        title="달력에서 결제일 선택"
                        aria-label={`${item.alias} 다음 결제일 선택`}
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
                  <div className="mt-2 flex flex-wrap gap-1">
                    <button
                      type="button"
                      className="rounded-md bg-primary px-2 py-1 text-[11px] font-medium text-primary-foreground disabled:opacity-60"
                      disabled={isAnyLoading}
                      onClick={() => void runAnalysisForKey("TEAM", teamBoardItemToAvailableKeyContext(item), "ANALYSIS")}
                    >
                      {isRowLoading("TEAM", item.teamApiKeyId, "ANALYSIS") ? "분석 중..." : "분석"}
                    </button>
                    <button
                      type="button"
                      className="rounded-md border border-border bg-background px-2 py-1 text-[11px] font-medium disabled:opacity-60"
                      disabled={isAnyLoading}
                      onClick={() =>
                        void runAnalysisForKey("TEAM", teamBoardItemToAvailableKeyContext(item), "RECOMMENDATION")
                      }
                    >
                      {isRowLoading("TEAM", item.teamApiKeyId, "RECOMMENDATION") ? "추천 중..." : "추천"}
                    </button>
                  </div>
                </li>
              ))}
              {selectedTeamId != null && visibleSelectedTeamKeys.length === 0 ? (
                <li className="rounded-md border border-dashed px-2 py-1 text-xs">
                  {selectedTeamKeys.length === 0
                    ? "선택된 팀의 키가 없습니다."
                    : "삭제된 키만 있어 목록이 비었습니다. 위에서 「삭제된 키 표시」를 눌러 보세요."}
                </li>
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

        <div className="rounded-md border border-dashed bg-muted/30 px-2 py-1.5 text-xs text-muted-foreground">
          <p>각 키의 분석·추천은 해당 키의 데이터만 AI 요청에 포함합니다.</p>
          <p className="mt-1 text-[11px] leading-snug">
            <span className="font-medium">누적 지출(최근 400일)</span>은 billing `summary(from,to)`를 활용한 최근 400일 합입니다.{" "}
            <span className="font-medium">당월 지출·진행률·잔여</span>는{" "}
            <span className="font-medium">월 1일~오늘</span>과 동일한 방식으로, 요약·스냅샷을 합친 값입니다.
            과금 서비스가 오래되면 누적 API가 없을 수 있으니 배포를 맞추고, 숫자가 비면{" "}
            <span className="font-medium">목록 새로고침</span>을 눌러 보세요.
          </p>
        </div>
        {modelCatalog ? (
          <div className="rounded-md border border-dashed bg-muted/30 p-2 text-xs text-muted-foreground">
            <p className="leading-snug">
              모델 카탈로그:{" "}
              <span className="block break-all font-medium sm:inline">
                {modelCatalog.source || "unknown"}
              </span>
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
        <div className="flex flex-wrap gap-1 border-b border-border pb-2" role="tablist" aria-label="분석 결과 보기">
          <button
            type="button"
            role="tab"
            aria-selected={mainResultsTab === "current"}
            className={`rounded-md px-3 py-1.5 text-sm font-medium ${
              mainResultsTab === "current" ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:bg-muted/60"
            }`}
            onClick={() => setMainResultsTab("current")}
          >
            현재 결과
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={mainResultsTab === "history"}
            className={`rounded-md px-3 py-1.5 text-sm font-medium ${
              mainResultsTab === "history" ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:bg-muted/60"
            }`}
            onClick={() => setMainResultsTab("history")}
          >
            과거 기록
          </button>
        </div>

        {mainResultsTab === "current" ? (
          <AnalysisResultArticles
            results={results}
            loadingTarget={loadingTarget}
            loadingMessage={loadingMessage}
            emptyLabel="분석·추천을 실행하면 여기에 결과가 나타납니다."
          />
        ) : (
          <div className="grid gap-4 pt-2 md:grid-cols-12">
            <div className="space-y-2 md:col-span-3">
              <p className="text-xs font-medium text-muted-foreground">날짜</p>
              {historyDateKeys.length === 0 ? (
                <p className="rounded-md border border-dashed px-2 py-2 text-xs text-muted-foreground">
                  저장된 과거 기록이 없습니다. 분석 또는 추천을 실행할 때마다 이 브라우저에 날짜별로 쌓입니다.
                </p>
              ) : (
                <ul className="space-y-1 text-sm">
                  {historyDateKeys.map((dk: string) => (
                    <li key={dk}>
                      <button
                        type="button"
                        className={`w-full rounded-md border px-2 py-1.5 text-left text-xs ${
                          historySelectedDateKey === dk
                            ? "border-primary bg-primary/10 font-medium"
                            : "border-border bg-background hover:bg-muted/50"
                        }`}
                        onClick={() => {
                          setHistorySelectedDateKey(dk)
                          const first = historyByDate.get(dk)?.[0]
                          setHistorySelectedSnapshotId(first?.id ?? null)
                        }}
                      >
                        {formatDateKeyForDisplay(dk)}
                        <span className="ml-1 text-[10px] text-muted-foreground">
                          ({historyByDate.get(dk)?.length ?? 0})
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
            <div className="min-w-0 space-y-3 md:col-span-9">
              {historyDateKeys.length === 0 ? null : (
                <>
                  <div className="space-y-1">
                    <p className="text-xs font-medium text-muted-foreground">저장 시각</p>
                    <div className="flex flex-wrap gap-1">
                      {snapshotsForSelectedDate.map((snap: AnalysisHistorySnapshot) => (
                        <button
                          key={snap.id}
                          type="button"
                          className={`rounded-full border px-2 py-1 text-[11px] ${
                            historySelectedSnapshotId === snap.id
                              ? "border-primary bg-primary/10 font-medium"
                              : "border-border bg-muted/30 hover:bg-muted/50"
                          }`}
                          onClick={() => setHistorySelectedSnapshotId(snap.id)}
                        >
                          {new Date(snap.savedAt).toLocaleString("ko-KR")} · 키 {snap.results.length}개
                        </button>
                      ))}
                    </div>
                  </div>
                  {selectedHistorySnapshot ? (
                    <AnalysisResultArticles
                      results={selectedHistorySnapshot.results}
                      loadingTarget={null}
                      loadingMessage=""
                      emptyLabel={null}
                    />
                  ) : (
                    <p className="text-xs text-muted-foreground">기록을 선택해 주세요.</p>
                  )}
                </>
              )}
            </div>
          </div>
        )}
      </section>
    </div>
  )
}
