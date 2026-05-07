import type { AnalysisScope, AvailableKeyContext } from "./agent-key-shared"
import type { AnalysisResult } from "./agent-result-shared"
import { requestBudgetForecastBatch, type ForecastInput } from "./analysis-service"

export async function runBudgetAnalysisFlow(params: {
  scope: AnalysisScope
  targetKeys: AvailableKeyContext[]
  currentUserId: number | null
  selectedTeamId: number | null
  selectedTeamLabel: string
  billingByLedgerKey: Record<string, string>
  personalLedgerKey: (keyId: number) => string
  teamLedgerKey: (teamId: number, teamApiKeyId: number) => string
  resolveForecastInputs: (stats: AvailableKeyContext["providerStats"], monthlyBudgetUsd: number) => ForecastInput
  setLoadingMessage: (message: string) => void
}): Promise<AnalysisResult[]> {
  const {
    scope,
    targetKeys,
    currentUserId,
    selectedTeamId,
    selectedTeamLabel,
    billingByLedgerKey,
    personalLedgerKey,
    teamLedgerKey,
    resolveForecastInputs,
    setLoadingMessage,
  } = params
  const nextResults: AnalysisResult[] = []
  setLoadingMessage(
    scope === "PERSONAL"
      ? `개인 API 키 사용량을 배치 분석 중입니다... (1/${targetKeys.length})`
      : `${selectedTeamLabel || `Team ${selectedTeamId}`} 키 사용량을 배치 분석 중입니다... (1/${targetKeys.length})`,
  )
  if (scope === "PERSONAL" && currentUserId == null) {
    return targetKeys.map((keyItem) => ({
      keyId: keyItem.keyId,
      keyLabel: keyItem.keyLabel,
      provider: keyItem.provider,
      error: "개인 키 분석에는 사용자 식별이 필요합니다. (헤더/스냅샷에 userId 없음) 팀 키 분석은 팀 선택 후 시도해 주세요.",
    }))
  }
  const resolvedTeamId = scope === "TEAM" ? (selectedTeamId ?? null) : (selectedTeamId ?? 0)
  if (scope === "TEAM" && resolvedTeamId == null) {
    return targetKeys.map((keyItem) => ({
      keyId: keyItem.keyId,
      keyLabel: keyItem.keyLabel,
      provider: keyItem.provider,
      error: "팀 식별 정보를 확인할 수 없어 팀 키 분석을 진행할 수 없습니다.",
    }))
  }
  const forecastByKeyId: Record<number, ForecastInput> = {}
  const billingCycleByKeyId: Record<number, string> = {}
  for (const keyItem of targetKeys) {
    const resolvedTeamIdNumber = Number(resolvedTeamId ?? 0)
    const billingLedgerKey =
      scope === "PERSONAL" ? personalLedgerKey(keyItem.keyId) : teamLedgerKey(resolvedTeamIdNumber, keyItem.keyId)
    billingCycleByKeyId[keyItem.keyId] = (billingByLedgerKey[billingLedgerKey] ?? "").trim()
    forecastByKeyId[keyItem.keyId] = resolveForecastInputs(keyItem.providerStats, keyItem.monthlyBudgetUsd)
  }
  try {
    const forecastResultByKeyId = await requestBudgetForecastBatch({
      scope,
      keyItems: targetKeys,
      currentUserId,
      resolvedTeamId: resolvedTeamId == null ? null : Number(resolvedTeamId),
      forecastByKeyId,
      billingCycleByKeyId,
    })
    for (const keyItem of targetKeys) {
      const forecast = forecastByKeyId[keyItem.keyId]
      const data = forecastResultByKeyId[keyItem.keyId]
      nextResults.push({
        keyId: keyItem.keyId,
        keyLabel: keyItem.keyLabel,
        provider: keyItem.provider,
        data,
        forecastGaps: forecast.gaps.length > 0 ? forecast.gaps : undefined,
        error: data == null ? "분석 결과를 찾을 수 없습니다." : undefined,
      })
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : "분석 요청 실패"
    for (const keyItem of targetKeys) {
      nextResults.push({
        keyId: keyItem.keyId,
        keyLabel: keyItem.keyLabel,
        provider: keyItem.provider,
        error: message,
      })
    }
  }
  return nextResults
}
