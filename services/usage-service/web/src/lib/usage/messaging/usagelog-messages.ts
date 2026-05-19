const COPY_ENV_SYSTEM_ERROR =
  "시스템 오류가 발생했습니다. 잠시 후 다시 시도해 주세요. (Code: ERR_ENV_500)"

/** 상세 로그 (`usage-log-panel.tsx`) 전용 문구 */
export const USAGELOG_MESSAGES = {
  loading: {
    main: "로그를 불러오는 중입니다…",
  },
  empty: {
    noData: "사용 데이터가 없습니다",
  },
  errors: {
    logsFetch: "로그 데이터를 불러오지 못했습니다. (Code: ERR_LOG_500)",
    memberTeamsFooter: "팀 목록을 로드하지 못했습니다. (Code: ERR_LOG_TEAM)",
    memberTeamsEnv: COPY_ENV_SYSTEM_ERROR,
  },
  logTags: {
    logsFetch: "Usage Log Fetch Error",
    memberTeamsFetch: "Usage Log Member Teams Fetch Error",
    teamApiKeysFetch: "Usage Log Team Api Keys Fetch Error",
    personalApiKeysFetch: "Usage Log Personal Api Keys Fetch Error",
  },
  internalLog: {
    missingTeamBffBase: "사용량 API 베이스 URL을 확인할 수 없습니다",
  },
} as const

export function toUsageLogFetchErrorMessage(_error: unknown): string {
  return USAGELOG_MESSAGES.errors.logsFetch
}

export function logUsageLogFetchError(context: Record<string, unknown>): void {
  console.error(`[${USAGELOG_MESSAGES.logTags.logsFetch}]`, context)
}

export function logUsageLogMemberTeamsFetch(context: Record<string, unknown>): void {
  console.error(`[${USAGELOG_MESSAGES.logTags.memberTeamsFetch}]`, context)
}

export function logUsageLogTeamApiKeysFetch(context: Record<string, unknown>): void {
  console.error(`[${USAGELOG_MESSAGES.logTags.teamApiKeysFetch}]`, context)
}

export function logUsageLogPersonalApiKeysFetch(context: Record<string, unknown>): void {
  console.error(`[${USAGELOG_MESSAGES.logTags.personalApiKeysFetch}]`, context)
}
