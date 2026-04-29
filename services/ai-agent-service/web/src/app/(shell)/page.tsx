"use client"

import { useMemo, useState } from "react"

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

type KeyUsagePreset = {
  keyId: string
  keyLabel: string
  provider: string
  model: string
  teamId: string
  monthlyBudgetUsd: number
  currentSpendUsd: number
  remainingTokens: number
  averageDailyTokenUsage: number
  averageDailySpendUsd: number
  recentDailySpendUsd: number[]
}

type TeamPreset = {
  teamId: string
  teamName: string
  keys: KeyUsagePreset[]
}

type AnalysisResult = {
  keyId: string
  keyLabel: string
  data?: BudgetForecastResponse
  error?: string
}

const TEAM_PRESETS: TeamPreset[] = [
  {
    teamId: "team-001",
    teamName: "Platform Team",
    keys: [
      {
        keyId: "key-agent-prod",
        keyLabel: "Agent Prod Key",
        provider: "google",
        model: "gemini-1.5-flash",
        teamId: "team-001",
        monthlyBudgetUsd: 160,
        currentSpendUsd: 124,
        remainingTokens: 240000,
        averageDailyTokenUsage: 32000,
        averageDailySpendUsd: 9.8,
        recentDailySpendUsd: [8.2, 9.1, 9.0, 11.4],
      },
      {
        keyId: "key-rag-dev",
        keyLabel: "RAG Dev Key",
        provider: "google",
        model: "gemini-1.5-flash",
        teamId: "team-001",
        monthlyBudgetUsd: 80,
        currentSpendUsd: 41,
        remainingTokens: 520000,
        averageDailyTokenUsage: 18000,
        averageDailySpendUsd: 3.1,
        recentDailySpendUsd: [2.8, 3.0, 3.4, 3.2],
      },
      {
        keyId: "key-ops-batch",
        keyLabel: "Ops Batch Key",
        provider: "google",
        model: "gemini-1.5-flash",
        teamId: "team-001",
        monthlyBudgetUsd: 120,
        currentSpendUsd: 117,
        remainingTokens: 90000,
        averageDailyTokenUsage: 19000,
        averageDailySpendUsd: 8.4,
        recentDailySpendUsd: [6.1, 6.4, 7.0, 12.8],
      },
    ],
  },
  {
    teamId: "team-002",
    teamName: "Data Team",
    keys: [
      {
        keyId: "key-data-pipeline",
        keyLabel: "Data Pipeline Key",
        provider: "google",
        model: "gemini-1.5-flash",
        teamId: "team-002",
        monthlyBudgetUsd: 220,
        currentSpendUsd: 75,
        remainingTokens: 680000,
        averageDailyTokenUsage: 23000,
        averageDailySpendUsd: 5.6,
        recentDailySpendUsd: [5.0, 5.3, 5.7, 6.1],
      },
    ],
  },
]

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

export default function AgentPage() {
  const [selectedTeamId, setSelectedTeamId] = useState<string>(TEAM_PRESETS[0]?.teamId ?? "")
  const [loading, setLoading] = useState<boolean>(false)
  const [loadingMessage, setLoadingMessage] = useState<string>("")
  const [results, setResults] = useState<AnalysisResult[]>([])

  const selectedTeam = useMemo(
    () => TEAM_PRESETS.find((team) => team.teamId === selectedTeamId) ?? TEAM_PRESETS[0],
    [selectedTeamId],
  )

  const runTeamAnalysis = async () => {
    if (!selectedTeam) return

    setLoading(true)
    setResults([])
    const billingCycleEndDate = buildBillingCycleEndDate()
    const nextResults: AnalysisResult[] = []

    for (let i = 0; i < selectedTeam.keys.length; i += 1) {
      const keyPreset = selectedTeam.keys[i]
      setLoadingMessage(`${selectedTeam.teamId}의 최근 사용량을 분석 중입니다... (${i + 1}/${selectedTeam.keys.length})`)

      try {
        const response = await fetch("/agent/api/v1/agents/budget-forecast-assistant", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            userId: "team-bot",
            teamId: keyPreset.teamId,
            provider: keyPreset.provider,
            model: keyPreset.model,
            monthlyBudgetUsd: keyPreset.monthlyBudgetUsd,
            currentSpendUsd: keyPreset.currentSpendUsd,
            remainingTokens: keyPreset.remainingTokens,
            averageDailyTokenUsage: keyPreset.averageDailyTokenUsage,
            averageDailySpendUsd: keyPreset.averageDailySpendUsd,
            billingCycleEndDate,
            recentDailySpendUsd: keyPreset.recentDailySpendUsd,
          }),
        })

        if (!response.ok) {
          const text = await response.text()
          throw new Error(text || `요청 실패 (${response.status})`)
        }

        const data = (await response.json()) as BudgetForecastResponse
        nextResults.push({
          keyId: keyPreset.keyId,
          keyLabel: keyPreset.keyLabel,
          data,
        })
      } catch (error) {
        const message = error instanceof Error ? error.message : "분석 요청 실패"
        nextResults.push({
          keyId: keyPreset.keyId,
          keyLabel: keyPreset.keyLabel,
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
          <h2 className="text-sm font-semibold">팀 선택</h2>
          <select
            className="w-full rounded-md border bg-background px-3 py-2 text-sm"
            value={selectedTeam?.teamId}
            onChange={(event) => setSelectedTeamId(event.target.value)}
            disabled={loading}
          >
            {TEAM_PRESETS.map((team) => (
              <option key={team.teamId} value={team.teamId}>
                {team.teamId} ({team.teamName})
              </option>
            ))}
          </select>
        </div>

        <div className="space-y-2">
          <h3 className="text-sm font-semibold">팀 API 키</h3>
          <ul className="space-y-1 text-sm text-muted-foreground">
            {selectedTeam?.keys.map((item) => (
              <li key={item.keyId} className="rounded-md border px-2 py-1">
                {item.keyLabel}
              </li>
            ))}
          </ul>
        </div>

        <button
          type="button"
          className="w-full rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:opacity-60"
          onClick={runTeamAnalysis}
          disabled={loading}
        >
          {loading ? "분석 중..." : "팀 분석 시작"}
        </button>
      </aside>

      <section className="space-y-4 md:col-span-9">
        {loading ? (
          <div className="flex min-h-[260px] flex-col items-center justify-center gap-3 rounded-xl border bg-card p-6 text-center">
            <div className="h-10 w-10 animate-spin rounded-full border-4 border-muted border-t-primary" />
            <p className="text-sm text-muted-foreground">
              {loadingMessage || `${selectedTeam?.teamId ?? "team"}의 최근 사용량을 분석 중입니다...`}
            </p>
          </div>
        ) : null}

        {!loading && results.length > 0
          ? results.map((result) => (
              <article key={result.keyId} className="space-y-3 rounded-xl border bg-card p-4">
                <div className="flex items-center justify-between gap-2">
                  <h2 className="text-lg font-semibold">{result.keyLabel}</h2>
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
                      {result.data.recommendedActions.map((action) => (
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
