"use client"

import {
  collectCommonDetailEntries,
  OPENAI_DETAIL_KEYS_IN_DEDICATED_UI,
} from "@/lib/usage/log-provider-token-details"

export type ProviderTokenDetailsCommonSectionProps = {
  providerTokenDetails: Record<string, unknown>
  excludeOpenAiDedicatedKeys?: boolean
}

export function ProviderTokenDetailsCommonSection({
  providerTokenDetails,
  excludeOpenAiDedicatedKeys = false,
}: ProviderTokenDetailsCommonSectionProps) {
  const entries = collectCommonDetailEntries(providerTokenDetails, {
    excludeKeys: excludeOpenAiDedicatedKeys ? OPENAI_DETAIL_KEYS_IN_DEDICATED_UI : undefined,
    maxDepth: 2,
  })

  if (entries.length === 0) return null

  return (
    <section className="mt-6 space-y-3 border-t border-border pt-4">
      <h3 className="text-sm font-semibold">추가 상세</h3>
      <dl className="space-y-2">
        {entries.map((entry) => (
          <div
            key={entry.key}
            className="grid grid-cols-[minmax(0,1fr)_auto] gap-3 rounded-md border border-border/70 px-3 py-2 text-xs"
          >
            <dt className="font-medium text-muted-foreground">{entry.label}</dt>
            <dd className="tabular-nums text-right text-foreground">{entry.displayValue}</dd>
          </div>
        ))}
      </dl>
    </section>
  )
}
