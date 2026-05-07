import type { AnalysisScope, AvailableKeyContext } from "./agent-key-shared"

export type BudgetForecastResponse = {
  healthStatus: "HEALTHY" | "WARNING" | "CRITICAL" | string
  healthStatusLabel?: string
  riskCriteria?: string
  confidenceLevel?: "HIGH" | "MEDIUM" | "LOW" | string
  confidenceCriteria?: string
  predictedRunOutDate: string
  daysUntilRunOut: number
  daysUntilBillingCycleEnd: number | null
  billingDateGapDays: number | null
  budgetUtilizationPercent: string | number
  assistantMessage: string
  recommendedActions: string[]
  anomalySummary?: string
  routingRecommendation?: string
  estimatedRoutingSavingsPercent?: string | number
}

export type ForecastInput = {
  averageDailySpendUsd: number
  averageDailyTokenUsage: number
  remainingTokens: number
  recentDailySpendUsd: number[]
  recentDailyTokenUsage7d: number[]
  modelUsageDistribution7d: Array<{ model: string; percentage: number }>
  hourlyTokenUsage24h: number[]
  gaps: string[]
}

type BudgetForecastBatchResponse = {
  results: Array<{
    keyId: number
    forecast: BudgetForecastResponse | null
  }>
}

export async function requestBudgetForecast(params: {
  scope: AnalysisScope
  keyItem: AvailableKeyContext
  currentUserId: number | null
  resolvedTeamId: number | null
  forecast: ForecastInput
  billingCycleIso: string
}): Promise<BudgetForecastResponse> {
  const { scope, keyItem, currentUserId, resolvedTeamId, forecast, billingCycleIso } = params
  const resolvedTeamIdNumber = resolvedTeamId ?? 0
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
      recentDailyTokenUsage7d: forecast.recentDailyTokenUsage7d,
      modelUsageDistribution7d: forecast.modelUsageDistribution7d,
      hourlyTokenUsage24h: forecast.hourlyTokenUsage24h,
    }),
  })
  if (!response.ok) {
    const text = await response.text()
    throw new Error(text || `요청 실패 (${response.status})`)
  }
  return (await response.json()) as BudgetForecastResponse
}

export async function requestBudgetForecastBatch(params: {
  scope: AnalysisScope
  keyItems: AvailableKeyContext[]
  currentUserId: number | null
  resolvedTeamId: number | null
  forecastByKeyId: Record<number, ForecastInput>
  billingCycleByKeyId: Record<number, string>
}): Promise<Record<number, BudgetForecastResponse>> {
  const { scope, keyItems, currentUserId, resolvedTeamId, forecastByKeyId, billingCycleByKeyId } = params
  const resolvedTeamIdNumber = resolvedTeamId ?? 0
  const response = await fetch("/agent/api/v1/agents/budget-forecast-assistant/batch", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      requests: keyItems.map((keyItem) => {
        const forecast = forecastByKeyId[keyItem.keyId]
        const billingCycleIso = billingCycleByKeyId[keyItem.keyId] ?? ""
        return {
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
          recentDailyTokenUsage7d: forecast.recentDailyTokenUsage7d,
          modelUsageDistribution7d: forecast.modelUsageDistribution7d,
          hourlyTokenUsage24h: forecast.hourlyTokenUsage24h,
        }
      }),
    }),
  })
  if (!response.ok) {
    const text = await response.text()
    throw new Error(text || `배치 요청 실패 (${response.status})`)
  }
  const payload = (await response.json()) as BudgetForecastBatchResponse
  const byKeyId: Record<number, BudgetForecastResponse> = {}
  for (const item of payload.results ?? []) {
    if (!Number.isFinite(item.keyId) || item.forecast == null) {
      continue
    }
    byKeyId[item.keyId] = item.forecast
  }
  return byKeyId
}
