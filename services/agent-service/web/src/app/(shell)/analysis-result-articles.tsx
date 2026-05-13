"use client"

import type { AnalysisResult } from "./agent-result-shared"

export type AnalysisResultLoadingTarget = {
  scope: "PERSONAL" | "TEAM"
  keyId: number
  action: "ANALYSIS" | "RECOMMENDATION"
}

type AnalysisResultArticlesProps = {
  results: AnalysisResult[]
  loadingTarget: AnalysisResultLoadingTarget | null
  loadingMessage: string
  emptyLabel?: string | null
}

function parseInputOutputRatio(value: string | null | undefined): { input: number; output: number } | null {
  if (!value) return null
  const [inputRaw, outputRaw] = value.split(":")
  const input = Number(inputRaw)
  const output = Number(outputRaw)
  if (!Number.isFinite(input) || !Number.isFinite(output) || input < 0 || output < 0) return null
  return { input, output }
}

function estimateSavingsUsd(
  estimatedSavingsPct: number | string,
  recommendedMonthlyCostUsd: number | string,
): number | null {
  const pct = Number(estimatedSavingsPct)
  const recommended = Number(recommendedMonthlyCostUsd)
  if (!Number.isFinite(pct) || !Number.isFinite(recommended)) return null
  if (pct <= 0 || pct >= 100 || recommended < 0) return null
  const estimatedCurrent = recommended / (1 - pct / 100)
  const estimatedSavingsUsd = estimatedCurrent - recommended
  if (!Number.isFinite(estimatedSavingsUsd) || estimatedSavingsUsd < 0) return null
  return estimatedSavingsUsd
}

function latencyStatus(latencyMs: number): { label: string; className: string; progress: number } {
  if (latencyMs <= 600) {
    return { label: "빠름", className: "bg-emerald-100 text-emerald-700", progress: 25 }
  }
  if (latencyMs <= 1200) {
    return { label: "보통", className: "bg-amber-100 text-amber-700", progress: 55 }
  }
  if (latencyMs <= 2000) {
    return { label: "지연", className: "bg-orange-100 text-orange-700", progress: 78 }
  }
  return { label: "높은 지연", className: "bg-red-100 text-red-700", progress: 92 }
}

function latencyStatusNullable(latencyMs: number | null | undefined): { label: string; className: string; progress: number } {
  if (latencyMs == null || !Number.isFinite(latencyMs) || latencyMs < 0) {
    return { label: "지표 없음", className: "bg-muted text-muted-foreground", progress: 0 }
  }
  return latencyStatus(latencyMs)
}

function ratioDominance(ratio: { input: number; output: number } | null): {
  label: string
  className: string
  inputPct: number
  outputPct: number
} {
  if (!ratio) {
    return { label: "지표 없음", className: "bg-muted text-muted-foreground", inputPct: 0, outputPct: 0 }
  }
  const total = ratio.input + ratio.output
  if (total <= 0) {
    return { label: "지표 없음", className: "bg-muted text-muted-foreground", inputPct: 0, outputPct: 0 }
  }
  const inputPct = (ratio.input / total) * 100
  const outputPct = 100 - inputPct
  if (inputPct >= 80) {
    return { label: "입력 중심", className: "bg-blue-100 text-blue-700", inputPct, outputPct }
  }
  if (outputPct >= 80) {
    return { label: "출력 중심", className: "bg-violet-100 text-violet-700", inputPct, outputPct }
  }
  return { label: "균형형", className: "bg-slate-100 text-slate-700", inputPct, outputPct }
}

function formatBillingMetric(value: number | null | undefined): string {
  if (value == null) {
    return "표시 불가 — 결제일 미입력"
  }
  return `${value}일`
}

function statusClassName(status: string): string {
  if (status === "CRITICAL") return "bg-red-100 text-red-700"
  if (status === "WARNING") return "bg-amber-100 text-amber-700"
  return "bg-emerald-100 text-emerald-700"
}

