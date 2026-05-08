export type AvailableKeyContext = {
  keyId: number
  /** 팀 키 분석 시에만 설정 (결제일 저장·요청 구분용) */
  teamIdForBilling?: number
  keyLabel: string
  provider: string
  monthlyBudgetUsd: number
  status: string
  budgetStats?: {
    currentSpendUsd: number
    remainingBudgetUsd: number
    budgetUsagePercent: number
    isBudgetExceeded: boolean
  }
  providerStats: {
    currentSpendUsd: number
    averageDailySpendUsd: number
    averageDailyTokenUsage: number
    recentDailySpendUsd: number[]
  }
}

export type TeamBoardItem = {
  teamId: number
  teamName?: string
  teamApiKeyId: number
  ownerUserId?: string | null
  visibility?: string | null
  alias: string
  provider: string
  status: string
  monthlyBudgetUsd?: number
  budgetStats?: {
    currentSpendUsd: number
    remainingBudgetUsd: number
    budgetUsagePercent: number
    isBudgetExceeded: boolean
  }
  providerStats?: {
    currentSpendUsd: number
    averageDailySpendUsd: number
    averageDailyTokenUsage: number
    recentDailySpendUsd: number[]
  }
}

export type TeamGroup = {
  teamId: number
  teamName: string
  keys: TeamBoardItem[]
}

export type AnalysisScope = "PERSONAL" | "TEAM"

export function resolveTargetKeys(
  scope: AnalysisScope,
  keys: AvailableKeyContext[],
  selectedTeamKeys: TeamBoardItem[],
): AvailableKeyContext[] {
  if (scope === "PERSONAL") {
    return keys
  }
  return selectedTeamKeys.map((item: TeamBoardItem) => ({
    keyId: item.teamApiKeyId,
    teamIdForBilling: item.teamId,
    keyLabel: item.alias,
    provider: item.provider,
    monthlyBudgetUsd: item.monthlyBudgetUsd ?? 0,
    status: item.status,
    providerStats: item.providerStats ?? {
      currentSpendUsd: 0,
      averageDailySpendUsd: 0,
      averageDailyTokenUsage: 0,
      recentDailySpendUsd: [],
    },
  }))
}
