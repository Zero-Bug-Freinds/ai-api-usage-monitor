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