function localizedHealthStatus(status: string): string {
  if (status === "CRITICAL") return "위험"
  if (status === "WARNING") return "주의"
  if (status === "HEALTHY") return "양호"
  return status
}

function localizedConfidenceLevel(level: string | null | undefined): string {
  if (level == null) return "미표시"
  if (level === "HIGH") return "높음"
  if (level === "MEDIUM") return "보통"
  if (level === "LOW") return "낮음"
  return level
}

function localizeAssistantMessage(message: string): string {
  return message
    .replaceAll("CRITICAL", "위험")
    .replaceAll("WARNING", "주의")
    .replaceAll("HEALTHY", "양호")
}

export function AnalysisResultArticles({
  results,
  loadingTarget,
  loadingMessage,
  emptyLabel,
}: AnalysisResultArticlesProps) {
  if (results.length === 0) {
    if (emptyLabel == null || emptyLabel === "") {
      return null
    }
    return (
      <div className="rounded-xl border border-dashed bg-muted/20 px-4 py-8 text-center text-sm text-muted-foreground">
        {emptyLabel}
      </div>
    )
  }

  return (
    <>
      {results.map((result: AnalysisResult) => (
        <article key={result.keyId} className="space-y-3 rounded-xl border bg-card p-4">
          <div className="flex items-center justify-between gap-2">
            <h2 className="text-lg font-semibold">
              {result.keyLabel} ({result.provider})
            </h2>
            {result.data ? (
              <span className={`rounded-full px-2 py-1 text-xs font-semibold ${statusClassName(result.data.healthStatus)}`}>
                {result.data.healthStatusLabel || localizedHealthStatus(result.data.healthStatus)}
              </span>
            ) : null}
          </div>

          {result.error ? (
            <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">{result.error}</div>
          ) : null}

          {result.forecastGaps != null && result.forecastGaps.length > 0 ? (
            <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
              <p className="font-medium">일부는 이벤트가 없어 표시·추정이 제한됩니다.</p>
              <ul className="mt-2 list-disc space-y-1 pl-5">
                {result.forecastGaps.map((line: string) => (
                  <li key={`${result.keyId}-gap-${line}`}>{line}</li>
                ))}
              </ul>
            </div>
          ) : null}

          <div className="grid gap-3">
            <div className="order-2 space-y-2 rounded-md border border-dashed bg-muted/20 p-3">
              <p className="text-xs font-medium text-muted-foreground">모델 추천</p>
              {loadingTarget?.keyId === result.keyId && loadingTarget.action === "RECOMMENDATION" ? (
                <div className="rounded-md border bg-background p-3 text-xs text-muted-foreground">
                  {loadingMessage || "추천 로딩 중..."}
                </div>
              ) : null}
              {result.recommendationError ? (
                <div className="rounded-md border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
                  모델 추천 조회에 실패했습니다: {result.recommendationError}
                </div>
              ) : null}

              {result.recommendation?.recommendationDetails ? (
                <div className="space-y-2 rounded-md border bg-background p-3">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <p className="text-sm font-semibold">{result.recommendation.recommendationDetails.title}</p>
                    <span className="rounded-full bg-blue-100 px-2 py-1 text-xs font-medium text-blue-700">
                      신뢰도 {result.recommendation.recommendationDetails.confidenceLevel}
                    </span>
                  </div>
                  <p className="text-sm text-muted-foreground">{result.recommendation.recommendationDetails.reasonMessage}</p>
                  <p className="text-sm">
                    예상 절감률:{" "}
                    {typeof result.recommendation.recommendationDetails.estimatedSavingsPct === "number"
                      ? result.recommendation.recommendationDetails.estimatedSavingsPct.toFixed(2)
                      : result.recommendation.recommendationDetails.estimatedSavingsPct}
                    %
                  </p>
                  {(() => {
                    const primaryCandidate = result.recommendation?.recommendationDetails?.candidates?.[0]
                    const estimatedSavingsUsd = primaryCandidate
                      ? estimateSavingsUsd(
                          result.recommendation.recommendationDetails.estimatedSavingsPct,
                          primaryCandidate.expectedMonthlyCostUsd,
                        )
                      : null
                    return estimatedSavingsUsd != null ? (
                      <p className="text-sm font-medium text-emerald-700">예상 절감액(월): ${estimatedSavingsUsd.toFixed(2)}</p>
                    ) : null
                  })()}
                  {result.recommendation.recommendationDetails.disclaimer ? (
                    <p className="text-xs text-amber-700">{result.recommendation.recommendationDetails.disclaimer}</p>
                  ) : null}
                </div>
              ) : null}

              {result.recommendation?.metricsContext ? (
                <div className="space-y-2 rounded-md border border-dashed bg-muted/30 p-2">
                  <p className="text-xs font-medium text-muted-foreground">추천 근거 지표</p>
                  <div className="grid gap-2 md:grid-cols-2">
                    {(() => {
                      const ratio = parseInputOutputRatio(result.recommendation?.metricsContext?.inputOutputRatio)
                      const dominance = ratioDominance(ratio)
                      return (
                        <div className="space-y-1 rounded border bg-background px-2 py-1.5">
                          <div className="flex items-center justify-between">
                            <p className="text-xs font-medium">입출력 비율</p>
                            <span className={`rounded-full px-2 py-0.5 text-[10px] font-medium ${dominance.className}`}>
                              {dominance.label}
                            </span>
                          </div>
                          <p className="text-xs text-muted-foreground">
                            {result.recommendation?.metricsContext?.inputOutputRatio ?? "N/A"}
                          </p>
                          <div className="h-1.5 w-full overflow-hidden rounded bg-muted">
                            <div className="h-full bg-blue-500" style={{ width: `${dominance.inputPct}%` }} />
                          </div>
                          <p className="text-[10px] text-muted-foreground">
                            input {dominance.inputPct.toFixed(0)}% / output {dominance.outputPct.toFixed(0)}%
                          </p>
                        </div>
                      )
                    })()}
                    {(() => {
                      const rawLatency = result.recommendation?.metricsContext?.averageLatencyMs
                      const latency = rawLatency == null ? null : Number(rawLatency)
                      const latencyMeta = latencyStatusNullable(latency)
                      return (
                        <div className="space-y-1 rounded border bg-background px-2 py-1.5">
                          <div className="flex items-center justify-between">
                            <p className="text-xs font-medium">최근 평균 지연</p>
                            <span className={`rounded-full px-2 py-0.5 text-[10px] font-medium ${latencyMeta.className}`}>
                              {latencyMeta.label}
                            </span>
                          </div>
                          <p className="text-xs text-muted-foreground">
                            {latency == null || !Number.isFinite(latency) ? "N/A" : `${latency.toFixed(0)} ms`}
                          </p>
                          <div className="h-1.5 w-full overflow-hidden rounded bg-muted">
                            <div className="h-full bg-amber-500" style={{ width: `${latencyMeta.progress}%` }} />
                          </div>
                        </div>
                      )
                    })()}
                  </div>
                </div>
              ) : null}

              {result.recommendation?.recommendationDetails?.candidates != null &&
              result.recommendation.recommendationDetails.candidates.length > 0 ? (
                <div className="rounded-md border bg-background p-3">
                  <p className="mb-2 text-xs font-medium text-muted-foreground">추천 모델 후보</p>
                  <ul className="space-y-1 text-sm">
                    {result.recommendation.recommendationDetails.candidates.map((candidate) => (
                      <li key={`${result.keyId}-${candidate.modelName}`} className="rounded border px-2 py-1">
                        <p className="font-medium">{candidate.modelName}</p>
                        <p className="text-xs text-muted-foreground">{candidate.keyFeature}</p>
                        <p className="text-xs text-muted-foreground">
                          예상 월 비용 ${Number(candidate.expectedMonthlyCostUsd).toFixed(2)} / 변화율{" "}
                          {typeof candidate.expectedCostDiffPct === "number"
                            ? candidate.expectedCostDiffPct.toFixed(2)
                            : candidate.expectedCostDiffPct}
                          %
                        </p>
                      </li>
                    ))}
                  </ul>
                </div>
              ) : (
                <p className="text-xs text-muted-foreground">추천 결과가 없습니다. 해당 키 옆의 추천을 눌러 주세요.</p>
              )}
            </div>

            <div className="order-1 space-y-2 rounded-md border border-dashed bg-muted/20 p-3">
              <p className="text-xs font-medium text-muted-foreground">예산 분석</p>
              {loadingTarget?.keyId === result.keyId && loadingTarget.action === "ANALYSIS" ? (
                <div className="rounded-md border bg-background p-3 text-xs text-muted-foreground">
                  {loadingMessage || "분석 로딩 중..."}
                </div>
              ) : null}
              {result.data ? (
                <>
                  <div className="grid gap-2 text-sm md:grid-cols-2">
                    <p>예상 소진일: {result.data.predictedRunOutDate}</p>
                    <p>소진까지 남은 일수: {result.data.daysUntilRunOut}일</p>
                    <p>결제일까지 남은 일수: {formatBillingMetric(result.data.daysUntilBillingCycleEnd)}</p>
                    <p>결제일 차이(결제일-소진일): {formatBillingMetric(result.data.billingDateGapDays)}</p>
                    <p>
                      예산 사용률:{" "}
                      {typeof result.data.budgetUtilizationPercent === "number"
                        ? result.data.budgetUtilizationPercent.toFixed(2)
                        : result.data.budgetUtilizationPercent}
                      %
                    </p>
                    <p>신뢰도: {localizedConfidenceLevel(result.data.confidenceLevel)}</p>
                  </div>
                  <p className="text-xs text-muted-foreground">
                    소진까지 남은 일수 = 하루에 얼마나 쓰는지(소모 속도, velocity)로 미래를 예측한 값
                  </p>
                  {result.data.riskCriteria || result.data.confidenceCriteria ? (
                    <div className="rounded-md border border-dashed bg-muted/40 p-3 text-xs text-muted-foreground">
                      {result.data.riskCriteria ? <p>판정 기준: {result.data.riskCriteria}</p> : null}
                      {result.data.confidenceCriteria ? <p>신뢰도 기준: {result.data.confidenceCriteria}</p> : null}
                    </div>
                  ) : null}

                  <div className="rounded-md bg-muted p-3 text-sm">{localizeAssistantMessage(result.data.assistantMessage)}</div>
                  {result.data.anomalySummary ? (
                    <div className="rounded-md border border-orange-200 bg-orange-50 p-3 text-sm text-orange-900 dark:border-orange-900/50 dark:bg-orange-950/40 dark:text-orange-100">
                      <p>이상 탐지: {result.data.anomalySummary}</p>
                      <p className="mt-2 text-xs text-orange-800/90 dark:text-orange-200/90">
                        구체적인 사용 패턴은 대시보드의 <strong>사용량</strong> 또는 <strong>상세 로그</strong> 탭에서 확인해 주세요.
                      </p>
                    </div>
                  ) : null}
                  {result.data.routingRecommendation ? (
                    <div className="rounded-md border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-900">
                      비용 라우팅: {result.data.routingRecommendation}
                      {result.data.estimatedRoutingSavingsPercent != null ? (
                        <span className="ml-1">
                          (예상 절감률{" "}
                          {typeof result.data.estimatedRoutingSavingsPercent === "number"
                            ? result.data.estimatedRoutingSavingsPercent.toFixed(2)
                            : result.data.estimatedRoutingSavingsPercent}
                          %)
                        </span>
                      ) : null}
                    </div>
                  ) : null}

                  <ul className="list-disc space-y-1 pl-5 text-sm">
                    {result.data.recommendedActions.map((action: string) => (
                      <li key={`${result.keyId}-${action}`}>{action}</li>
                    ))}
                  </ul>
                </>
              ) : (
                <p className="text-xs text-muted-foreground">분석 결과가 없습니다. 해당 키 옆의 분석을 눌러 주세요.</p>
              )}
            </div>
          </div>
        </article>
      ))}
    </>
  )
}
