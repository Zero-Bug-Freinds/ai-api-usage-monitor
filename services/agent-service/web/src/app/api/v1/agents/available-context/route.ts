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

type IdentityBudgetByKey = {
  externalApiKeyId?: number | string
  provider?: string | null
  alias?: string | null
  monthlyBudgetUsd?: number | null
}

type IdentityBudgetResponse = {
  monthlyBudgetsByKey?: IdentityBudgetByKey[] | null
}

function backendOriginCandidates(): string[] {
  const configured = (process.env.AI_AGENT_SERVICE_INTERNAL_ORIGIN ?? "").trim().replace(/\/$/, "")
  const defaults = ["http://localhost:8096", "http://host.docker.internal:8096", "http://agent-service:8096"]
  return Array.from(new Set([configured, ...defaults].filter((value) => value.length > 0)))
}

function identityServiceOriginCandidates(): string[] {
  const configured = (process.env.IDENTITY_SERVICE_INTERNAL_ORIGIN ?? "").trim().replace(/\/$/, "")
  const defaults = [
    "http://localhost:8090",
    "http://host.docker.internal:8090",
    "http://localhost:8080",
    "http://host.docker.internal:8080",
    "http://identity-service:8080",
  ]
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

function userIdAsNumber(request: Request): number | null {
  const raw = userIdFromHeaders(request)
  const parsed = Number(raw)
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return null
  }
  return parsed
}

function userIdentifierFromHeaders(request: Request): { userId: number | null; email: string | null } {
  const raw = userIdFromHeaders(request).trim()
  if (!raw) {
    return { userId: null, email: null }
  }
  const numeric = Number(raw)
  if (Number.isFinite(numeric) && numeric > 0) {
    return { userId: numeric, email: null }
  }
  if (raw.includes("@")) {
    return { userId: null, email: raw }
  }
  return { userId: null, email: null }
}

function resolveCurrentUserId(
  request: Request,
  keys: IdentitySnapshot[],
  userContexts: UserContextSnapshot[],
): number | null {
  const fromHeader = userIdAsNumber(request)
  if (fromHeader != null) {
    return fromHeader
  }

  const fromContext = userContexts.find((item) => Number.isFinite(item.userId) && item.userId > 0)?.userId ?? null
  if (fromContext != null) {
    return fromContext
  }

  const fromKeys = keys.find((item) => Number.isFinite(item.userId) && item.userId > 0)?.userId ?? null
  return fromKeys
}

async function fetchIdentityBudgetKeys(
  userId: number | null,
  email: string | null,
  fallbackEmails: string[] = [],
): Promise<IdentityBudgetByKey[]> {
  const queries: string[] = []
  if (userId != null) {
    queries.push(`/api/identity/v1/users/${userId}/budget`)
  }
  if (email != null) {
    queries.push(`/api/identity/v1/users/budget?email=${encodeURIComponent(email)}`)
  }
  for (const fallbackEmail of fallbackEmails) {
    queries.push(`/api/identity/v1/users/budget?email=${encodeURIComponent(fallbackEmail)}`)
  }
  if (queries.length === 0) return []

  for (const query of queries) {
    for (const origin of identityServiceOriginCandidates()) {
      try {
        const response = await fetch(`${origin}${query}`, { cache: "no-store" })
        if (!response.ok) continue
        const payload = (await response.json()) as IdentityBudgetResponse
        const keys = payload.monthlyBudgetsByKey ?? []
        if (keys.length > 0) {
          return keys
        }
      } catch {
        // try next candidate
      }
    }
  }
  return []
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

        const teamName = normalizeTeamName(summary.teamAlias, teamId)
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

function normalizeTeamName(value: string | null | undefined, teamId: number): string {
  const trimmed = (value ?? "").trim()
  const withoutParentheses = trimmed.replace(/\s*\([^)]*\)\s*/g, " ").replace(/\s+/g, " ").trim()
  if (withoutParentheses.length > 0) return withoutParentheses
  return `Team ${teamId}`
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
    const headerIdentifier = userIdentifierFromHeaders(request)
    const currentUserId = resolveCurrentUserId(request, keys, userContexts)
    const keysForCurrentUser = currentUserId == null ? [] : keys.filter((key) => key.userId === currentUserId)
    const userContextsForCurrentUser =
      currentUserId == null ? [] : userContexts.filter((context) => context.userId === currentUserId)

    const teamApiKeys = teamCatalog.keys.length > 0 ? teamCatalog.keys : snapshotTeamApiKeys
    const fallbackEmails = Array.from(
      new Set(
        teamApiKeys
          .map((item) => (item.ownerUserId ?? "").trim())
          .filter((value) => value.includes("@")),
      ),
    )
    const billingByKeyId = new Map<string, BillingSignal>(billingSignals.map((item) => [item.apiKeyId, item]))
    const identityBudgetKeys = await fetchIdentityBudgetKeys(currentUserId, headerIdentifier.email, fallbackEmails)

    const personalKeysFromSnapshot = keysForCurrentUser.map((key) => {
      const signal = billingByKeyId.get(String(key.keyId))
      const currentSpendUsd = toNumber(signal?.latestEstimatedCostUsd, 0)
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
      }
    })
    const personalKeysFromIdentity = identityBudgetKeys
      .map((key) => {
        const keyId = toNumber(key.externalApiKeyId, 0)
        if (keyId <= 0) {
          return null
        }
        const signal = billingByKeyId.get(String(keyId))
        const currentSpendUsd = toNumber(signal?.latestEstimatedCostUsd, 0)
        return {
          keyId,
          alias: (key.alias ?? "").trim() || `key-${keyId}`,
          provider: (key.provider ?? "").trim() || "UNKNOWN",
          monthlyBudgetUsd: toNumber(key.monthlyBudgetUsd, 0),
          status: "ACTIVE",
          providerStats: {
            currentSpendUsd,
            averageDailySpendUsd: Math.max(currentSpendUsd / 7, 0.01),
            averageDailyTokenUsage: 1,
            recentDailySpendUsd: currentSpendUsd > 0 ? [currentSpendUsd] : [],
          },
        }
      })
      .filter((item): item is NonNullable<typeof item> => item != null)
    const personalKeys = personalKeysFromIdentity.length > 0 ? personalKeysFromIdentity : personalKeysFromSnapshot

    const activeTeamContext = userContextsForCurrentUser.find((ctx) => ctx.activeTeamId != null) ?? null
    const teamBoard = teamApiKeys.map((item) => {
      const signal = billingByKeyId.get(String(item.teamApiKeyId))
      const currentSpendUsd = toNumber(signal?.latestEstimatedCostUsd, 0)
      return {
        teamId: item.teamId,
        teamName: normalizeTeamName(item.teamName, item.teamId),
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
      }
    })

    const teamNameById = new Map<number, string>()
    for (const item of teamCatalog.teams) {
      const teamId = toNumber(item.teamId, 0)
      if (teamId <= 0) continue
      const teamName = normalizeTeamName(item.teamName, teamId)
      teamNameById.set(teamId, teamName)
    }
    for (const snapshot of teamSnapshots) {
      const teamId = toNumber(snapshot.teamId, 0)
      if (teamId <= 0) continue
      const teamName = normalizeTeamName(snapshot.teamName, teamId)
      teamNameById.set(teamId, teamName)
    }
    for (const item of teamBoard) {
      const teamName = normalizeTeamName(item.teamName, item.teamId)
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
    })
  } catch {
    return NextResponse.json(
      { message: "이벤트 스냅샷 컨텍스트 조회에 실패했습니다." },
      { status: 502 },
    )
  }
}
