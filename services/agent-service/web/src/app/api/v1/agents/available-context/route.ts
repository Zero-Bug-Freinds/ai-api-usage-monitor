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

type BillingSignal = {
  apiKeyId: string
  latestEstimatedCostUsd?: number | null
}

type UsagePredictionSignal = {
  teamId?: string | null
  userId?: string | null
  averageDailySpendUsd7d?: number | null
  averageDailyTokenUsage7d?: number | null
  recentDailySpendUsd?: number[] | null
}

type DailyCumulativeTokenSignal = {
  teamId?: string | null
  userId?: string | null
  apiKeyId?: string | null
  dailyTotalTokens?: number | null
  occurredAt?: string | null
}

type BudgetStats = {
  currentSpendUsd: number
  remainingBudgetUsd: number
  budgetUsagePercent: number
  isBudgetExceeded: boolean
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
  teams: Array<{ teamId: number; teamName?: string | null }>
  keys: TeamApiKeySnapshot[]
}

type IdentityBudgetByKey = {
  externalApiKeyId?: number | string
  apiKeyId?: number | string
  provider?: string | null
  alias?: string | null
  monthlyBudgetUsd?: number | null
}

type IdentityBudgetResponse = {
  monthlyBudgetsByKey?: IdentityBudgetByKey[] | null
  data?: {
    monthlyBudgetsByKey?: IdentityBudgetByKey[] | null
  } | null
}

const ORIGIN_PROBE_TIMEOUT_MS = 3000
const CONTEXT_FETCH_TIMEOUT_MS = 10000

type SessionApiResponse = {
  success?: boolean
  data?: { email?: string | null } | null
}

function backendOriginCandidates(): string[] {
  const configured = (process.env.AI_AGENT_SERVICE_INTERNAL_ORIGIN ?? "").trim().replace(/\/$/, "")
  const defaults = ["http://localhost:8096", "http://host.docker.internal:8096", "http://agent-service:8096"]
  return Array.from(new Set([configured, ...defaults].filter((value) => value.length > 0)))
}

function identityWebOriginCandidates(): string[] {
  const configured = (process.env.IDENTITY_WEB_INTERNAL_ORIGIN ?? "").trim().replace(/\/$/, "")
  const publicOrigin = (process.env.NEXT_PUBLIC_IDENTITY_WEB_ORIGIN ?? "").trim().replace(/\/$/, "")
  const defaults = ["http://identity-web:3000", "http://host.docker.internal:3000", "http://localhost:3000"]
  return Array.from(new Set([configured, publicOrigin, ...defaults].filter((value) => value.length > 0)))
}

function identityServiceOriginCandidates(): string[] {
  const configured = (process.env.IDENTITY_SERVICE_INTERNAL_ORIGIN ?? "").trim().replace(/\/$/, "")
  const defaults = [
    "http://identity-service:8080",
    "http://host.docker.internal:8090",
    "http://localhost:8090",
    "http://host.docker.internal:8080",
    "http://localhost:8080",
    "http://identity-service:8080",
  ]
  return Array.from(new Set([configured, ...defaults].filter((value) => value.length > 0)))
}

function teamServiceOriginCandidates(): string[] {
  const configured = (process.env.TEAM_SERVICE_INTERNAL_ORIGIN ?? "").trim().replace(/\/$/, "")
  const defaults = ["http://localhost:8093", "http://host.docker.internal:8093", "http://team-service:8093"]
  return Array.from(new Set([configured, ...defaults].filter((value) => value.length > 0)))
}

async function resolveSessionEmail(request: Request): Promise<string | null> {
  const cookieHeader = request.headers.get("cookie") ?? ""
  const forwardedHeaders: HeadersInit = {
    Accept: "application/json",
  }
  if (cookieHeader.trim().length > 0) {
    forwardedHeaders.Cookie = cookieHeader
  }
  for (const origin of identityWebOriginCandidates()) {
    try {
      const response = await fetchWithTimeout(`${origin}/api/auth/session`, CONTEXT_FETCH_TIMEOUT_MS, {
        method: "GET",
        headers: forwardedHeaders,
      })
      if (!response.ok) continue
      const payload = (await response.json()) as SessionApiResponse
      if (!payload.success) continue
      const email = payload.data?.email?.trim() ?? ""
      if (email.includes("@")) return email
    } catch {
      // try next candidate
    }
  }
  return null
}

