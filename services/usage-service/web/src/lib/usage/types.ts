/** Usage 서비스 DTO와 동일한 JSON 필드(camelCase). */

export type UsageSummaryResponse = {
  totalRequests: number
  totalErrors: number
  totalInputTokens: number
  totalEstimatedCost: number | string
}

export type DailyUsagePoint = {
  date: string
  requestCount: number
  errorCount: number
  inputTokens: number
  estimatedCost: number | string
}

export type MonthlyUsagePoint = {
  yearMonth: string
  requestCount: number
  errorCount: number
  inputTokens: number
  estimatedCost: number | string
}

export type ModelUsageAggregate = {
  model: string
  provider: string
  requestCount: number
  inputTokens: number
  estimatedReasoningTokens: number
  outputTokens: number
}

export type UsageCostIntradayKpiResponse = {
  todayEstimatedCostUsd: number | string
  yesterdaySameWindowEstimatedCostUsd: number | string
  changeRatePercent: number | string | null
  comparisonWindowEnd: string
  kstDateToday: string
}

export type HourlyUsagePoint = {
  hour: number
  requestCount: number
  errorCount: number
  estimatedCostUsd: number | string
}

export type UsageSeriesUnit = "HOUR" | "DAY" | "MONTH"

export type UsageSeriesPoint = {
  bucketLabel: string
  requestCount: number
  errorCount: number
  inputTokens: number
  estimatedCost: number | string
}

export type UsageLogApiKeyItemResponse = {
  apiKeyId: string
  alias: string | null
  status: "ACTIVE" | "DELETION_REQUESTED" | "DELETED" | null
}

export type UsageLogEntryResponse = {
  eventId: string
  occurredAt: string
  correlationId: string | null
  provider: string
  model: string
  apiKeyId: string | null
  apiKeyAlias: string | null
  apiKeyStatus: "ACTIVE" | "DELETION_REQUESTED" | "DELETED" | null
  promptTokens: number | null
  completionTokens: number | null
  estimatedReasoningTokens: number | null
  promptCachedTokens: number | null
  promptAudioTokens: number | null
  completionReasoningTokens: number | null
  completionAudioTokens: number | null
  completionAcceptedPredictionTokens: number | null
  completionRejectedPredictionTokens: number | null
  totalTokens: number | null
  estimatedCost: number | string | null
  requestPath: string | null
  upstreamHost: string | null
  streaming: boolean | null
  requestSuccessful: boolean
  upstreamStatusCode: number | null
}

export type PagedLogsResponse = {
  content: UsageLogEntryResponse[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type UsageProviderFilter = "OPENAI" | "ANTHROPIC" | "GOOGLE"
