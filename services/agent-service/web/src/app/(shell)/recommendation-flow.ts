import type { AnalysisScope, AvailableKeyContext } from "./agent-key-shared"
import type { AnalysisResult } from "./agent-result-shared"
import { requestRecommendationsBatch } from "./recommendation-service"

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
  setLoadingMessage(
    scope === "PERSONAL"
      ? `개인 API 키 모델 추천을 배치 분석 중입니다... (1/${targetKeys.length})`
      : `${selectedTeamLabel || `Team ${selectedTeamId}`} 키 모델 추천을 배치 분석 중입니다... (1/${targetKeys.length})`,
  )
  if (scope === "PERSONAL" && currentUserId == null) {
    return targetKeys.map((keyItem) => ({
      keyId: keyItem.keyId,
      keyLabel: keyItem.keyLabel,
      provider: keyItem.provider,
      recommendationError: "개인 키 추천에는 사용자 식별이 필요합니다. (헤더/스냅샷에 userId 없음)",
    }))
  }
  const resolvedTeamId = scope === "TEAM" ? (selectedTeamId ?? null) : (selectedTeamId ?? 0)
  if (scope === "TEAM" && resolvedTeamId == null) {
    return targetKeys.map((keyItem) => ({
      keyId: keyItem.keyId,
      keyLabel: keyItem.keyLabel,
      provider: keyItem.provider,
      recommendationError: "팀 식별 정보를 확인할 수 없어 팀 키 추천을 진행할 수 없습니다.",
    }))
  }
  try {
    const recommendationByKeyId = await requestRecommendationsBatch({
      scope,
      keyItems: targetKeys,
      currentUserId,
      resolvedTeamIdNumber: Number(resolvedTeamId ?? 0),
    })
    for (const keyItem of targetKeys) {
      const recommendation = recommendationByKeyId[keyItem.keyId]
      nextResults.push({
        keyId: keyItem.keyId,
        keyLabel: keyItem.keyLabel,
        provider: keyItem.provider,
        recommendation,
        recommendationError: recommendation == null ? "추천 결과를 찾을 수 없습니다." : undefined,
      })
    }
  } catch (error) {
    const recommendationError = error instanceof Error ? error.message : "추천 시스템 호출 실패"
    for (const keyItem of targetKeys) {
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
