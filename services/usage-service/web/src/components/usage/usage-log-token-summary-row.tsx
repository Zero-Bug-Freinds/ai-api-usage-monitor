"use client"

import type { ReactNode } from "react"
import { CircleHelp } from "lucide-react"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@ai-usage/ui"
import type { UsageLogEntryResponse } from "@/lib/usage/types"

function reasoningTokensTooltipContent() {
  return (
    <div className="space-y-1">
      <p className="font-medium text-foreground">추론 토큰 산출</p>
      <p>Google / OpenAI: 모델이 직접 응답 전문에 포함하여 제공한 실제 추론 수치입니다.</p>
      <p>Anthropic: 현재 사용 기록이 없어 추론 토큰 상세값이 없는 경우가 있습니다.</p>
      <p>공통: 모델의 사고 과정(Reasoning) 및 시스템 처리 비용을 포함합니다.</p>
    </div>
  )
}

function outputTokensTooltipContent() {
  return (
    <div className="space-y-1">
      <p className="font-medium text-foreground">출력 토큰 산출</p>
      <p>출력 토큰에서는 추론 토큰을 제외한 순수 응답량만 표시합니다.</p>
    </div>
  )
}

function TokenLabelWithHelp({
  label,
  helpAriaLabel,
  tooltip,
}: {
  label: string
  helpAriaLabel: string
  tooltip: ReactNode
}) {
  return (
    <span className="inline-flex items-center justify-center gap-1">
      {label}
      <TooltipProvider>
        <Tooltip>
          <TooltipTrigger asChild>
            <button
              type="button"
              className="inline-flex h-4 w-4 items-center justify-center rounded-full border border-muted-foreground/40 text-[10px] text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/60"
              aria-label={helpAriaLabel}
            >
              <CircleHelp className="h-3 w-3" />
            </button>
          </TooltipTrigger>
          <TooltipContent side="top" align="center">
            {tooltip}
          </TooltipContent>
        </Tooltip>
      </TooltipProvider>
    </span>
  )
}

export type UsageLogTokenSummaryRowProps = {
  row: UsageLogEntryResponse
}

export function UsageLogTokenSummaryRow({ row }: UsageLogTokenSummaryRowProps) {
  const ert = row.estimatedReasoningTokens
  const reasoningDisplay = !row.requestSuccessful
    ? "-"
    : typeof ert === "number" && Number.isFinite(ert)
      ? String(ert)
      : "0"

  return (
    <section className="mt-4 grid grid-cols-3 gap-3 rounded-lg border border-border/70 bg-muted/20 p-3">
      <div className="text-center">
        <p className="text-xs text-muted-foreground">입력 토큰</p>
        <p className="mt-1 text-sm font-semibold tabular-nums">{row.promptTokens ?? "—"}</p>
      </div>
      <div className="text-center">
        <p className="text-xs text-muted-foreground">
          <TokenLabelWithHelp
            label="추론 토큰"
            helpAriaLabel="추론 토큰 설명"
            tooltip={reasoningTokensTooltipContent()}
          />
        </p>
        <p className="mt-1 text-sm font-semibold tabular-nums">{reasoningDisplay}</p>
      </div>
      <div className="text-center">
        <p className="text-xs text-muted-foreground">
          <TokenLabelWithHelp
            label="출력 토큰"
            helpAriaLabel="출력 토큰 설명"
            tooltip={outputTokensTooltipContent()}
          />
        </p>
        <p className="mt-1 text-sm font-semibold tabular-nums">{row.completionTokens ?? "—"}</p>
      </div>
    </section>
  )
}
