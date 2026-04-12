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
  estimatedCostUsd: number | string
}

export type UsageLogApiKeyItemResponse = {
  apiKeyId: string
}

export type UsageLogEntryResponse = {
  eventId: string
  occurredAt: string
  correlationId: string | null
  provider: string
  model: string
  apiKeyId: string | null
  promptTokens: number | null
  completionTokens: number | null
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