async function resolveBackendOrigin(): Promise<string | null> {
  const origins = backendOriginCandidates()
  for (const origin of origins) {
    try {
      const response = await fetchWithTimeout(`${origin}/api/v1/agents/identity-api-keys`, ORIGIN_PROBE_TIMEOUT_MS)
      if (response.ok) {
        return origin
      }
    } catch {
      // Try next origin candidate.
    }
  }
  return null
}

async function fetchWithTimeout(url: string, timeoutMs: number, init?: RequestInit): Promise<Response> {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), timeoutMs)
  try {
    return await fetch(url, {
      ...init,
      cache: "no-store",
      signal: controller.signal,
    })
  } finally {
    clearTimeout(timeout)
  }
}

function userIdFromHeaders(request: Request): string {
  const candidates = [
    "x-user-id",
    "X-User-Id",
    "x-userid",
    "X-Userid",
    "x-platform-user-id",
    "X-Platform-User-Id",
    "x-platform-userid",
    "X-Platform-Userid",
  ]
  for (const header of candidates) {
    const value = request.headers.get(header)
    if (value && value.trim().length > 0) {
      return value.trim()
    }
  }
  const fallbackUserId = (process.env.AI_AGENT_FALLBACK_USER_ID ?? "1").trim()
  if (fallbackUserId.length > 0) return fallbackUserId
  return "1"
}

function userIdAsNumber(request: Request): number | null {
  const raw = userIdFromHeaders(request)
  const parsed = Number(raw)
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return null
  }
  return parsed
}

function emailFromHeaders(request: Request): string | null {
  const emailHeaders = [
    "x-user-email",
    "X-User-Email",
    "x-email",
    "X-Email",
    "x-user-name",
    "X-User-Name",
  ]
  for (const header of emailHeaders) {
    const value = request.headers.get(header)?.trim()
    if (value && value.includes("@")) {
      return value
    }
  }
  return null
}

function userIdentifierFromHeaders(request: Request): { userId: number | null; email: string | null } {
  const raw = userIdFromHeaders(request).trim()
  const headerEmail = emailFromHeaders(request)
  if (!raw) {
    return { userId: null, email: headerEmail }
  }
  const numeric = Number(raw)
  if (Number.isFinite(numeric) && numeric > 0) {
    return { userId: numeric, email: headerEmail }
  }
  if (raw.includes("@")) {
    return { userId: null, email: raw }
  }
  return { userId: null, email: headerEmail }
}

