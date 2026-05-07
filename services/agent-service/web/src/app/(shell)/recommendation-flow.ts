import type { AnalysisScope, AvailableKeyContext } from "./agent-key-shared"
import type { AnalysisResult } from "./agent-result-shared"
import { requestRecommendation } from "./recommendation-service"

export async function runRecommendationFlow(params: {
  scope: AnalysisScope
  targetKeys: AvailableKeyContext[]
  currentUserId: number | null
  selectedTeamId: number | null
  selectedTeamLabel: string
  setLoadingMessage: (message: string) => void
}): Promise<AnalysisResult[]> {
  const { scope, targetKeys, currentUserId, selectedTeamId, selectedTeamLabel, setLoadingMessage } = params
  const nextResults: AnalysisResult[] = []
  for (let i = 0; i < targetKeys.length; i += 1) {
    const keyItem = targetKeys[i]
    setLoadingMessage(
      scope === "PERSONAL"
        ? `개인 API 키 모델 추천을 분석 중입니다... (${i + 1}/${targetKeys.length})`
        : `${selectedTeamLabel || `Team ${selectedTeamId}`} 키 모델 추천을 분석 중입니다... (${i + 1}/${targetKeys.length})`,
    )
    try {
      if (scope === "PERSONAL" && currentUserId == null) {
        nextResults.push({
          keyId: keyItem.keyId,
          keyLabel: keyItem.keyLabel,
          provider: keyItem.provider,
          recommendationError: "개인 키 추천에는 사용자 식별이 필요합니다. (헤더/스냅샷에 userId 없음)",
        })
        continue
      }
      const resolvedTeamId = keyItem.teamIdForBilling ?? selectedTeamId ?? null
      if (scope === "TEAM" && resolvedTeamId == null) {
        nextResults.push({
          keyId: keyItem.keyId,
          keyLabel: keyItem.keyLabel,
          provider: keyItem.provider,
          recommendationError: "팀 식별 정보를 확인할 수 없어 팀 키 추천을 진행할 수 없습니다.",
        })
        continue
      }
      const recommendation = await requestRecommendation({
        scope,
        keyItem,
        currentUserId,
        resolvedTeamIdNumber: resolvedTeamId ?? 0,
      })
      nextResults.push({
        keyId: keyItem.keyId,
        keyLabel: keyItem.keyLabel,
        provider: keyItem.provider,
        recommendation,
      })
    } catch (error) {
      const recommendationError = error instanceof Error ? error.message : "추천 시스템 호출 실패"
      nextResults.push({
        keyId: keyItem.keyId,
        keyLabel: keyItem.keyLabel,
        provider: keyItem.provider,
        recommendationError,
      })
    }
  }
  return nextResults
}
