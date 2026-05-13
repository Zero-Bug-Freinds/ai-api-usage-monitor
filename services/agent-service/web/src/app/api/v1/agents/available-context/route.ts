import { NextResponse } from "next/server"

type IdentitySnapshot = {
  keyId: number
  userId: number | string
  alias: string
  provider: string
  visibility?: string
  status: string
  monthlyBudgetUsd?: number | null
  /** identity 저장소의 SHA-256 해시; 동일 시크릿 재등록·별칭 변경 시 병합용 */
  keyHash?: string | null
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
  keyHash?: string | null
}

type BillingSignal = {
  /** Backend uses string id; normalize with String() when indexing maps. */
  apiKeyId: string | number
  latestEstimatedCostUsd?: number | null
  /** Sum of usage.cost.finalized event costs; retained after key delete. */
  accumulatedCostUsd?: number | null
  provider?: string | null
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
  /** 서울 달력 당월 1일~오늘 과금 요약(월 예산·진행률·잔여 계산용). */
  currentSpendUsd: number
  /** `daily_expenditure_agg` 전 기간 합(표시용 누적 지출). */
  lifetimeSpendUsd: number
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

type ExpenditureSummaryResponse = {
  totalCostUsd?: number | string | null
  /** Billing expenditure API: same source as billing 웹 키별 요약 */
  monthlyBudgetUsd?: number | string | null
}

type BillingSummaryRow = {
  totalCostUsd: number
  monthlyBudgetUsd: number | null
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

function billingSignalMapKey(raw: string | number | null | undefined): string {
  if (raw === undefined || raw === null) return ""
  return String(raw).trim()
}

function lifetimeSpendForKey(
  keyId: number,
  lifetimeSpendByKey: Map<number, number>,
  signal?: BillingSignal,
): number {
  const fromBillingRange = toNumber(lifetimeSpendByKey.get(keyId), 0)
  const fromEventSum = toNumber(signal?.accumulatedCostUsd, 0)
  const fromLastEvent = toNumber(signal?.latestEstimatedCostUsd, 0)
  return Math.max(fromBillingRange, fromEventSum, fromLastEvent)
}

/** Identity/팀 스냅샷 + billing 신호에 등장하는 모든 키로 과금 요약을 조회한다(삭제된 키·해시 병합 시 누락 방지). */
function collectAllKnownKeysForBillingFetch(
  keys: IdentitySnapshot[],
  teamApiKeys: TeamApiKeySnapshot[],
  billingSignals: BillingSignal[],
): Array<{ apiKeyId: number; provider: string }> {
  const byId = new Map<number, string>()
  for (const item of keys) {
    const id = toNumber(item.keyId, 0)
    if (id <= 0) continue
    const p = (item.provider ?? "").trim() || "UNKNOWN"
    byId.set(id, p)
  }
  for (const item of teamApiKeys) {
    const id = toNumber(item.teamApiKeyId, 0)
    if (id <= 0) continue
    if (byId.has(id)) continue
    const p = (item.provider ?? "").trim() || "UNKNOWN"
    byId.set(id, p)
  }
  for (const s of billingSignals) {
    const id = toNumber(s.apiKeyId, 0)
    if (id <= 0) continue
    if (byId.has(id)) continue
    const p = (s.provider ?? "").trim() || "UNKNOWN"
    byId.set(id, p)
  }
  return Array.from(byId.entries()).map(([apiKeyId, provider]) => ({ apiKeyId, provider }))
}

const ORIGIN_PROBE_TIMEOUT_MS = 3000
const CONTEXT_FETCH_TIMEOUT_MS = 10000

function hasUsableKeyAlias(alias: string | null | undefined): boolean {
  return (alias ?? "").trim().length > 0
}

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

function billingServiceOriginCandidates(): string[] {
  const configured = (process.env.BILLING_SERVICE_INTERNAL_ORIGIN ?? "").trim().replace(/\/$/, "")
  const defaults = ["http://localhost:8095", "http://host.docker.internal:8095", "http://billing-service:8095"]
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

function buildForwardHeaders(request: Request, email: string | null, fallbackUserId?: string): HeadersInit {
  const headers: HeadersInit = {}
  const authorization = request.headers.get("authorization")?.trim() ?? ""
  const cookie = request.headers.get("cookie")?.trim() ?? ""
  const userIdFromRequest = userIdFromHeaders(request).trim()
  const userId = userIdFromRequest.length > 0 ? userIdFromRequest : (fallbackUserId ?? "").trim()

  if (authorization.length > 0) {
    headers.Authorization = authorization
  }
  if (cookie.length > 0) {
    headers.Cookie = cookie
  }
  if (userId.length > 0) {
    headers["x-user-id"] = userId
  }
  if (email && email.trim().length > 0) {
    headers["x-user-email"] = email.trim()
  }
  return headers
}

/**
 * Billing 내부 호출은 사용자 식별 헤더만 전달한다.
 * 브라우저 cookie/authorization 전달로 사용자 컨텍스트가 뒤집히는 문제를 방지한다.
 */
function buildBillingForwardHeaders(request: Request, email: string | null, fallbackUserId?: string): Record<string, string> {
  const headers: Record<string, string> = {}
  const userIdFromRequest = userIdFromHeaders(request).trim()
  const userId = resolveBillingUserId(userIdFromRequest, fallbackUserId)
  if (userId.length > 0) {
    headers["x-user-id"] = userId
  }
  if (email && email.trim().length > 0) {
    headers["x-user-email"] = email.trim()
  }
  return headers
}

function resolveBillingUserId(primaryUserId?: string, fallbackUserId?: string): string {
  const primary = (primaryUserId ?? "").trim()
  const fallback = (fallbackUserId ?? "").trim()
  // billing 집계는 이메일 userId 기준으로 저장된 레코드가 있어, 숫자형 userId보다 이메일을 우선한다.
  if (primary.includes("@")) return primary
  if (fallback.includes("@")) return fallback
  if (primary.length > 0) return primary
  return fallback
}

function billingUserIdCandidates(
  request: Request,
  email: string | null,
  fallbackUserId?: string,
  extraCandidates: string[] = [],
): string[] {
  const primary = userIdFromHeaders(request).trim()
  const fallback = (fallbackUserId ?? "").trim()
  const mail = (email ?? "").trim()
  const ordered = [resolveBillingUserId(primary, fallback), mail, fallback, primary, ...extraCandidates.map((v) => v.trim())]
  return Array.from(new Set(ordered.filter((value) => value.length > 0)))
}

function billingProviderCandidates(rawProvider: string): string[] {
  const normalized = (rawProvider ?? "").trim().toUpperCase()
  const supported = ["OPENAI", "ANTHROPIC", "GOOGLE"]
  /** Billing aggregates Gemini usage under {@link AiProvider#GOOGLE}; Identity may still store GEMINI. */
  if (normalized === "GEMINI") {
    return ["GOOGLE"]
  }
  if (supported.includes(normalized)) {
    return [normalized]
  }
  return supported
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

function userIdFromHeadersStrict(request: Request): string | null {
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
  return null
}

function userIdAsNumber(request: Request): number | null {
  const raw = userIdFromHeadersStrict(request)
  if (!raw) {
    return null
  }
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

  const fromKeysRaw = keys
    .map((item) => Number(String(item.userId).trim()))
    .find((value) => Number.isFinite(value) && value > 0)
  if (fromKeysRaw != null) {
    return fromKeysRaw
  }
  const fallbackUserId = (process.env.AI_AGENT_FALLBACK_USER_ID ?? "1").trim()
  const parsedFallback = Number(fallbackUserId)
  if (Number.isFinite(parsedFallback) && parsedFallback > 0) {
    return parsedFallback
  }
  return 1
}

function identityUserIdCandidates(
  request: Request,
  currentUserId: number | null,
  resolvedEmail: string | null,
): string[] {
  const rawHeaderUserId = userIdFromHeadersStrict(request)?.trim() ?? ""
  const normalizedHeaderUserId =
    rawHeaderUserId.length > 0
      ? Number.isFinite(Number(rawHeaderUserId))
        ? String(Number(rawHeaderUserId))
        : rawHeaderUserId
      : ""
  const numericCurrent = currentUserId != null ? String(currentUserId) : ""
  const email = (resolvedEmail ?? "").trim()
  return Array.from(new Set([numericCurrent, normalizedHeaderUserId, rawHeaderUserId, email].filter((v) => v.length > 0)))
}

function matchesIdentityUserId(snapshotUserId: number | string, candidates: string[]): boolean {
  const raw = String(snapshotUserId ?? "").trim()
  if (!raw) return false
  if (candidates.includes(raw)) return true
  const numeric = Number(raw)
  if (Number.isFinite(numeric)) {
    return candidates.includes(String(numeric))
  }
  return false
}

async function fetchIdentityBudgetKeys(
  request: Request,
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
        const response = await fetchWithTimeout(`${origin}${query}`, CONTEXT_FETCH_TIMEOUT_MS, {
          headers: buildForwardHeaders(request, email),
        })
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

async function fetchIdentityKeyHashesForUser(
  request: Request,
  userId: number,
  email: string | null,
): Promise<Map<number, string>> {
  const map = new Map<number, string>()
  const headers = buildForwardHeaders(request, email)
  for (const origin of identityServiceOriginCandidates()) {
    try {
      const response = await fetchWithTimeout(
        `${origin}/internal/v1/api-keys/users/${encodeURIComponent(String(userId))}/key-hashes`,
        CONTEXT_FETCH_TIMEOUT_MS,
        { method: "GET", headers },
      )
      if (!response.ok) continue
      const rows = (await response.json()) as Array<{ keyId?: number; keyHash?: string | null }>
      for (const row of rows ?? []) {
        const id = toNumber(row.keyId, 0)
        const h = (row.keyHash ?? "").trim()
        if (id > 0 && h.length > 0) {
          map.set(id, h)
        }
      }
      return map
    } catch {
      // try next origin
    }
  }
  return map
}

async function enrichIdentitySnapshotsWithKeyHashes(
  request: Request,
  keys: IdentitySnapshot[],
  userId: number,
  email: string | null,
): Promise<IdentitySnapshot[]> {
  const hashByKeyId = await fetchIdentityKeyHashesForUser(request, userId, email)
  if (hashByKeyId.size === 0) {
    return keys
  }
  return keys.map((k) => {
    const existing = (k.keyHash ?? "").trim()
    if (existing.length > 0) {
      return k
    }
    const h = hashByKeyId.get(k.keyId)
    if (h != null && h.length > 0) {
      return { ...k, keyHash: h }
    }
    return k
  })
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
        const forwardHeaders = buildForwardHeaders(request, emailFromHeaders(request))
        const teamListResponse = await fetchWithTimeout(
          `${origin}/internal/teams/users/${encodeURIComponent(userId)}/billing-summaries`,
          CONTEXT_FETCH_TIMEOUT_MS,
          {
            headers: forwardHeaders,
          },
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
            const aliasTrimmed = (key.alias ?? "").trim()
            if (!hasUsableKeyAlias(aliasTrimmed)) continue
            keys.push({
              teamId,
              teamName,
              teamApiKeyId,
              ownerUserId: userId,
              visibility: "TEAM",
              alias: aliasTrimmed,
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
  if (typeof value === "bigint") {
    const parsed = Number(value)
    if (Number.isFinite(parsed)) return parsed
  }
  return fallback
}

/** Agent `BillingKeySignal` JSON: camelCase 기본 + snake_case·프록시 변형 대비. */
function firstDefined<T>(...values: (T | null | undefined)[]): T | undefined {
  for (const v of values) {
    if (v !== undefined && v !== null) return v
  }
  return undefined
}

function parseBillingSignalRow(raw: unknown): BillingSignal | null {
  if (!raw || typeof raw !== "object") return null
  const o = raw as Record<string, unknown>
  const idRaw = firstDefined(o.apiKeyId, o.api_key_id)
  if (idRaw === undefined || idRaw === null || String(idRaw).trim() === "") return null
  const latestRaw = firstDefined(o.latestEstimatedCostUsd, o.latest_estimated_cost_usd)
  const accumRaw = firstDefined(o.accumulatedCostUsd, o.accumulated_cost_usd)
  const providerRaw = firstDefined(o.provider, o.Provider)
  return {
    apiKeyId: idRaw as string | number,
    latestEstimatedCostUsd:
      latestRaw === undefined || latestRaw === null || String(latestRaw).trim() === ""
        ? undefined
        : toNumber(latestRaw, 0),
    accumulatedCostUsd:
      accumRaw === undefined || accumRaw === null || String(accumRaw).trim() === ""
        ? undefined
        : toNumber(accumRaw, 0),
    provider: providerRaw === undefined || providerRaw === null ? undefined : String(providerRaw),
  }
}

function normalizeBillingSignalsFromApi(raw: unknown): BillingSignal[] {
  if (!Array.isArray(raw)) return []
  return raw.map(parseBillingSignalRow).filter((item): item is BillingSignal => item != null)
}

function normalizeTeamName(value: string | null | undefined, teamId: number): string {
  const trimmed = (value ?? "").trim()
  const withoutParentheses = trimmed.replace(/\s*\([^)]*\)\s*/g, " ").replace(/\s+/g, " ").trim()
  if (withoutParentheses.length > 0) return withoutParentheses
  return `Team ${teamId}`
}

/**
 * Aligns with billing expenditure UI monthly rollups (Asia/Seoul calendar month start → today in Seoul).
 * See services/billing-service/web/src/lib/expenditure/dates.ts (currentMonthRangeKst).
 */
function currentMonthRangeKstMonthToDate(): { from: string; to: string } {
  const formatter = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  })
  const parts = formatter.formatToParts(new Date())
  const y = parts.find((p) => p.type === "year")?.value ?? "1970"
  const m = parts.find((p) => p.type === "month")?.value ?? "01"
  const d = parts.find((p) => p.type === "day")?.value ?? "01"
  const today = `${y}-${m}-${d}`
  const from = `${today.slice(0, 7)}-01`
  return { from, to: today }
}

/**
 * Billing summary API currently requires from/to and enforces max range days.
 * We align with current billing maxRangeDays default(400) to compute practical cumulative spend.
 */
function rollingRangeKst(days: number): { from: string; to: string } {
  const formatter = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  })
  const parts = formatter.formatToParts(new Date())
  const y = Number(parts.find((p) => p.type === "year")?.value ?? "1970")
  const m = Number(parts.find((p) => p.type === "month")?.value ?? "01")
  const d = Number(parts.find((p) => p.type === "day")?.value ?? "01")
  const end = new Date(Date.UTC(y, Math.max(0, m - 1), d))
  const safeDays = Math.max(1, Math.min(days, 400))
  const start = new Date(end)
  start.setUTCDate(start.getUTCDate() - (safeDays - 1))
  const toIso = (value: Date): string => {
    const yy = value.getUTCFullYear()
    const mm = String(value.getUTCMonth() + 1).padStart(2, "0")
    const dd = String(value.getUTCDate()).padStart(2, "0")
    return `${yy}-${mm}-${dd}`
  }
  return { from: toIso(start), to: toIso(end) }
}

async function fetchBillingSummaryByKey(
  request: Request,
  keys: Array<{ apiKeyId: number; provider: string }>,
  email: string | null,
  fallbackUserId?: string,
  extraBillingUserIds: string[] = [],
): Promise<Map<number, BillingSummaryRow>> {
  const byKeyId = new Map<number, BillingSummaryRow>()
  const uniqueKeys = Array.from(
    new Map(
      keys
        .map((item) => ({
          apiKeyId: toNumber(item.apiKeyId, 0),
          provider: (item.provider ?? "").trim().toUpperCase(),
        }))
        .filter((item) => item.apiKeyId > 0)
        .map((item) => [item.apiKeyId, item]),
    ).values(),
  )
  if (uniqueKeys.length === 0) return byKeyId

  const { from, to } = currentMonthRangeKstMonthToDate()
  const userIdCandidates = billingUserIdCandidates(request, email, fallbackUserId, extraBillingUserIds)
  const baseHeaders = buildBillingForwardHeaders(request, email, userIdCandidates[0] ?? fallbackUserId)
  const gatewaySharedSecret = (
    process.env.GATEWAY_SHARED_SECRET ?? "local-dev-gateway-shared-secret-do-not-use-in-prod"
  ).trim()
  if (gatewaySharedSecret.length > 0) {
    baseHeaders["x-gateway-auth"] = gatewaySharedSecret
  }

  await Promise.all(
    uniqueKeys.map(async (key) => {
      const providerCandidates = billingProviderCandidates(key.provider)
      const queries = providerCandidates.map(
        (provider) =>
          `/api/v1/expenditure/summary?apiKeyId=${encodeURIComponent(String(key.apiKeyId))}&provider=${encodeURIComponent(provider)}&from=${from}&to=${to}`,
      )
      let bestTotalCostUsd = -1
      let bestMonthlyBudgetUsd: number | null = null
      for (const origin of billingServiceOriginCandidates()) {
        for (const userId of userIdCandidates) {
          for (const query of queries) {
            try {
              const headers = { ...baseHeaders, "x-user-id": userId }
              const response = await fetchWithTimeout(`${origin}${query}`, CONTEXT_FETCH_TIMEOUT_MS, {
                method: "GET",
                headers,
              })
              if (!response.ok) continue
              const payload = (await response.json()) as ExpenditureSummaryResponse
              const totalCostUsd = toNumber(payload.totalCostUsd, 0)
              const rawBudget = payload.monthlyBudgetUsd
              const monthlyBudgetUsd =
                rawBudget === null || rawBudget === undefined || String(rawBudget).trim() === ""
                  ? null
                  : toNumber(rawBudget, 0)
              if (totalCostUsd > bestTotalCostUsd) {
                bestTotalCostUsd = totalCostUsd
                bestMonthlyBudgetUsd = monthlyBudgetUsd != null && monthlyBudgetUsd > 0 ? monthlyBudgetUsd : null
              }
            } catch {
              // try next query/origin/user candidate
            }
          }
        }
      }
      if (bestTotalCostUsd >= 0) {
        byKeyId.set(key.apiKeyId, {
          totalCostUsd: bestTotalCostUsd,
          monthlyBudgetUsd: bestMonthlyBudgetUsd,
        })
      }
    }),
  )
  return byKeyId
}

async function fetchBillingLifetimeSpendByKey(
  request: Request,
  keys: Array<{ apiKeyId: number; provider: string }>,
  email: string | null,
  fallbackUserId?: string,
  extraBillingUserIds: string[] = [],
): Promise<Map<number, number>> {
  const byKeyId = new Map<number, number>()
  const uniqueKeys = Array.from(
    new Map(
      keys
        .map((item) => ({
          apiKeyId: toNumber(item.apiKeyId, 0),
          provider: (item.provider ?? "").trim().toUpperCase(),
        }))
        .filter((item) => item.apiKeyId > 0)
        .map((item) => [item.apiKeyId, item]),
    ).values(),
  )
  if (uniqueKeys.length === 0) return byKeyId

  const userIdCandidates = billingUserIdCandidates(request, email, fallbackUserId, extraBillingUserIds)
  const baseHeaders = buildBillingForwardHeaders(request, email, userIdCandidates[0] ?? fallbackUserId)
  const gatewaySharedSecret = (
    process.env.GATEWAY_SHARED_SECRET ?? "local-dev-gateway-shared-secret-do-not-use-in-prod"
  ).trim()
  if (gatewaySharedSecret.length > 0) {
    baseHeaders["x-gateway-auth"] = gatewaySharedSecret
  }
  const { from, to } = rollingRangeKst(400)

  await Promise.all(
    uniqueKeys.map(async (key) => {
      const providerCandidates = billingProviderCandidates(key.provider)
      const queries = providerCandidates.map(
        (provider) =>
          `/api/v1/expenditure/summary?apiKeyId=${encodeURIComponent(String(key.apiKeyId))}&provider=${encodeURIComponent(provider)}&from=${from}&to=${to}`,
      )
      let bestTotalCostUsd = -1
      for (const origin of billingServiceOriginCandidates()) {
        for (const userId of userIdCandidates) {
          for (const query of queries) {
            try {
              const headers = { ...baseHeaders, "x-user-id": userId }
              const response = await fetchWithTimeout(`${origin}${query}`, CONTEXT_FETCH_TIMEOUT_MS, {
                method: "GET",
                headers,
              })
              if (!response.ok) continue
              const payload = (await response.json()) as ExpenditureSummaryResponse
              bestTotalCostUsd = Math.max(bestTotalCostUsd, toNumber(payload.totalCostUsd, 0))
            } catch {
              // try next query/origin/user candidate
            }
          }
        }
      }
      if (bestTotalCostUsd >= 0) {
        byKeyId.set(key.apiKeyId, bestTotalCostUsd)
      }
    }),
  )
  return byKeyId
}

function spendAndBudgetForKey(
  signal: BillingSignal | undefined,
  snapshotMonthlyBudgetUsd: number,
  billingRow: BillingSummaryRow | undefined,
): { currentSpendUsd: number; monthlyBudgetUsd: number } {
  const fromSignal = toNumber(signal?.latestEstimatedCostUsd, 0)
  const fromSummary = toNumber(billingRow?.totalCostUsd, 0)
  const currentSpendUsd = Math.max(fromSignal, fromSummary)
  const billingBudget = billingRow?.monthlyBudgetUsd != null ? toNumber(billingRow.monthlyBudgetUsd, 0) : 0
  const snapshotBudget = toNumber(snapshotMonthlyBudgetUsd, 0)
  const monthlyBudgetUsd = snapshotBudget >= 0 ? snapshotBudget : billingBudget
  return { currentSpendUsd, monthlyBudgetUsd }
}

function buildBudgetStats(
  monthlyBudgetUsd: number,
  currentSpendUsd: number,
  lifetimeSpendUsd: number,
): BudgetStats {
  const normalizedBudget = monthlyBudgetUsd > 0 ? monthlyBudgetUsd : 0
  const normalizedSpend = currentSpendUsd > 0 ? currentSpendUsd : 0
  const normalizedLifetime = Math.max(0, Number.isFinite(lifetimeSpendUsd) ? lifetimeSpendUsd : 0)
  const remainingBudgetUsd = Math.max(normalizedBudget - normalizedSpend, 0)
  const budgetUsagePercent = normalizedBudget > 0 ? (normalizedSpend / normalizedBudget) * 100 : 0
  return {
    currentSpendUsd: normalizedSpend,
    lifetimeSpendUsd: normalizedLifetime,
    remainingBudgetUsd,
    budgetUsagePercent: Math.max(budgetUsagePercent, 0),
    isBudgetExceeded: normalizedBudget > 0 && normalizedSpend >= normalizedBudget,
  }
}

type PersonalContextRow = {
  keyId: number
  alias: string
  provider: string
  monthlyBudgetUsd: number
  status: string
  budgetStats: BudgetStats
  providerStats: {
    currentSpendUsd: number
    averageDailySpendUsd: number
    averageDailyTokenUsage: number
    recentDailySpendUsd: number[]
  }
  mergedKeyIds?: number[]
  /** agent 스냅샷 key_hash; 있으면 별칭과 무관하게 동일 키로 병합 */
  keyHash?: string | null
}

type TeamContextRow = {
  teamId: number
  teamName: string
  teamApiKeyId: number
  ownerUserId?: string | null
  visibility?: string
  alias: string
  provider: string
  status: string
  monthlyBudgetUsd: number
  budgetStats: BudgetStats
  providerStats: {
    currentSpendUsd: number
    averageDailySpendUsd: number
    averageDailyTokenUsage: number
    recentDailySpendUsd: number[]
  }
  mergedTeamApiKeyIds?: number[]
  keyHash?: string | null
}

function normalizeCredentialSegment(value: string): string {
  return value.trim().toLowerCase().replace(/\s+/g, " ")
}

/** 재등록 등으로 keyId만 다른 동일 별칭·제공자 조합을 묶을 때(구버전 스냅샷) fallback 지문. */
function personalCredentialFingerprint(provider: string, alias: string): string {
  const p = normalizeCredentialSegment(provider || "UNKNOWN")
  const a = normalizeCredentialSegment(alias || "")
  return `${p}::${a}`
}

/** 동일 외부 시크릿(키 해시) 우선; 해시 없으면 별칭·제공자 fallback. */
function personalKeyMergeFingerprint(provider: string, alias: string, keyHash?: string | null): string {
  const h = (keyHash ?? "").trim()
  if (h.length > 0) {
    return `hash:${h}`
  }
  return `legacy:${personalCredentialFingerprint(provider, alias)}`
}

function teamKeyMergeFingerprint(
  teamId: number,
  provider: string,
  alias: string,
  keyHash?: string | null,
): string {
  const h = (keyHash ?? "").trim()
  if (h.length > 0) {
    return `${teamId}::hash:${h}`
  }
  return `${teamId}::legacy:${personalCredentialFingerprint(provider, alias)}`
}

function credentialStatusRank(status: string): number {
  const u = (status ?? "").toUpperCase()
  if (u === "ACTIVE") return 0
  if (u === "DELETION_REQUESTED") return 1
  if (u === "DELETED") return 2
  return 3
}

/**
 * 동일 자격증명 병합 시 최신 keyId를 대표 상태로 본다.
 * 재등록/재삭제 반복에서 ACTIVE가 과거 이력인데도 대표로 남는 현상을 방지한다.
 */
function pickLatestPersonalRow(group: PersonalContextRow[]): PersonalContextRow {
  return [...group].sort((a, b) => {
    const byKeyId = b.keyId - a.keyId
    if (byKeyId !== 0) return byKeyId
    return credentialStatusRank(b.status) - credentialStatusRank(a.status)
  })[0]
}

function pickLatestTeamRow(group: TeamContextRow[]): TeamContextRow {
  return [...group].sort((a, b) => {
    const byKeyId = b.teamApiKeyId - a.teamApiKeyId
    if (byKeyId !== 0) return byKeyId
    return credentialStatusRank(b.status) - credentialStatusRank(a.status)
  })[0]
}

function mergeProviderStatsBundle(
  stats: PersonalContextRow["providerStats"][],
): PersonalContextRow["providerStats"] {
  let currentSpendUsd = 0
  let averageDailySpendUsd = 0
  let averageDailyTokenUsage = 0
  const recentDailySpendUsd: number[] = []
  for (const p of stats) {
    currentSpendUsd += p.currentSpendUsd
    averageDailySpendUsd += p.averageDailySpendUsd
    averageDailyTokenUsage += p.averageDailyTokenUsage
    const arr = p.recentDailySpendUsd ?? []
    for (let i = 0; i < arr.length; i++) {
      recentDailySpendUsd[i] = (recentDailySpendUsd[i] ?? 0) + (arr[i] ?? 0)
    }
  }
  return { currentSpendUsd, averageDailySpendUsd, averageDailyTokenUsage, recentDailySpendUsd }
}

function dedupePersonalKeysByCredential(
  rows: PersonalContextRow[],
  lifetimeSpendByKey: Map<number, number>,
  billingByKeyId: Map<string, BillingSignal>,
): PersonalContextRow[] {
  const byFp = new Map<string, PersonalContextRow[]>()
  const hashGroupByLegacy = new Map<string, string[]>()
  for (const row of rows) {
    const legacyFp = `legacy:${personalCredentialFingerprint(row.provider, row.alias)}`
    const fp = personalKeyMergeFingerprint(row.provider, row.alias, row.keyHash)
    const list = byFp.get(fp) ?? []
    list.push(row)
    byFp.set(fp, list)
    if (fp.startsWith("hash:")) {
      const hashGroups = hashGroupByLegacy.get(legacyFp) ?? []
      if (!hashGroups.includes(fp)) {
        hashGroups.push(fp)
        hashGroupByLegacy.set(legacyFp, hashGroups)
      }
    }
  }

  // keyHash가 없는 legacy 행은, 동일 alias/provider의 hash 그룹이 정확히 1개면 그쪽으로 흡수한다.
  for (const row of rows) {
    const hasHash = (row.keyHash ?? "").trim().length > 0
    if (hasHash) continue
    const legacyFp = `legacy:${personalCredentialFingerprint(row.provider, row.alias)}`
    const hashTargets = hashGroupByLegacy.get(legacyFp) ?? []
    if (hashTargets.length !== 1) continue
    const legacyGroup = byFp.get(legacyFp) ?? []
    if (!legacyGroup.some((item) => item.keyId === row.keyId)) continue
    byFp.set(
      legacyFp,
      legacyGroup.filter((item) => item.keyId !== row.keyId),
    )
    const hashGroupKey = hashTargets[0]
    const hashGroup = byFp.get(hashGroupKey) ?? []
    hashGroup.push(row)
    byFp.set(hashGroupKey, hashGroup)
  }

  const out: PersonalContextRow[] = []
  for (const [fp, group] of byFp) {
    if (group.length === 0) continue
    if (group.length === 1) {
      const single = group[0]
      out.push({ ...single, mergedKeyIds: [single.keyId] })
      continue
    }
    const primary = pickLatestPersonalRow(group)
    const mergedKeyIds = [...new Set(group.map((g) => g.keyId))].sort((a, b) => a - b)
    const monthlyBudgetUsd = toNumber(primary.monthlyBudgetUsd, 0)
    let totalCurrent = 0
    let totalLifetime = 0
    for (const r of group) {
      totalCurrent += r.budgetStats?.currentSpendUsd ?? 0
      const sig = billingByKeyId.get(billingSignalMapKey(r.keyId))
      totalLifetime += lifetimeSpendForKey(r.keyId, lifetimeSpendByKey, sig)
    }
    const mergedBudget = buildBudgetStats(monthlyBudgetUsd, totalCurrent, totalLifetime)
    const mergedProvider = mergeProviderStatsBundle(group.map((g) => g.providerStats))
    console.info("[agent available-context] merged personal keys (same keyHash or legacy provider+alias)", {
      fingerprint: fp,
      canonicalKeyId: primary.keyId,
      mergedKeyIds,
      statuses: group.map((g) => ({ keyId: g.keyId, status: g.status })),
    })
    out.push({
      ...primary,
      monthlyBudgetUsd,
      status: primary.status,
      budgetStats: mergedBudget,
      providerStats: mergedProvider,
      mergedKeyIds,
    })
  }
  return out
}

function dedupeTeamBoardByCredential(
  rows: TeamContextRow[],
  lifetimeSpendByKey: Map<number, number>,
  billingByKeyId: Map<string, BillingSignal>,
): TeamContextRow[] {
  const byFp = new Map<string, TeamContextRow[]>()
  const hashGroupByLegacy = new Map<string, string[]>()
  for (const row of rows) {
    const legacyFp = `${row.teamId}::legacy:${personalCredentialFingerprint(row.provider, row.alias)}`
    const fp = teamKeyMergeFingerprint(row.teamId, row.provider, row.alias, row.keyHash)
    const list = byFp.get(fp) ?? []
    list.push(row)
    byFp.set(fp, list)
    if (fp.includes("::hash:")) {
      const hashGroups = hashGroupByLegacy.get(legacyFp) ?? []
      if (!hashGroups.includes(fp)) {
        hashGroups.push(fp)
        hashGroupByLegacy.set(legacyFp, hashGroups)
      }
    }
  }

  // 팀 키도 개인 키와 동일: 무해시 legacy 행은 동일 팀+alias/provider의 hash 그룹이 1개면 흡수.
  for (const row of rows) {
    const hasHash = (row.keyHash ?? "").trim().length > 0
    if (hasHash) continue
    const legacyFp = `${row.teamId}::legacy:${personalCredentialFingerprint(row.provider, row.alias)}`
    const hashTargets = hashGroupByLegacy.get(legacyFp) ?? []
    if (hashTargets.length !== 1) continue
    const legacyGroup = byFp.get(legacyFp) ?? []
    if (!legacyGroup.some((item) => item.teamApiKeyId === row.teamApiKeyId)) continue
    byFp.set(
      legacyFp,
      legacyGroup.filter((item) => item.teamApiKeyId !== row.teamApiKeyId),
    )
    const hashGroupKey = hashTargets[0]
    const hashGroup = byFp.get(hashGroupKey) ?? []
    hashGroup.push(row)
    byFp.set(hashGroupKey, hashGroup)
  }

  const out: TeamContextRow[] = []
  for (const [fp, group] of byFp) {
    if (group.length === 0) continue
    if (group.length === 1) {
      const single = group[0]
      out.push({ ...single, mergedTeamApiKeyIds: [single.teamApiKeyId] })
      continue
    }
    const primary = pickLatestTeamRow(group)
    const mergedTeamApiKeyIds = [...new Set(group.map((g) => g.teamApiKeyId))].sort((a, b) => a - b)
    const monthlyBudgetUsd = toNumber(primary.monthlyBudgetUsd, 0)
    let totalCurrent = 0
    let totalLifetime = 0
    for (const r of group) {
      totalCurrent += r.budgetStats?.currentSpendUsd ?? 0
      const sig = billingByKeyId.get(billingSignalMapKey(r.teamApiKeyId))
      totalLifetime += lifetimeSpendForKey(r.teamApiKeyId, lifetimeSpendByKey, sig)
    }
    const mergedBudget = buildBudgetStats(monthlyBudgetUsd, totalCurrent, totalLifetime)
    const mergedProvider = mergeProviderStatsBundle(group.map((g) => g.providerStats))
    console.info("[agent available-context] merged team keys (same keyHash or legacy team+provider+alias)", {
      fingerprint: fp,
      canonicalTeamApiKeyId: primary.teamApiKeyId,
      mergedTeamApiKeyIds,
      teamId: primary.teamId,
      statuses: group.map((g) => ({ teamApiKeyId: g.teamApiKeyId, status: g.status })),
    })
    out.push({
      ...primary,
      monthlyBudgetUsd,
      status: primary.status,
      budgetStats: mergedBudget,
      providerStats: mergedProvider,
      mergedTeamApiKeyIds,
    })
  }
  return out
}

export async function GET(request: Request) {
  try {
    const sessionEmail = await resolveSessionEmail(request)
    const derivedHeaderEmail = emailFromHeaders(request)
    const resolvedEmailHeader = sessionEmail ?? derivedHeaderEmail
    const backendOrigin = await resolveBackendOrigin()
    const forwardedHeaders = buildForwardHeaders(request, resolvedEmailHeader)
    const strictUserId = userIdAsNumber(request)
    const identityApiKeyPath =
      strictUserId != null
        ? `/api/v1/agents/identity-api-keys/${strictUserId}`
        : "/api/v1/agents/identity-api-keys"
    const [keysInitial, billingSignals, usagePredictionSignals, dailyCumulativeTokens, snapshotTeamApiKeys] = backendOrigin
      ? await Promise.all([
          fetchWithTimeout(`${backendOrigin}${identityApiKeyPath}`, CONTEXT_FETCH_TIMEOUT_MS, {
            method: "GET",
            headers: forwardedHeaders,
          }).then(async (response) => (response.ok ? (((await response.json()) as IdentitySnapshot[]) ?? []) : [])),
          fetchWithTimeout(`${backendOrigin}/api/v1/agents/billing-signals`, CONTEXT_FETCH_TIMEOUT_MS, {
            method: "GET",
            headers: forwardedHeaders,
          }).then(async (response) =>
            response.ok ? normalizeBillingSignalsFromApi(await response.json()) : [],
          ),
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
    let keys: IdentitySnapshot[] = keysInitial
    const headerIdentifier = userIdentifierFromHeaders(request)
    const resolvedIdentifier = {
      userId: headerIdentifier.userId,
      email: resolvedEmailHeader ?? headerIdentifier.email,
    }
    const currentUserId = resolveCurrentUserId(request, keys)
    if (currentUserId != null && keys.length > 0) {
      keys = await enrichIdentitySnapshotsWithKeyHashes(request, keys, currentUserId, resolvedEmailHeader)
    }
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
    const currentUserCandidates = identityUserIdCandidates(request, currentUserId, resolvedIdentifier.email)
    const keysForCurrentUser = keys.filter((key) => matchesIdentityUserId(key.userId, currentUserCandidates))

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
    const billingByKeyId = new Map<string, BillingSignal>()
    for (const item of billingSignals) {
      const id = billingSignalMapKey(item.apiKeyId)
      if (!id) continue
      billingByKeyId.set(id, item)
    }
    const allKnownKeys = collectAllKnownKeysForBillingFetch(keys, teamApiKeys, billingSignals)
    const fallbackBillingUserId = resolvedIdentifier.email ?? (currentUserId != null ? String(currentUserId) : undefined)
    const extraBillingUserIds = Array.from(
      new Set(
        keys
          .map((item) => String(item.userId ?? "").trim())
          .filter((value) => value.length > 0),
      ),
    )
    const [billingSummaryByKey, lifetimeSpendByKey] = await Promise.all([
      fetchBillingSummaryByKey(request, allKnownKeys, resolvedEmailHeader, fallbackBillingUserId, extraBillingUserIds),
      fetchBillingLifetimeSpendByKey(request, allKnownKeys, resolvedEmailHeader, fallbackBillingUserId, extraBillingUserIds),
    ])
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

    const personalKeysFromSnapshot = keysForCurrentUser.filter((key) => hasUsableKeyAlias(key.alias)).map((key) => {
      const signal = billingByKeyId.get(String(key.keyId))
      const billingRow = billingSummaryByKey.get(key.keyId)
      const { currentSpendUsd, monthlyBudgetUsd } = spendAndBudgetForKey(
        signal,
        toNumber(key.monthlyBudgetUsd, 0),
        billingRow,
      )
      const budgetStats = buildBudgetStats(
        monthlyBudgetUsd,
        currentSpendUsd,
        lifetimeSpendForKey(key.keyId, lifetimeSpendByKey, signal),
      )
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
        alias: (key.alias ?? "").trim(),
        provider: key.provider,
        monthlyBudgetUsd,
        status: key.status,
        keyHash: key.keyHash ?? null,
        budgetStats,
        providerStats: {
          currentSpendUsd: budgetStats.currentSpendUsd,
          averageDailySpendUsd,
          averageDailyTokenUsage,
          recentDailySpendUsd,
        },
      }
    })
    const identityBudgetKeys = await fetchIdentityBudgetKeys(
      request,
      currentUserId,
      resolvedIdentifier.email,
      fallbackEmails,
    )
    const personalKeysFromIdentity = identityBudgetKeys
      .map((key) => {
        const keyId = toNumber(key.externalApiKeyId ?? key.apiKeyId, 0)
        if (keyId <= 0) {
          return null
        }
        const signal = billingByKeyId.get(String(keyId))
        const billingRow = billingSummaryByKey.get(keyId)
        const { currentSpendUsd, monthlyBudgetUsd } = spendAndBudgetForKey(
          signal,
          toNumber(key.monthlyBudgetUsd, 0),
          billingRow,
        )
        const budgetStats = buildBudgetStats(
          monthlyBudgetUsd,
          currentSpendUsd,
          lifetimeSpendForKey(keyId, lifetimeSpendByKey, signal),
        )
        const averageDailySpendUsd = toNumber(personalUsageSignal?.averageDailySpendUsd7d, 0)
        const averageDailyTokenUsage = Math.max(
          toNumber(personalUsageSignal?.averageDailyTokenUsage7d, 0),
          resolveDailyTokens(keyId, null),
        )
        const recentDailySpendUsd = (personalUsageSignal?.recentDailySpendUsd ?? [])
          .map((value) => toNumber(value, 0))
          .filter((value) => value >= 0)
        const aliasTrimmed = (key.alias ?? "").trim()
        if (!hasUsableKeyAlias(aliasTrimmed)) {
          return null
        }
        return {
          keyId,
          alias: aliasTrimmed,
          provider: (key.provider ?? "").trim() || "UNKNOWN",
          monthlyBudgetUsd,
          status: "ACTIVE",
          keyHash: null,
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
    const personalKeysById = new Map<number, (typeof personalKeysFromSnapshot)[number]>()
    for (const key of personalKeysFromIdentity) {
      personalKeysById.set(key.keyId, key)
    }
    for (const key of personalKeysFromSnapshot) {
      const current = personalKeysById.get(key.keyId)
      if (!current) {
        personalKeysById.set(key.keyId, key)
        continue
      }
      const snapshotBudget = toNumber(current.monthlyBudgetUsd, 0)
      const merged = spendAndBudgetForKey(
        billingByKeyId.get(String(key.keyId)),
        snapshotBudget,
        billingSummaryByKey.get(key.keyId),
      )
      const mergedBudgetStats = buildBudgetStats(
        merged.monthlyBudgetUsd,
        merged.currentSpendUsd,
        lifetimeSpendForKey(key.keyId, lifetimeSpendByKey, billingByKeyId.get(String(key.keyId))),
      )
      personalKeysById.set(key.keyId, {
        ...current,
        ...key,
        alias: (key.alias ?? "").trim() || (current.alias ?? "").trim(),
        provider: key.provider?.trim() ? key.provider : current.provider,
        monthlyBudgetUsd: merged.monthlyBudgetUsd,
        budgetStats: mergedBudgetStats,
        providerStats: {
          ...key.providerStats,
          currentSpendUsd: mergedBudgetStats.currentSpendUsd,
        },
      })
    }
    const personalKeysMerged = dedupePersonalKeysByCredential(
      Array.from(personalKeysById.values()) as PersonalContextRow[],
      lifetimeSpendByKey,
      billingByKeyId,
    ).filter((row) => hasUsableKeyAlias(row.alias))

    const teamBoardRaw = teamApiKeys.filter((item) => hasUsableKeyAlias(item.alias)).map((item) => {
      const signal = billingByKeyId.get(String(item.teamApiKeyId))
      const billingRow = billingSummaryByKey.get(item.teamApiKeyId)
      const { currentSpendUsd, monthlyBudgetUsd } = spendAndBudgetForKey(
        signal,
        toNumber(item.monthlyBudgetUsd, 0),
        billingRow,
      )
      const budgetStats = buildBudgetStats(
        monthlyBudgetUsd,
        currentSpendUsd,
        lifetimeSpendForKey(item.teamApiKeyId, lifetimeSpendByKey, signal),
      )
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
        alias: (item.alias ?? "").trim(),
        provider: item.provider,
        status: item.status,
        keyHash: item.keyHash ?? null,
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

    const teamBoard = dedupeTeamBoardByCredential(
      teamBoardRaw as TeamContextRow[],
      lifetimeSpendByKey,
      billingByKeyId,
    ).filter((row) =>
      hasUsableKeyAlias(row.alias),
    )

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
      data: personalKeysMerged,
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
