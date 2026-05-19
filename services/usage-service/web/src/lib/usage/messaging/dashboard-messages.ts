import type { LatencyInsightResponse } from "@/lib/usage/types"

/** 로딩·빈 상태·안내 배너·힌트 등 화면 표시용 문구 */
export const DASHBOARD_LOADING = {
  main: "불러오는 중…",
} as const

export const DASHBOARD_EMPTY = {
  chartAggregated: "집계 데이터 없음",
} as const

export const DASHBOARD_BANNERS = {
  noTeamMembership: "팀에 속하게 되면 팀 대시보드 사용이 가능해집니다.",
} as const

export const DASHBOARD_HINTS = {
  noTeams: "소속 팀이 있으면 팀을 선택해 팀 키 기준 나의 사용량을 확인할 수 있습니다.",
  noApiKeys: "선택한 팀에 등록된 API Key가 없거나, 해당 공급사에 맞는 팀 키가 없습니다.",
  noUsageData: "선택한 기간·공급사에 대한 사용 데이터가 없습니다",
} as const

export type DashboardMessagesDataContext = "PERSONAL" | "TEAM_MEMBER_ONLY"

export function resolveEmptyDashboardHint(
  dataContext: DashboardMessagesDataContext,
  memberHasTeams: boolean,
  apiKeyOptionsLength: number,
): string {
  if (dataContext === "TEAM_MEMBER_ONLY" && !memberHasTeams) {
    return DASHBOARD_HINTS.noTeams
  }
  if (dataContext === "TEAM_MEMBER_ONLY" && apiKeyOptionsLength === 0) {
    return DASHBOARD_HINTS.noApiKeys
  }
  return DASHBOARD_HINTS.noUsageData
}

/** 지연 인사이트 배너 정적 문구 */
export const LATENCY_INSIGHTS = {
  noData: "선택 구간에 지연(latency) 데이터가 없거나 부족합니다.",
  noCompare: "이전 동일 길이 구간의 평균 지연과 비교할 수 없습니다.",
} as const

export function latencyInsightBannerText(
  insight: LatencyInsightResponse | null,
  comparePhrase: string,
  formatLatencyMs: (ms: number | null | undefined) => string,
): string {
  if (!insight || insight.currentAvgLatencyMs == null) {
    return LATENCY_INSIGHTS.noData
  }
  if (insight.previousAvgLatencyMs == null) {
    return LATENCY_INSIGHTS.noCompare
  }
  const cp = insight.changePercent
  const currentFormatted = formatLatencyMs(insight.currentAvgLatencyMs)
  if (cp == null) {
    return `평균 응답 지연은 ${currentFormatted}입니다.`
  }
  const abs = Math.abs(cp).toFixed(1)
  if (Math.abs(cp) < 0.05) {
    return `평균 응답 지연이 ${comparePhrase}와 거의 같습니다 (${currentFormatted}).`
  }
  const improved = cp < 0
  if (improved) {
    return `평균 응답 지연이 ${comparePhrase} ${abs}% 개선되었습니다 (현재 ${currentFormatted}).`
  }
  return `평균 응답 지연이 ${comparePhrase} ${abs}% 악화되었습니다 (현재 ${currentFormatted}).`
}

/** 마스킹된 API/팀 목록 오류 및 클라이언트 검증 문구 */
export const DASHBOARD_ERRORS = {
  validation: {
    endBeforeStart: "종료일은 시작일보다 앞설 수 없습니다.",
    maxRangeDays: "조회 기간은 최대 1년(366일)까지 가능합니다.",
  },
  mainSystem: "대시보드 데이터를 불러오지 못했습니다. (Code: ERR_MAIN_500)",
  memberTeamsEnv: "시스템 오류가 발생했습니다. 잠시 후 다시 시도해 주세요. (Code: ERR_ENV_500)",
  memberTeamsList: "소속된 팀 정보를 불러오지 못했습니다. (Code: ERR_TEAM_LIST)",
  logTags: {
    main: "Dashboard Main Error",
    memberTeams: "Member Teams Fetch Error",
  },
  internalLog: {
    missingTeamBffBase: "사용량 API 베이스 URL을 확인할 수 없습니다",
    teamsPayloadNotArray: "teams payload is not an array",
  },
} as const

const DASHBOARD_CLIENT_VALIDATION_MESSAGES = new Set<string>(Object.values(DASHBOARD_ERRORS.validation))

export function isDashboardClientValidationMessage(message: string): boolean {
  return DASHBOARD_CLIENT_VALIDATION_MESSAGES.has(message)
}

export function toDashboardMainErrorMessage(error: unknown): string {
  if (error instanceof Error && isDashboardClientValidationMessage(error.message)) {
    return error.message
  }
  return DASHBOARD_ERRORS.mainSystem
}

export function logDashboardMainError(error: unknown, context?: Record<string, unknown>): void {
  const originalMessage = error instanceof Error ? error.message : String(error)
  console.error(`[${DASHBOARD_ERRORS.logTags.main}]`, {
    ...context,
    originalMessage,
    error,
  })
}

export function logDashboardMemberTeamsError(context: Record<string, unknown>): void {
  console.error(`[${DASHBOARD_ERRORS.logTags.memberTeams}]`, context)
}
