import { NextRequest, NextResponse } from "next/server"

type IdentityKeyItem = {
  id: number
  provider: "GEMINI" | "OPENAI" | "ANTHROPIC" | string
  alias: string
  monthlyBudgetUsd?: number | null
  deletionRequestedAt?: string | null
}

type UsageSummaryResponse = {
  totalRequests: number
  totalErrors: number
  totalInputTokens: number
  totalEstimatedCost: number
}

type DailyUsagePoint = {
  date: string
  requestCount: number
  errorCount: number
  inputTokens: number
  estimatedCost: number
}

type ProviderStats = {
  provider: string
  currentSpendUsd: number
  averageDailySpendUsd: number
  averageDailyTokenUsage: number
  recentDailySpendUsd: number[]
}

function getCookieValue(cookieHeader: string | null, name: string): string | null {
  if (!cookieHeader) return null
  const prefix = `${name}=`
  for (const part of cookieHeader.split(";")) {
    const trimmed = part.trim()
    if (trimmed.startsWith(prefix)) {
      const value = trimmed.slice(prefix.length)
      return value.length > 0 ? value : null
    }
  }
  return null
}

function gatewayBaseUrl(): string {
  const fromEnv = process.env.WEB_GATEWAY_URL ?? process.env.GATEWAY_URL
  if (fromEnv) return fromEnv.replace(/\/+$/, "")
  return "http://api-gateway-service:8080"
}

function toNumber(value: unknown, fallback = 0): number {
  if (typeof value === "number" && Number.isFinite(value)) return value
  if (typeof value === "string") {
    const parsed = Number(value)
    if (Number.isFinite(parsed)) return parsed
  }
  return fallback
}

export async function GET(request: NextRequest) {
  const accessToken = getCookieValue(request.headers.get("cookie"), "access_token")
  if (!accessToken) {
    return NextResponse.json({ message: "로그인이 필요합니다." }, { status: 401 })
  }

  const to = new Date()
  const from = new Date()
  from.setDate(to.getDate() - 6)
  const toDate = to.toISOString().slice(0, 10)
  const fromDate = from.toISOString().slice(0, 10)
  const rangeDays = 7

  try {
    const keysRes = await fetch(`${gatewayBaseUrl()}/api/identity/auth/external-keys`, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        Accept: "application/json",
      },
      cache: "no-store",
    })

    if (!keysRes.ok) {
      const text = await keysRes.text()
      return NextResponse.json({ message: text || "외부 키 목록 조회 실패" }, { status: keysRes.status })
    }

    const keysJson = (await keysRes.json()) as { data?: IdentityKeyItem[] }
    const keys = Array.isArray(keysJson.data) ? keysJson.data : []
    const providers = Array.from(new Set(keys.map((k) => k.provider)))

    const providerStatsMap = new Map<string, ProviderStats>()

    for (const provider of providers) {
      const query = `from=${fromDate}&to=${toDate}&provider=${encodeURIComponent(provider)}`
      const [summaryRes, dailyRes] = await Promise.all([
        fetch(`${gatewayBaseUrl()}/api/v1/usage/dashboard/summary?${query}`, {
          headers: {
            Authorization: `Bearer ${accessToken}`,
            Accept: "application/json",
          },
          cache: "no-store",
        }),
        fetch(`${gatewayBaseUrl()}/api/v1/usage/dashboard/series/daily?${query}`, {
          headers: {
            Authorization: `Bearer ${accessToken}`,
            Accept: "application/json",
          },
          cache: "no-store",
        }),
      ])

      const summaryJson = summaryRes.ok ? ((await summaryRes.json()) as UsageSummaryResponse) : null
      const dailyJson = dailyRes.ok ? ((await dailyRes.json()) as DailyUsagePoint[]) : []

      const totalEstimatedCost = toNumber(summaryJson?.totalEstimatedCost, 0)
      const totalInputTokens = toNumber(summaryJson?.totalInputTokens, 0)
      const averageDailySpendUsd = Math.max(totalEstimatedCost / rangeDays, 0.01)
      const averageDailyTokenUsage = Math.max(totalInputTokens / rangeDays, 1)
      const recentDailySpendUsd = (Array.isArray(dailyJson) ? dailyJson : [])
        .slice(-4)
        .map((point) => toNumber(point.estimatedCost, 0))

      providerStatsMap.set(provider, {
        provider,
        currentSpendUsd: totalEstimatedCost,
        averageDailySpendUsd,
        averageDailyTokenUsage,
        recentDailySpendUsd,
      })
    }

    const data = keys.map((key) => ({
      keyId: key.id,
      alias: key.alias,
      provider: key.provider,
      monthlyBudgetUsd: toNumber(key.monthlyBudgetUsd, 0),
      status: key.deletionRequestedAt ? "DELETION_REQUESTED" : "ACTIVE",
      providerStats:
        providerStatsMap.get(key.provider) ??
        ({
          provider: key.provider,
          currentSpendUsd: 0,
          averageDailySpendUsd: 0.01,
          averageDailyTokenUsage: 1,
          recentDailySpendUsd: [],
        } satisfies ProviderStats),
    }))

    return NextResponse.json({
      period: { from: fromDate, to: toDate },
      data,
      note: "키별 사용량/지출 원천은 아직 provider 단위 집계만 사용합니다.",
    })
  } catch {
    return NextResponse.json(
      { message: "identity/usage 컨텍스트 조회에 실패했습니다." },
      { status: 502 },
    )
  }
}
