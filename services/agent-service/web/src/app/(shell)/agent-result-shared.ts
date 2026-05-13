import type { BudgetForecastResponse } from "./analysis-service"
import type { RecommendationQueryResponse } from "./recommendation-service"

export type AnalysisResult = {
  keyId: number
  keyLabel: string
  provider: string
  data?: BudgetForecastResponse
  recommendation?: RecommendationQueryResponse
  error?: string
  recommendationError?: string
  forecastGaps?: string[]
}

/** 브라우저 `localStorage`에 쌓는 분석·추천 결과 스냅샷(날짜별 과거 기록 탭용). */
export type AnalysisHistorySnapshot = {
  id: string
  savedAt: string
  dateKey: string
  results: AnalysisResult[]
}
