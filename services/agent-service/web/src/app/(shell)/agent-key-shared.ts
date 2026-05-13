export type AvailableKeyContext = {
  keyId: number
  /** 동일 제공자·별칭(재등록 등)으로 묶인 다른 external key ID 목록; 단일이면 [keyId] */
  mergedKeyIds?: number[]
  /** 팀 키 분석 시에만 설정 (결제일 저장·요청 구분용) */
  teamIdForBilling?: number
  keyLabel: string
  provider: string
  monthlyBudgetUsd: number
  status: string
  budgetStats?: {
    currentSpendUsd: number
    lifetimeSpendUsd?: number
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
  /** 동일 팀·제공자·별칭으로 묶인 팀 API 키 ID 목록 */
  mergedTeamApiKeyIds?: number[]
  ownerUserId?: string | null
  visibility?: string | null
  alias: string
  provider: string
  status: string
  monthlyBudgetUsd?: number
  budgetStats?: {
    currentSpendUsd: number
    lifetimeSpendUsd?: number
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
  return selectedTeamKeys.map((item: TeamBoardItem) => teamBoardItemToAvailableKeyContext(item))
}

export function teamBoardItemToAvailableKeyContext(item: TeamBoardItem): AvailableKeyContext {
  const merged = item.mergedTeamApiKeyIds ?? [item.teamApiKeyId]
  return {
    keyId: item.teamApiKeyId,
    mergedKeyIds: merged,
    teamIdForBilling: item.teamId,
    keyLabel: item.alias,
    provider: item.provider,
    monthlyBudgetUsd: item.monthlyBudgetUsd ?? 0,
    status: item.status,
    budgetStats: item.budgetStats,
    providerStats: item.providerStats ?? {
      currentSpendUsd: 0,
      averageDailySpendUsd: 0,
      averageDailyTokenUsage: 0,
      recentDailySpendUsd: [],
    },
  }
}
