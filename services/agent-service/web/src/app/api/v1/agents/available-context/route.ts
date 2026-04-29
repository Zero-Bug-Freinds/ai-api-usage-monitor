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
  teamApiKeyId: number
  ownerUserId?: string | null
  visibility?: string | null
  alias: string
  provider: string
  status: string
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

function backendOrigin(): string {
  return (process.env.AI_AGENT_SERVICE_INTERNAL_ORIGIN ?? "http://agent-service:8096").replace(/\/$/, "")
}

function toNumber(value: unknown, fallback = 0): number {
  if (typeof value === "number" && Number.isFinite(value)) return value
  if (typeof value === "string") {
    const parsed = Number(value)
    if (Number.isFinite(parsed)) return parsed
  }
  return fallback
}

export async function GET() {
  try {
    const [keysRes, billingRes, userContextsRes, teamKeysRes] = await Promise.all([
      fetch(`${backendOrigin()}/api/v1/agents/identity-api-keys`, { cache: "no-store" }),
      fetch(`${backendOrigin()}/api/v1/agents/billing-signals`, { cache: "no-store" }),
      fetch(`${backendOrigin()}/api/v1/agents/user-contexts`, { cache: "no-store" }),
      fetch(`${backendOrigin()}/api/v1/agents/team-api-keys`, { cache: "no-store" }),
    ])

    const keys = (keysRes.ok ? ((await keysRes.json()) as IdentitySnapshot[]) : []) ?? []
    const billingSignals = (billingRes.ok ? ((await billingRes.json()) as BillingSignal[]) : []) ?? []
    const userContexts = (userContextsRes.ok ? ((await userContextsRes.json()) as UserContextSnapshot[]) : []) ?? []
    const teamApiKeys = (teamKeysRes.ok ? ((await teamKeysRes.json()) as TeamApiKeySnapshot[]) : []) ?? []
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
    const activeTeamId = activeTeamContext?.activeTeamId ?? null
    const teamBoard =
      activeTeamId == null
        ? []
        : teamApiKeys
            .filter((item) => item.teamId === activeTeamId)
            .map((item) => ({
              teamId: item.teamId,
              teamApiKeyId: item.teamApiKeyId,
              ownerUserId: item.ownerUserId,
              visibility: item.visibility ?? "TEAM",
              alias: item.alias,
              provider: item.provider,
              status: item.status,
            }))

    return NextResponse.json({
      data: personalKeys,
      userContext: activeTeamContext,
      teamBoard,
      note: "RabbitMQ 이벤트 스냅샷 기반(Identity + Team + Billing). 테스트 DB 조회는 사용하지 않습니다.",
    })
  } catch {
    return NextResponse.json(
      { message: "이벤트 스냅샷 컨텍스트 조회에 실패했습니다." },
      { status: 502 },
    )
  }
}
