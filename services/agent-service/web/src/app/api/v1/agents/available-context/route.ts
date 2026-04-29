import { NextResponse } from "next/server"

type IdentitySnapshot = {
  keyId: number
  alias: string
  provider: string
  status: string
  monthlyBudgetUsd?: number | null
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
  return (process.env.AI_AGENT_SERVICE_INTERNAL_ORIGIN ?? "http://host.docker.internal:8096").replace(/\/$/, "")
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
    const [keysRes, billingRes] = await Promise.all([
      fetch(`${backendOrigin()}/api/v1/agents/identity-api-keys`, { cache: "no-store" }),
      fetch(`${backendOrigin()}/api/v1/agents/billing-signals`, { cache: "no-store" }),
    ])

    const keys = (keysRes.ok ? ((await keysRes.json()) as IdentitySnapshot[]) : []) ?? []
    const billingSignals = (billingRes.ok ? ((await billingRes.json()) as BillingSignal[]) : []) ?? []
    const billingByKeyId = new Map<string, BillingSignal>(billingSignals.map((item) => [item.apiKeyId, item]))

    const data = keys.map((key) => {
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

    return NextResponse.json({
      data,
      note: "RabbitMQ 이벤트 스냅샷 기반(Identity + Billing). 테스트 DB 조회는 사용하지 않습니다.",
    })
  } catch {
    return NextResponse.json(
      { message: "이벤트 스냅샷 컨텍스트 조회에 실패했습니다." },
      { status: 502 },
    )
  }
}
