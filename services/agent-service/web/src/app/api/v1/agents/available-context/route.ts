import { NextResponse } from "next/server"

type IdentitySnapshot = {
  keyId: number
  userId: number
  alias: string
  provider: string
  visibility?: string
  status: string
  monthlyBudgetUsd?: number | null
}

type UserContextSnapshot = {
  userId: number
  activeTeamId?: number | null
  role?: string | null
}

type TeamApiKeySnapshot = {
  teamId: number
  teamName?: string | null
  teamApiKeyId: number
  ownerUserId?: string | null
  visibility?: string | null
  alias: string
  provider: string
  status: string
  monthlyBudgetUsd?: number | null
}

type TeamSnapshot = {
  teamId: number
  teamName?: string | null
}

type BillingSignal = {
  apiKeyId: string
  latestEstimatedCostUsd?: number | null
  budgetThreshold?: {
    thresholdPct?: number | null
    monthlyTotalUsd?: number | null
    monthlyBudgetUsd?: number | null
  } | null
}

type TeamApiResponse<T> = {
  success?: boolean
  message?: string
  data?: T
}

type TeamBillingApiKey = {
  apiKeyId?: number | string
  provider?: string | null
  alias?: string | null
  monthlyBudgetUsd?: number | null
}

type TeamBillingSummary = {
  teamId?: number | string
  teamAlias?: string | null
  apiKeys?: TeamBillingApiKey[] | null
}

type TeamCatalog = {
  teams: TeamSnapshot[]
  keys: TeamApiKeySnapshot[]
}

function backendOriginCandidates(): string[] {
  const configured = (process.env.AI_AGENT_SERVICE_INTERNAL_ORIGIN ?? "").trim().replace(/\/$/, "")
  const defaults = ["http://localhost:8096", "http://host.docker.internal:8096", "http://agent-service:8096"]
  return Array.from(new Set([configured, ...defaults].filter((value) => value.length > 0)))
}

function teamServiceOriginCandidates(): string[] {
  const configured = (process.env.TEAM_SERVICE_INTERNAL_ORIGIN ?? "").trim().replace(/\/$/, "")
  const defaults = ["http://localhost:8093", "http://host.docker.internal:8093", "http://team-service:8093"]
  return Array.from(new Set([configured, ...defaults].filter((value) => value.length > 0)))
}

async function fetchJsonWithFallback<T>(path: string): Promise<T[]> {
  const origins = backendOriginCandidates()
  for (const origin of origins) {
    try {
      const response = await fetch(`${origin}${path}`, { cache: "no-store" })
      if (!response.ok) continue
      return ((await response.json()) as T[]) ?? []
    } catch {
      // Try next origin candidate.
    }
  }
  return []
}

function userIdFromHeaders(request: Request): string {
  const candidates = ["x-user-id", "X-User-Id", "x-userid", "X-Userid"]
  for (const header of candidates) {
    const value = request.headers.get(header)
    if (value && value.trim().length > 0) {
      return value.trim()
    }
  }
  const fallbackUserId = (process.env.AI_AGENT_FALLBACK_USER_ID ?? "").trim()
  if (fallbackUserId.length > 0) return fallbackUserId
  return ""
}

async function fetchTeamCatalogFromTeamService(request: Request): Promise<TeamCatalog> {
  const userId = userIdFromHeaders(request)
  if (!userId) {
    return { teams: [], keys: [] }
  }

  for (const origin of teamServiceOriginCandidates()) {
    try {
      const teamListResponse = await fetch(`${origin}/internal/teams/users/${encodeURIComponent(userId)}/billing-summaries`, {
        cache: "no-store",
      })
      if (!teamListResponse.ok) continue

      const teamListPayload = (await teamListResponse.json()) as TeamApiResponse<TeamBillingSummary[]>
      const summaries = teamListPayload.data ?? []
      const teams: TeamSnapshot[] = []
      const keys: TeamApiKeySnapshot[] = []

      for (const summary of summaries) {
        const teamId = toNumber(summary.teamId, 0)
        if (teamId <= 0) continue

        const teamName = (summary.teamAlias ?? "").trim() || `Team ${teamId}`
        teams.push({ teamId, teamName })

        const teamKeys = summary.apiKeys ?? []
        for (const key of teamKeys) {
          const teamApiKeyId = toNumber(key.apiKeyId, 0)
          if (teamApiKeyId <= 0) continue
          keys.push({
            teamId,
            teamName,
            teamApiKeyId,
            ownerUserId: userId,
            visibility: "TEAM",
            alias: (key.alias ?? "").trim() || `team-key-${teamApiKeyId}`,
            provider: (key.provider ?? "").trim() || "UNKNOWN",
            status: "ACTIVE",
            monthlyBudgetUsd: toNumber(key.monthlyBudgetUsd, 0),
          })
        }
      }

      return { teams, keys }
    } catch {
      // try next candidate
    }
  }

  return { teams: [], keys: [] }
}

