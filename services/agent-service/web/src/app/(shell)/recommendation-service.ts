import type { AnalysisScope, AvailableKeyContext } from "./agent-key-shared"

export type RecommendationQueryResponse = {
  keyId: string
  keyType: "PERSONAL" | "TEAM" | string
  status: "RECOMMENDATION_AVAILABLE" | "NO_RECOMMENDATION" | string
  generatedAt: string
  metricsContext?: {
    analysisWindowDays: number
    totalTokensUsed: number
    inputOutputRatio: string
    averageLatencyMs?: number | null
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

type RecommendationAnalyzeBatchResponse = {
  results: Array<{
    keyId: string
    recommendation: RecommendationQueryResponse | null
  }>
}

export async function requestRecommendation(params: {
  scope: AnalysisScope
  keyItem: AvailableKeyContext
  currentUserId: number | null
  resolvedTeamIdNumber: number
}): Promise<RecommendationQueryResponse> {
  const { scope, keyItem, currentUserId, resolvedTeamIdNumber } = params
  const recommendationScopeType = scope
  const recommendationScopeId = scope === "PERSONAL" ? String(currentUserId) : String(resolvedTeamIdNumber)

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
  return (await recommendationResponse.json()) as RecommendationQueryResponse
}

export async function requestRecommendationsBatch(params: {
  scope: AnalysisScope
  keyItems: AvailableKeyContext[]
  currentUserId: number | null
  resolvedTeamIdNumber: number
}): Promise<Record<number, RecommendationQueryResponse>> {
  const { scope, keyItems, currentUserId, resolvedTeamIdNumber } = params
  const recommendationScopeType = scope
  const recommendationScopeId = scope === "PERSONAL" ? String(currentUserId) : String(resolvedTeamIdNumber)
  const analyzeResponse = await fetch("/agent/api/v1/agents/policy-recommendations/analyze/batch", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      requests: keyItems.map((keyItem) => ({
        scopeType: recommendationScopeType,
        scopeId: recommendationScopeId,
        keyId: String(keyItem.keyId),
        windowDays: 7,
        triggeredBy: "WEB_DASHBOARD",
      })),
    }),
  })
  if (!analyzeResponse.ok) {
    const text = await analyzeResponse.text()
    throw new Error(text || `추천 배치 분석 실패 (${analyzeResponse.status})`)
  }
  const payload = (await analyzeResponse.json()) as RecommendationAnalyzeBatchResponse
  const byKeyId: Record<number, RecommendationQueryResponse> = {}
  for (const item of payload.results ?? []) {
    const parsedKeyId = Number(item.keyId)
    if (!Number.isFinite(parsedKeyId) || item.recommendation == null) {
      continue
    }
    byKeyId[parsedKeyId] = item.recommendation
  }
  return byKeyId
}
