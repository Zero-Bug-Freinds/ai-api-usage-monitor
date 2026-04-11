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
}

export type UsageLogApiKeyItem = {
  apiKeyId: string
}

export type UsageLogEntryResponse = {
  eventId: string
  occurredAt: string
  correlationId: string | null
  provider: string
  apiKeyId: string | null
  model: string
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

export type UsageCostIntradayKpiResponse = {
  kstDate: string
  comparisonWindowEnd: string
  todayEstimatedCost: number | string
  yesterdaySameWindowEstimatedCost: number | string
  changeRatePercent: number | string | null
}

export type HourlyUsagePoint = {
  hour: number
  requestCount: number
  estimatedCost: number | string
}

export type PeriodMode = "today" | "7d" | "30d" | "custom"