function toNumber(value: unknown, fallback = 0): number {
  if (typeof value === "number" && Number.isFinite(value)) return value
  if (typeof value === "string") {
    const parsed = Number(value)
    if (Number.isFinite(parsed)) return parsed
  }
  return fallback
}

export async function GET(request: Request) {
  try {
    const [keys, billingSignals, userContexts, snapshotTeamApiKeys, teamSnapshots] = await Promise.all([
      fetchJsonWithFallback<IdentitySnapshot>("/api/v1/agents/identity-api-keys"),
      fetchJsonWithFallback<BillingSignal>("/api/v1/agents/billing-signals"),
      fetchJsonWithFallback<UserContextSnapshot>("/api/v1/agents/user-contexts"),
      fetchJsonWithFallback<TeamApiKeySnapshot>("/api/v1/agents/team-api-keys"),
      fetchJsonWithFallback<TeamSnapshot>("/api/v1/agents/teams"),
    ])
    const teamCatalog = await fetchTeamCatalogFromTeamService(request)

    const teamApiKeys = teamCatalog.keys.length > 0 ? teamCatalog.keys : snapshotTeamApiKeys
    const billingByKeyId = new Map<string, BillingSignal>(billingSignals.map((item) => [item.apiKeyId, item]))

    const personalKeys = keys.map((key) => {
      const signal = billingByKeyId.get(String(key.keyId))
      const currentSpendUsd = toNumber(signal?.latestEstimatedCostUsd, 0)
      const thresholdPct = toNumber(signal?.budgetThreshold?.thresholdPct, 0)
      const recentDailySpendUsd = currentSpendUsd > 0 ? [currentSpendUsd] : []

      return {
        keyId: key.keyId,
        alias: key.alias,
        provider: key.provider,
        monthlyBudgetUsd: toNumber(key.monthlyBudgetUsd, 0),
        status: key.status,
        providerStats: {
          currentSpendUsd,
          averageDailySpendUsd: Math.max(currentSpendUsd / 7, 0.01),
          averageDailyTokenUsage: 1,
          recentDailySpendUsd,
        },
        billingThresholdPct: thresholdPct,
      }
    })

    const activeTeamContext = userContexts.find((ctx) => ctx.activeTeamId != null) ?? null
    const teamBoard = teamApiKeys.map((item) => {
      const signal = billingByKeyId.get(String(item.teamApiKeyId))
      const currentSpendUsd = toNumber(signal?.latestEstimatedCostUsd, 0)
      const thresholdPct = toNumber(signal?.budgetThreshold?.thresholdPct, 0)
      return {
        teamId: item.teamId,
        teamName: item.teamName?.trim() || `Team ${item.teamId}`,
        teamApiKeyId: item.teamApiKeyId,
        ownerUserId: item.ownerUserId,
        visibility: item.visibility ?? "TEAM",
        alias: item.alias,
        provider: item.provider,
        status: item.status,
        monthlyBudgetUsd: toNumber(signal?.budgetThreshold?.monthlyBudgetUsd, toNumber(item.monthlyBudgetUsd, 0)),
        providerStats: {
          currentSpendUsd,
          averageDailySpendUsd: Math.max(currentSpendUsd / 7, 0.01),
          averageDailyTokenUsage: 1,
          recentDailySpendUsd: currentSpendUsd > 0 ? [currentSpendUsd] : [],
        },
        billingThresholdPct: thresholdPct,
      }
    })

    const teamNameById = new Map<number, string>()
    for (const item of teamCatalog.teams) {
      const teamId = toNumber(item.teamId, 0)
      if (teamId <= 0) continue
      const teamName = item.teamName?.trim() || `Team ${teamId}`
      teamNameById.set(teamId, teamName)
    }
    for (const snapshot of teamSnapshots) {
      const teamId = toNumber(snapshot.teamId, 0)
      if (teamId <= 0) continue
      const teamName = snapshot.teamName?.trim() || `Team ${teamId}`
      teamNameById.set(teamId, teamName)
    }
    for (const item of teamBoard) {
      const teamName = item.teamName?.trim() || `Team ${item.teamId}`
      if (!teamNameById.has(item.teamId)) {
        teamNameById.set(item.teamId, teamName)
      }
    }
    const teamGroups = Array.from(teamNameById.entries()).map(([teamId, teamName]) => ({
      teamId,
      teamName,
      keys: teamBoard.filter((item) => item.teamId === teamId),
    }))

    return NextResponse.json({
      data: personalKeys,
      userContext: activeTeamContext,
      teamBoard,
      teamGroups,
      note: "RabbitMQ 이벤트 스냅샷 기반(Identity + Team + Billing). 이벤트가 아직 없으면 목록이 비어 있을 수 있습니다.",
    })
  } catch {
    return NextResponse.json(
      { message: "이벤트 스냅샷 컨텍스트 조회에 실패했습니다." },
      { status: 502 },
    )
  }
}
