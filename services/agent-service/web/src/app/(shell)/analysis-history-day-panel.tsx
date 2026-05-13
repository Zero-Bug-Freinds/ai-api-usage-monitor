"use client"

import type { AnalysisHistorySnapshot, AnalysisResult } from "./agent-result-shared"
import { AnalysisResultArticles } from "./analysis-result-articles"

function hasBudgetContent(r: AnalysisResult): boolean {
  return Boolean(r.error) || Boolean(r.data) || (r.forecastGaps != null && r.forecastGaps.length > 0)
}

function hasRecommendationContent(r: AnalysisResult): boolean {
  return Boolean(r.recommendationError) || Boolean(r.recommendation?.recommendationDetails)
}

type AnalysisHistoryDayPanelProps = {
  snapshots: AnalysisHistorySnapshot[]
}

export function AnalysisHistoryDayPanel({ snapshots }: AnalysisHistoryDayPanelProps) {
  if (snapshots.length === 0) {
    return <p className="text-xs text-muted-foreground">이 날짜에 저장된 기록이 없습니다.</p>
  }

  return (
    <div className="space-y-8">
      {snapshots.map((snap: AnalysisHistorySnapshot, snapIndex: number) => {
        const budgetRows = snap.results.filter((r: AnalysisResult) => hasBudgetContent(r))
        const recRows = snap.results.filter((r: AnalysisResult) => hasRecommendationContent(r))

        return (
          <div
            key={snap.id}
            className={
              snapIndex > 0 ? "space-y-4 border-t border-dashed border-border pt-6" : "space-y-4"
            }
          >
            {snapshots.length > 1 ? (
              <p className="text-[10px] text-muted-foreground">
                {snapIndex + 1}번째 저장 ·{" "}
                {new Date(snap.savedAt).toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit", second: "2-digit" })}
              </p>
            ) : null}

            <div className="space-y-3">
              <h3 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">예산 분석</h3>
              {budgetRows.length === 0 ? (
                <p className="text-[11px] text-muted-foreground">이 시점에 저장된 예산 분석이 없습니다.</p>
              ) : (
                <div className="space-y-3">
                  {budgetRows.map((r: AnalysisResult) => (
                    <AnalysisResultArticles
                      key={`${snap.id}-b-${r.keyId}`}
                      results={[r]}
                      loadingTarget={null}
                      loadingMessage=""
                      emptyLabel={null}
                      sections={["budget"]}
                    />
                  ))}
                </div>
              )}
            </div>

            <div className="space-y-3">
              <h3 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">모델 추천</h3>
              {recRows.length === 0 ? (
                <p className="text-[11px] text-muted-foreground">이 시점에 저장된 모델 추천이 없습니다.</p>
              ) : (
                <div className="space-y-3">
                  {recRows.map((r: AnalysisResult) => (
                    <AnalysisResultArticles
                      key={`${snap.id}-r-${r.keyId}`}
                      results={[r]}
                      loadingTarget={null}
                      loadingMessage=""
                      emptyLabel={null}
                      sections={["recommendation"]}
                    />
                  ))}
                </div>
              )}
            </div>
          </div>
        )
      })}
    </div>
  )
}
