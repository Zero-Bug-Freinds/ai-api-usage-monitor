"use client"

import type { UsageLogEntryResponse } from "@/lib/usage/types"

function toLongOrZero(v: number | null | undefined): number {
  return typeof v === "number" && Number.isFinite(v) ? v : 0
}

export type OpenAiTokenDetailsSectionProps = {
  row: UsageLogEntryResponse
}

export function OpenAiTokenDetailsSection({ row }: OpenAiTokenDetailsSectionProps) {
  const cached = toLongOrZero(row.promptCachedTokens)
  const promptAudio = toLongOrZero(row.promptAudioTokens)
  const reasoning = toLongOrZero(row.completionReasoningTokens)
  const completionAudio = toLongOrZero(row.completionAudioTokens)
  const accepted = toLongOrZero(row.completionAcceptedPredictionTokens)
  const rejected = toLongOrZero(row.completionRejectedPredictionTokens)

  const bothPredZero = accepted === 0 && rejected === 0
  const predSum = accepted + rejected

  return (
    <div className="mt-4 space-y-5">
      <section className="space-y-3">
        <h3 className="text-sm font-semibold">Prompt Details</h3>
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="rounded-md border border-border/70 p-3">
            <p className="text-xs text-muted-foreground">Cached Tokens</p>
            <p className="mt-1 tabular-nums text-sm font-semibold">{cached.toLocaleString("en-US")}</p>
          </div>
          <div className="rounded-md border border-border/70 p-3">
            <p className="text-xs text-muted-foreground">Audio Tokens</p>
            <p className="mt-1 tabular-nums text-sm font-semibold">{promptAudio.toLocaleString("en-US")}</p>
          </div>
        </div>
      </section>

      <section className="space-y-3">
        <h3 className="text-sm font-semibold">Completion Details</h3>
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="rounded-md border border-border/70 p-3">
            <p className="text-xs text-muted-foreground">Reasoning Tokens</p>
            <p className="mt-1 tabular-nums text-sm font-semibold">{reasoning.toLocaleString("en-US")}</p>
          </div>
          <div className="rounded-md border border-border/70 p-3">
            <p className="text-xs text-muted-foreground">Audio Tokens</p>
            <p className="mt-1 tabular-nums text-sm font-semibold">{completionAudio.toLocaleString("en-US")}</p>
          </div>
        </div>

        <div className="rounded-md border border-border/70 p-3">
          <div className="flex items-start justify-between gap-3">
            <div>
              <p className="text-xs text-muted-foreground">Prediction Tokens</p>
              <p className="mt-1 text-sm font-semibold tabular-nums">
                Accepted {accepted.toLocaleString("en-US")} / Rejected {rejected.toLocaleString("en-US")}
              </p>
            </div>
          </div>

          <div className="mt-3">
            {bothPredZero ? (
              <p className="text-xs text-muted-foreground">두 값 모두 0</p>
            ) : (
              <>
                <div
                  className="flex h-2 overflow-hidden rounded bg-muted/30"
                  aria-label="Accepted/Rejected prediction mini graph"
                >
                  <div className="h-full bg-emerald-500" style={{ flexGrow: accepted }} aria-hidden="true" />
                  <div className="h-full bg-rose-500" style={{ flexGrow: rejected }} aria-hidden="true" />
                </div>
                <p className="mt-2 text-[11px] text-muted-foreground tabular-nums">
                  Accepted {Math.round((accepted / predSum) * 100)}% · Rejected{" "}
                  {Math.round((rejected / predSum) * 100)}%
                </p>
              </>
            )}
          </div>
        </div>
      </section>
    </div>
  )
}
