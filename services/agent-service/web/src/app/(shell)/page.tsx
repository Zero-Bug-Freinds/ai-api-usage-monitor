"use client"

import { useEffect, useMemo, useState } from "react"

type BudgetForecastResponse = {
  healthStatus: "HEALTHY" | "WARNING" | "CRITICAL" | string
  predictedRunOutDate: string
  daysUntilRunOut: number
  daysUntilBillingCycleEnd: number
  billingDateGapDays: number
  budgetUtilizationPercent: string
  assistantMessage: string
  recommendedActions: string[]
}

type AvailableKeyContext = {
  keyId: number
  keyLabel: string
  provider: string
  monthlyBudgetUsd: number
  status: string
  providerStats: {
    currentSpendUsd: number
    averageDailySpendUsd: number
    averageDailyTokenUsage: number
    recentDailySpendUsd: number[]
  }
  billingThresholdPct?: number
}

type TeamBoardItem = {
  teamId: number
  teamApiKeyId: number
  ownerUserId?: string | null
  visibility?: string | null
  alias: string
  provider: string
  status: string
}

type UserContext = {
  userId: number
  activeTeamId?: number | null
  role?: string | null
}

type AnalysisResult = {
  keyId: number
  keyLabel: string
  provider: string
  data?: BudgetForecastResponse
  error?: string
}

function buildBillingCycleEndDate(): string {
  const date = new Date()
  date.setDate(date.getDate() + 14)
  return date.toISOString().split("T")[0] ?? ""
}

function statusClassName(status: string): string {
  if (status === "CRITICAL") return "bg-red-100 text-red-700"
  if (status === "WARNING") return "bg-amber-100 text-amber-700"
  return "bg-emerald-100 text-emerald-700"
}

function formatThresholdPercent(value?: number): string {
  if (typeof value !== "number" || !Number.isFinite(value)) return ""
  const normalized = value <= 1 ? value * 100 : value
  return `${Math.round(normalized)}%`
}