function resolveCurrentUserId(
  request: Request,
  keys: IdentitySnapshot[],
): number | null {
  const fromHeader = userIdAsNumber(request)
  if (fromHeader != null) {
    return fromHeader
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
        const response = await fetchWithTimeout(`${origin}${query}`, CONTEXT_FETCH_TIMEOUT_MS)
        if (!response.ok) continue
        const payload = (await response.json()) as IdentityBudgetResponse
        const keys = payload.monthlyBudgetsByKey ?? payload.data?.monthlyBudgetsByKey ?? []
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

async function fetchTeamCatalogFromTeamService(request: Request, candidateUserIds: string[]): Promise<TeamCatalog> {
  const uniqueUserIds = Array.from(
    new Set(candidateUserIds.map((value) => value.trim()).filter((value) => value.length > 0)),
  )
  if (uniqueUserIds.length === 0) {
    return { teams: [], keys: [] }
  }

  for (const userId of uniqueUserIds) {
    for (const origin of teamServiceOriginCandidates()) {
      try {
        const teamListResponse = await fetchWithTimeout(
          `${origin}/internal/teams/users/${encodeURIComponent(userId)}/billing-summaries`,
          CONTEXT_FETCH_TIMEOUT_MS,
        )
        if (!teamListResponse.ok) continue

        const teamListPayload = (await teamListResponse.json()) as TeamApiResponse<TeamBillingSummary[]>
        const summaries = teamListPayload.data ?? []
        if (summaries.length === 0) {
          continue
        }
        const teams: Array<{ teamId: number; teamName?: string | null }> = []
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

function buildBudgetStats(monthlyBudgetUsd: number, currentSpendUsd: number): BudgetStats {
  const normalizedBudget = monthlyBudgetUsd > 0 ? monthlyBudgetUsd : 0
  const normalizedSpend = currentSpendUsd > 0 ? currentSpendUsd : 0
  const remainingBudgetUsd = Math.max(normalizedBudget - normalizedSpend, 0)
  const budgetUsagePercent = normalizedBudget > 0 ? (normalizedSpend / normalizedBudget) * 100 : 0
  return {
    currentSpendUsd: normalizedSpend,
    remainingBudgetUsd,
    budgetUsagePercent: Math.max(budgetUsagePercent, 0),
    isBudgetExceeded: normalizedBudget > 0 && normalizedSpend >= normalizedBudget,
  }
}

export async function GET(request: Request) {
  try {
    const sessionEmail = await resolveSessionEmail(request)
    const derivedHeaderEmail = emailFromHeaders(request)
    const resolvedEmailHeader = sessionEmail ?? derivedHeaderEmail
    const backendOrigin = await resolveBackendOrigin()
    const forwardedUserId = userIdFromHeaders(request)
    const forwardedHeaders: HeadersInit = {}
    if (forwardedUserId.trim().length > 0) {
      forwardedHeaders["x-user-id"] = forwardedUserId
    }
    if (resolvedEmailHeader && resolvedEmailHeader.trim().length > 0) {
      forwardedHeaders["x-user-email"] = resolvedEmailHeader.trim()
    }
    const [keys, billingSignals, usagePredictionSignals, dailyCumulativeTokens, snapshotTeamApiKeys] = backendOrigin
      ? await Promise.all([
          fetchWithTimeout(`${backendOrigin}/api/v1/agents/identity-api-keys`, CONTEXT_FETCH_TIMEOUT_MS, {
            method: "GET",
            headers: forwardedHeaders,
          }).then(async (response) => (response.ok ? (((await response.json()) as IdentitySnapshot[]) ?? []) : [])),
          fetchWithTimeout(`${backendOrigin}/api/v1/agents/billing-signals`, CONTEXT_FETCH_TIMEOUT_MS, {
            method: "GET",
            headers: forwardedHeaders,
          }).then(async (response) => (response.ok ? (((await response.json()) as BillingSignal[]) ?? []) : [])),
          fetchWithTimeout(`${backendOrigin}/api/v1/agents/usage-prediction-signals`, CONTEXT_FETCH_TIMEOUT_MS, {
            method: "GET",
            headers: forwardedHeaders,
          }).then(async (response) => (response.ok ? (((await response.json()) as UsagePredictionSignal[]) ?? []) : [])),
          fetchWithTimeout(`${backendOrigin}/api/v1/agents/daily-cumulative-tokens`, CONTEXT_FETCH_TIMEOUT_MS, {
            method: "GET",
            headers: forwardedHeaders,
          }).then(async (response) => (response.ok ? (((await response.json()) as DailyCumulativeTokenSignal[]) ?? []) : [])),
          fetchWithTimeout(`${backendOrigin}/api/v1/agents/team-api-keys`, CONTEXT_FETCH_TIMEOUT_MS, {
            method: "GET",
            headers: forwardedHeaders,
          }).then(async (response) => (response.ok ? (((await response.json()) as TeamApiKeySnapshot[]) ?? []) : [])),
        ])
      : [[], [], [], [], []]
    const headerIdentifier = userIdentifierFromHeaders(request)
    const resolvedIdentifier = {
      userId: headerIdentifier.userId,
      email: resolvedEmailHeader ?? headerIdentifier.email,
    }
    const currentUserId = resolveCurrentUserId(request, keys)
    const fallbackUserId = (process.env.AI_AGENT_FALLBACK_USER_ID ?? "").trim()
    const teamCatalogOverrideUserId = (process.env.AI_AGENT_TEAM_CATALOG_USER_ID ?? "").trim()
    const teamCatalogUserIds = [
      teamCatalogOverrideUserId,
      currentUserId != null ? String(currentUserId) : "",
      userIdFromHeaders(request),
      resolvedIdentifier.email ?? "",
      fallbackUserId,
    ]
    const teamCatalog = await fetchTeamCatalogFromTeamService(request, teamCatalogUserIds)
    const keysForCurrentUser = currentUserId == null ? [] : keys.filter((key) => key.userId === currentUserId)

    const teamApiKeyByCompositeKey = new Map<string, TeamApiKeySnapshot>()
    for (const item of snapshotTeamApiKeys) {
      teamApiKeyByCompositeKey.set(`${item.teamId}:${item.teamApiKeyId}`, item)
    }
    for (const item of teamCatalog.keys) {
      teamApiKeyByCompositeKey.set(`${item.teamId}:${item.teamApiKeyId}`, item)
    }
    const teamApiKeys = Array.from(teamApiKeyByCompositeKey.values())
    const fallbackEmails = Array.from(
      new Set(
        teamApiKeys
          .map((item) => (item.ownerUserId ?? "").trim())
          .filter((value) => value.includes("@")),
      ),
    )
    const billingByKeyId = new Map<string, BillingSignal>(billingSignals.map((item) => [item.apiKeyId, item]))
    const usageSignalByTeamAndUser = new Map<string, UsagePredictionSignal>()
    const usageSignalByTeam = new Map<string, UsagePredictionSignal>()
    for (const signal of usagePredictionSignals) {
      const userId = (signal.userId ?? "").trim()
      if (!userId) continue
      const teamId = (signal.teamId ?? "").trim()
      usageSignalByTeamAndUser.set(`${teamId}|${userId}`, signal)
      if (teamId.length > 0 && !usageSignalByTeam.has(teamId)) {
        usageSignalByTeam.set(teamId, signal)
      }
    }

    const dailyTokensByApiKey = new Map<string, DailyCumulativeTokenSignal>()
    const dailyTokensByTeamAndUser = new Map<string, DailyCumulativeTokenSignal>()
    const dailyTokensByTeam = new Map<string, DailyCumulativeTokenSignal>()
    const occurredAtEpoch = (value: string | null | undefined): number => {
      if (!value) return 0
      const parsed = Date.parse(value)
      return Number.isFinite(parsed) ? parsed : 0
    }
    const pickLatest = (
      map: Map<string, DailyCumulativeTokenSignal>,
      key: string,
      signal: DailyCumulativeTokenSignal,
    ) => {
      const previous = map.get(key)
      if (!previous || occurredAtEpoch(signal.occurredAt) >= occurredAtEpoch(previous.occurredAt)) {
        map.set(key, signal)
      }
    }
    for (const signal of dailyCumulativeTokens) {
      const userId = (signal.userId ?? "").trim()
      if (!userId) continue
      const teamId = (signal.teamId ?? "").trim()
      const apiKeyId = (signal.apiKeyId ?? "").trim()
      if (apiKeyId.length > 0) {
        pickLatest(dailyTokensByApiKey, apiKeyId, signal)
      }
      pickLatest(dailyTokensByTeamAndUser, `${teamId}|${userId}`, signal)
      if (teamId.length > 0) {
        pickLatest(dailyTokensByTeam, teamId, signal)
      }
    }

    const resolveDailyTokens = (apiKeyId: number, teamId?: number | null): number => {
      const keySignal = dailyTokensByApiKey.get(String(apiKeyId))
      const keyTokens = toNumber(keySignal?.dailyTotalTokens, 0)
      if (keyTokens > 0) return keyTokens
      if (currentUserId == null) return 0
      const resolvedTeamId = teamId == null ? "" : String(teamId)
      const scopedSignal =
        dailyTokensByTeamAndUser.get(`${resolvedTeamId}|${String(currentUserId)}`) ??
        (resolvedTeamId.length > 0 ? dailyTokensByTeam.get(resolvedTeamId) : null)
      return toNumber(scopedSignal?.dailyTotalTokens, 0)
    }
    const personalUsageSignal =
      currentUserId == null ? null : usageSignalByTeamAndUser.get(`|${String(currentUserId)}`) ?? null

    const personalKeysFromSnapshot = keysForCurrentUser.map((key) => {
      const signal = billingByKeyId.get(String(key.keyId))
      const currentSpendUsd = toNumber(signal?.latestEstimatedCostUsd, 0)
      const monthlyBudgetUsd = toNumber(key.monthlyBudgetUsd, 0)
      const budgetStats = buildBudgetStats(monthlyBudgetUsd, currentSpendUsd)
      const averageDailySpendUsd = toNumber(personalUsageSignal?.averageDailySpendUsd7d, 0)
      const averageDailyTokenUsage = Math.max(
        toNumber(personalUsageSignal?.averageDailyTokenUsage7d, 0),
        resolveDailyTokens(key.keyId, null),
      )
      const recentDailySpendUsd = (personalUsageSignal?.recentDailySpendUsd ?? [])
        .map((value) => toNumber(value, 0))
        .filter((value) => value >= 0)

      return {
        keyId: key.keyId,
        alias: key.alias,
        provider: key.provider,
        monthlyBudgetUsd,
        status: key.status,
        budgetStats,
        providerStats: {
          currentSpendUsd: budgetStats.currentSpendUsd,
          averageDailySpendUsd,
          averageDailyTokenUsage,
          recentDailySpendUsd,
        },
      }
    })
    const identityBudgetKeys =
      personalKeysFromSnapshot.length > 0
        ? []
        : await fetchIdentityBudgetKeys(currentUserId, resolvedIdentifier.email, fallbackEmails)
    const personalKeysFromIdentity = identityBudgetKeys
      .map((key) => {
        const keyId = toNumber(key.externalApiKeyId ?? key.apiKeyId, 0)
        if (keyId <= 0) {
          return null
        }
        const signal = billingByKeyId.get(String(keyId))
        const currentSpendUsd = toNumber(signal?.latestEstimatedCostUsd, 0)
        const monthlyBudgetUsd = toNumber(key.monthlyBudgetUsd, 0)
        const budgetStats = buildBudgetStats(monthlyBudgetUsd, currentSpendUsd)
        const averageDailySpendUsd = toNumber(personalUsageSignal?.averageDailySpendUsd7d, 0)
        const averageDailyTokenUsage = Math.max(
          toNumber(personalUsageSignal?.averageDailyTokenUsage7d, 0),
          resolveDailyTokens(keyId, null),
        )
        const recentDailySpendUsd = (personalUsageSignal?.recentDailySpendUsd ?? [])
          .map((value) => toNumber(value, 0))
          .filter((value) => value >= 0)
        return {
          keyId,
          alias: (key.alias ?? "").trim() || `key-${keyId}`,
          provider: (key.provider ?? "").trim() || "UNKNOWN",
          monthlyBudgetUsd,
          status: "ACTIVE",
          budgetStats,
          providerStats: {
            currentSpendUsd: budgetStats.currentSpendUsd,
            averageDailySpendUsd,
            averageDailyTokenUsage,
            recentDailySpendUsd,
          },
        }
      })
      .filter((item): item is NonNullable<typeof item> => item != null)
    const personalKeys = personalKeysFromIdentity.length > 0 ? personalKeysFromIdentity : personalKeysFromSnapshot

    const teamBoard = teamApiKeys.map((item) => {
      const signal = billingByKeyId.get(String(item.teamApiKeyId))
      const currentSpendUsd = toNumber(signal?.latestEstimatedCostUsd, 0)
      const monthlyBudgetUsd = toNumber(item.monthlyBudgetUsd, 0)
      const budgetStats = buildBudgetStats(monthlyBudgetUsd, currentSpendUsd)
      const usageSignal =
        currentUserId == null
          ? usageSignalByTeam.get(String(item.teamId)) ?? null
          : usageSignalByTeamAndUser.get(`${String(item.teamId)}|${String(currentUserId)}`) ??
            usageSignalByTeam.get(String(item.teamId)) ??
            null
      const averageDailySpendUsd = toNumber(usageSignal?.averageDailySpendUsd7d, 0)
      const averageDailyTokenUsage = Math.max(
        toNumber(usageSignal?.averageDailyTokenUsage7d, 0),
        resolveDailyTokens(item.teamApiKeyId, item.teamId),
      )
      const recentDailySpendUsd = (usageSignal?.recentDailySpendUsd ?? [])
        .map((value) => toNumber(value, 0))
        .filter((value) => value >= 0)
      return {
        teamId: item.teamId,
        teamName: normalizeTeamName(item.teamName, item.teamId),
        teamApiKeyId: item.teamApiKeyId,
        ownerUserId: item.ownerUserId,
        visibility: item.visibility ?? "TEAM",
        alias: item.alias,
        provider: item.provider,
        status: item.status,
        monthlyBudgetUsd,
        budgetStats,
        providerStats: {
          currentSpendUsd: budgetStats.currentSpendUsd,
          averageDailySpendUsd,
          averageDailyTokenUsage,
          recentDailySpendUsd,
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
      currentUserId,
      userContext: null,
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
