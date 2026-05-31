export const PROVIDER_COLOR: Record<string, string> = {
  GOOGLE: "#F97316",
  OPENAI: "#2563eb",
  ANTHROPIC: "#c2410c",
}

const PROVIDER_LABEL: Record<string, string> = {
  GOOGLE: "Gemini (Google)",
  OPENAI: "OpenAI",
  ANTHROPIC: "Anthropic",
}

export type ProviderShareInput = {
  provider: string
  requestCount: number
}

export type ProviderShareDatum = {
  name: string
  provider: string
  value: number
  percent: number
}

export function labelForProviderCode(code: string): string {
  return PROVIDER_LABEL[code] ?? code
}

export function aggregateProviderRequestShare(byModel: ProviderShareInput[]): ProviderShareDatum[] {
  const acc = new Map<string, number>()
  for (const row of byModel) {
    if (row.requestCount <= 0) continue
    acc.set(row.provider, (acc.get(row.provider) ?? 0) + row.requestCount)
  }
  const total = [...acc.values()].reduce((sum, value) => sum + value, 0)
  if (total <= 0) return []
  return [...acc.entries()].map(([provider, value]) => ({
    name: labelForProviderCode(provider),
    provider,
    value,
    percent: value / total,
  }))
}