export default function AgentPage() {
  const [loading, setLoading] = useState<boolean>(false)
  const [loadingMessage, setLoadingMessage] = useState<string>("")
  const [results, setResults] = useState<AnalysisResult[]>([])
  const [keys, setKeys] = useState<AvailableKeyContext[]>([])
  const [teamBoard, setTeamBoard] = useState<TeamBoardItem[]>([])
  const [userContext, setUserContext] = useState<UserContext | null>(null)
  const [bootstrapError, setBootstrapError] = useState<string>("")
  const [note, setNote] = useState<string>("")

  const contextTitle = useMemo(() => "개인 API Key 이벤트 컨텍스트", [])

  useEffect(() => {
    void loadAvailableContext()
  }, [])

  const loadAvailableContext = async () => {
    setBootstrapError("")
    try {
      const response = await fetch("/agent/api/v1/agents/available-context", { cache: "no-store" })
      if (!response.ok) {
        const message = await response.text()
        throw new Error(message || "컨텍스트 조회 실패")
      }
      const payload = (await response.json()) as {
        note?: string
        userContext?: UserContext | null
        teamBoard?: TeamBoardItem[]
        data?: Array<{
          keyId: number
          alias: string
          provider: string
          monthlyBudgetUsd: number
          status: string
          providerStats: {
            currentSpendUsd: number
            averageDailySpendUsd: number
            averageDailyTokenUsage: number
            recentDailySpendUsd: number[]
          }
        }>
      }

      const normalized =
        payload.data?.map((item) => ({
          keyId: item.keyId,
          keyLabel: item.alias,
          provider: item.provider,
          monthlyBudgetUsd: item.monthlyBudgetUsd,
          status: item.status,
          providerStats: item.providerStats,
        })) ?? []
      setKeys(normalized)
      setTeamBoard(payload.teamBoard ?? [])
      setUserContext(payload.userContext ?? null)
      setNote(payload.note ?? "")
    } catch (error) {
      const message = error instanceof Error ? error.message : "컨텍스트 조회 실패"
      setBootstrapError(message)
    }
  }

  const runTeamAnalysis = async () => {
    if (keys.length === 0) return

    setLoading(true)
    setResults([])
    const billingCycleEndDate = buildBillingCycleEndDate()
    const nextResults: AnalysisResult[] = []

    for (let i = 0; i < keys.length; i += 1) {
      const keyItem = keys[i]
      setLoadingMessage(`개인 API 키 사용량을 분석 중입니다... (${i + 1}/${keys.length})`)

      try {
        const response = await fetch("/agent/api/v1/agents/budget-forecast-assistant", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            userId: "personal-user",
            teamId: null,
            provider: keyItem.provider,
            model: "identity-linked-model",
            monthlyBudgetUsd: keyItem.monthlyBudgetUsd,
            currentSpendUsd: keyItem.providerStats.currentSpendUsd,
            remainingTokens: Math.max(Math.round(keyItem.providerStats.averageDailyTokenUsage * 14), 1),
            averageDailyTokenUsage: keyItem.providerStats.averageDailyTokenUsage,
            averageDailySpendUsd: keyItem.providerStats.averageDailySpendUsd,
            billingCycleEndDate,
            recentDailySpendUsd: keyItem.providerStats.recentDailySpendUsd,
          }),
        })

        if (!response.ok) {
          const text = await response.text()
          throw new Error(text || `요청 실패 (${response.status})`)
        }

        const data = (await response.json()) as BudgetForecastResponse
        nextResults.push({
          keyId: keyItem.keyId,
          keyLabel: keyItem.keyLabel,
          provider: keyItem.provider,
          data,
        })
      } catch (error) {
        const message = error instanceof Error ? error.message : "분석 요청 실패"
        nextResults.push({
          keyId: keyItem.keyId,
          keyLabel: keyItem.keyLabel,
          provider: keyItem.provider,
          error: message,
        })
      }
    }

    setResults(nextResults)
    setLoading(false)
    setLoadingMessage("")
  }

  return (
    <div className="grid min-h-[70vh] gap-4 p-4 md:grid-cols-12">
      <aside className="space-y-4 rounded-xl border bg-card p-4 md:col-span-3">
        <div className="space-y-1">
          <h2 className="text-sm font-semibold">{contextTitle}</h2>
          <p className="text-xs text-muted-foreground">RabbitMQ로 수신한 이벤트 스냅샷만 사용합니다.</p>
        </div>

        <div className="space-y-2">
          <h3 className="text-sm font-semibold">개인 API 키</h3>
          {bootstrapError ? (
            <div className="rounded-md border border-red-200 bg-red-50 px-2 py-1 text-xs text-red-700">{bootstrapError}</div>
          ) : null}
          {note ? <p className="text-xs text-muted-foreground">{note}</p> : null}
          <ul className="space-y-1 text-sm text-muted-foreground">
            {keys.map((item: AvailableKeyContext) => (
              <li key={item.keyId} className="rounded-md border px-2 py-1">
                {item.keyLabel} ({item.provider}) · ${item.monthlyBudgetUsd}
                {item.billingThresholdPct ? ` · 임계치 ${formatThresholdPercent(item.billingThresholdPct)}` : ""}
              </li>
            ))}
          </ul>
        </div>

        <div className="space-y-2">
          <h3 className="text-sm font-semibold">팀 보드</h3>
          <p className="text-xs text-muted-foreground">
            {userContext?.activeTeamId
              ? `활성 팀: ${userContext.activeTeamId} / 역할: ${userContext.role ?? "-"}`
              : "활성 팀 컨텍스트가 없습니다."}
          </p>
          <ul className="space-y-1 text-sm text-muted-foreground">
            {teamBoard.map((item: TeamBoardItem) => (
              <li key={`${item.teamId}-${item.teamApiKeyId}`} className="rounded-md border px-2 py-1">
                T{item.teamId} · {item.alias} ({item.provider}) · {item.status}
                {item.visibility ? ` · ${item.visibility}` : ""}
              </li>
            ))}
            {teamBoard.length === 0 ? (
              <li className="rounded-md border border-dashed px-2 py-1 text-xs">표시할 팀 키가 없습니다.</li>
            ) : null}
          </ul>
        </div>

        <button
          type="button"
          className="w-full rounded-md border px-4 py-2 text-sm font-medium"
          onClick={loadAvailableContext}
          disabled={loading}
        >
          지금 가능한 데이터 새로고침
        </button>

        <button
          type="button"
          className="w-full rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:opacity-60"
          onClick={runTeamAnalysis}
          disabled={loading || keys.length === 0}
        >
          {loading ? "분석 중..." : "개인 키 분석 시작"}
        </button>
      </aside>

      <section className="space-y-4 md:col-span-9">
        {loading ? (
          <div className="flex min-h-[260px] flex-col items-center justify-center gap-3 rounded-xl border bg-card p-6 text-center">
            <div className="h-10 w-10 animate-spin rounded-full border-4 border-muted border-t-primary" />
            <p className="text-sm text-muted-foreground">
              {loadingMessage || "개인 API 키 사용량을 분석 중입니다..."}
            </p>
          </div>
        ) : null}

        {!loading && results.length > 0
          ? results.map((result: AnalysisResult) => (
              <article key={result.keyId} className="space-y-3 rounded-xl border bg-card p-4">
                <div className="flex items-center justify-between gap-2">
                  <h2 className="text-lg font-semibold">{result.keyLabel} ({result.provider})</h2>
                  {result.data ? (
                    <span className={`rounded-full px-2 py-1 text-xs font-semibold ${statusClassName(result.data.healthStatus)}`}>
                      {result.data.healthStatus}
                    </span>
                  ) : null}
                </div>

                {result.error ? (
                  <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">{result.error}</div>
                ) : null}

                {result.data ? (
                  <>
                    <div className="grid gap-2 text-sm md:grid-cols-2">
                      <p>예상 소진일: {result.data.predictedRunOutDate}</p>
                      <p>소진까지 남은 일수: {result.data.daysUntilRunOut}일</p>
                      <p>결제일까지 남은 일수: {result.data.daysUntilBillingCycleEnd}일</p>
                      <p>결제일 차이(결제일-소진일): {result.data.billingDateGapDays}일</p>
                      <p>예산 사용률: {result.data.budgetUtilizationPercent}%</p>
                    </div>

                    <div className="rounded-md bg-muted p-3 text-sm">{result.data.assistantMessage}</div>

                    <ul className="list-disc space-y-1 pl-5 text-sm">
                      {result.data.recommendedActions.map((action: string) => (
                        <li key={`${result.keyId}-${action}`}>{action}</li>
                      ))}
                    </ul>
                  </>
                ) : null}
              </article>
            ))
          : null}
      </section>
    </div>
  )
}
