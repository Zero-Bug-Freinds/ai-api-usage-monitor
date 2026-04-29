"use client"

import { FormEvent, useMemo, useState } from "react"

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

type FormState = {
  userId: string
  teamId: string
  provider: string
  model: string
  monthlyBudgetUsd: string
  currentSpendUsd: string
  remainingTokens: string
  averageDailyTokenUsage: string
  averageDailySpendUsd: string
  billingCycleEndDate: string
  recentDailySpendUsd: string
}

const INITIAL_FORM: FormState = {
  userId: "demo-user",
  teamId: "team-001",
  provider: "google",
  model: "gemini-1.5-flash",
  monthlyBudgetUsd: "100",
  currentSpendUsd: "72",
  remainingTokens: "120000",
  averageDailyTokenUsage: "9000",
  averageDailySpendUsd: "4.8",
  billingCycleEndDate: "",
  recentDailySpendUsd: "3.5,4.1,4.0,5.8",
}

function statusClassName(status: string): string {
  if (status === "CRITICAL") return "bg-red-100 text-red-700"
  if (status === "WARNING") return "bg-amber-100 text-amber-700"
  return "bg-emerald-100 text-emerald-700"
}

export default function AgentPage() {
  const defaultBillingDate = useMemo(() => {
    const date = new Date()
    date.setDate(date.getDate() + 14)
    return date.toISOString().split("T")[0] ?? ""
  }, [])

  const [form, setForm] = useState<FormState>({
    ...INITIAL_FORM,
    billingCycleEndDate: defaultBillingDate,
  })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string>("")
  const [result, setResult] = useState<BudgetForecastResponse | null>(null)

  const onChange = (key: keyof FormState, value: string) => {
    setForm((prev) => ({ ...prev, [key]: value }))
  }

  const onSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setLoading(true)
    setError("")
    setResult(null)

    const recentDailySpendUsd = form.recentDailySpendUsd
      .split(",")
      .map((v) => v.trim())
      .filter(Boolean)
      .map((v) => Number(v))
      .filter((v) => Number.isFinite(v))

    const payload = {
      userId: form.userId,
      teamId: form.teamId || null,
      provider: form.provider || null,
      model: form.model || null,
      monthlyBudgetUsd: Number(form.monthlyBudgetUsd),
      currentSpendUsd: Number(form.currentSpendUsd),
      remainingTokens: Number(form.remainingTokens),
      averageDailyTokenUsage: Number(form.averageDailyTokenUsage),
      averageDailySpendUsd: Number(form.averageDailySpendUsd),
      billingCycleEndDate: form.billingCycleEndDate,
      recentDailySpendUsd,
    }

    try {
      const response = await fetch("/agent/api/v1/agents/budget-forecast-assistant", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      })
      if (!response.ok) {
        const message = await response.text()
        throw new Error(message || `요청 실패 (${response.status})`)
      }
      const data = (await response.json()) as BudgetForecastResponse
      setResult(data)
    } catch (submitError) {
      const message =
        submitError instanceof Error ? submitError.message : "요청 중 알 수 없는 오류가 발생했습니다."
      setError(message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="space-y-6 p-4">
      <div className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">비서</h1>
        <p className="text-sm text-muted-foreground">
          서버에 구축한 예산/토큰 소진 예측 API를 호출해 결과를 바로 확인합니다.
        </p>
      </div>

      <form className="grid gap-3 rounded-xl border bg-card p-4 md:grid-cols-2" onSubmit={onSubmit}>
        <input
          className="rounded-md border px-3 py-2 text-sm"
          placeholder="userId"
          value={form.userId}
          onChange={(e) => onChange("userId", e.target.value)}
          required
        />
        <input
          className="rounded-md border px-3 py-2 text-sm"
          placeholder="teamId"
          value={form.teamId}
          onChange={(e) => onChange("teamId", e.target.value)}
        />
        <input
          className="rounded-md border px-3 py-2 text-sm"
          placeholder="provider"
          value={form.provider}
          onChange={(e) => onChange("provider", e.target.value)}
        />
        <input
          className="rounded-md border px-3 py-2 text-sm"
          placeholder="model"
          value={form.model}
          onChange={(e) => onChange("model", e.target.value)}
        />
        <input
          className="rounded-md border px-3 py-2 text-sm"
          type="number"
          step="0.01"
          placeholder="monthlyBudgetUsd"
          value={form.monthlyBudgetUsd}
          onChange={(e) => onChange("monthlyBudgetUsd", e.target.value)}
          required
        />
        <input
          className="rounded-md border px-3 py-2 text-sm"
          type="number"
          step="0.01"
          placeholder="currentSpendUsd"
          value={form.currentSpendUsd}
          onChange={(e) => onChange("currentSpendUsd", e.target.value)}
          required
        />
        <input
          className="rounded-md border px-3 py-2 text-sm"
          type="number"
          step="1"
          placeholder="remainingTokens"
          value={form.remainingTokens}
          onChange={(e) => onChange("remainingTokens", e.target.value)}
          required
        />
        <input
          className="rounded-md border px-3 py-2 text-sm"
          type="number"
          step="0.01"
          placeholder="averageDailyTokenUsage"
          value={form.averageDailyTokenUsage}
          onChange={(e) => onChange("averageDailyTokenUsage", e.target.value)}
          required
        />
        <input
          className="rounded-md border px-3 py-2 text-sm"
          type="number"
          step="0.01"
          placeholder="averageDailySpendUsd"
          value={form.averageDailySpendUsd}
          onChange={(e) => onChange("averageDailySpendUsd", e.target.value)}
          required
        />
        <input
          className="rounded-md border px-3 py-2 text-sm"
          type="date"
          value={form.billingCycleEndDate}
          onChange={(e) => onChange("billingCycleEndDate", e.target.value)}
          required
        />
        <input
          className="rounded-md border px-3 py-2 text-sm md:col-span-2"
          placeholder="recentDailySpendUsd (comma separated, e.g. 3.5,4.1,4.0,5.8)"
          value={form.recentDailySpendUsd}
          onChange={(e) => onChange("recentDailySpendUsd", e.target.value)}
        />

        <div className="md:col-span-2">
          <button
            type="submit"
            className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:opacity-60"
            disabled={loading}
          >
            {loading ? "예측 계산 중..." : "비서에게 분석 요청"}
          </button>
        </div>
      </form>

      {error ? (
        <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</div>
      ) : null}

      {result ? (
        <section className="space-y-3 rounded-xl border bg-card p-4">
          <div className="flex items-center gap-2">
            <h2 className="text-lg font-semibold">분석 결과</h2>
            <span className={`rounded-full px-2 py-1 text-xs font-semibold ${statusClassName(result.healthStatus)}`}>
              {result.healthStatus}
            </span>
          </div>

          <div className="grid gap-2 text-sm md:grid-cols-2">
            <p>예상 소진일: {result.predictedRunOutDate}</p>
            <p>소진까지 남은 일수: {result.daysUntilRunOut}일</p>
            <p>결제일까지 남은 일수: {result.daysUntilBillingCycleEnd}일</p>
            <p>결제일 차이(결제일-소진일): {result.billingDateGapDays}일</p>
            <p>예산 사용률: {result.budgetUtilizationPercent}%</p>
          </div>

          <div className="rounded-md bg-muted p-3 text-sm">{result.assistantMessage}</div>

          <ul className="list-disc space-y-1 pl-5 text-sm">
            {result.recommendedActions.map((action) => (
              <li key={action}>{action}</li>
            ))}
          </ul>
        </section>
      ) : null}
    </div>
  )
}
