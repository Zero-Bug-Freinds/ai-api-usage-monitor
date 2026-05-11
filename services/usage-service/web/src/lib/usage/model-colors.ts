/**
 * Stable colors per provider+model (same logic as usage-dashboard palette hashing).
 */

const MODEL_COLOR_CACHE = new Map<string, string>()

const PROVIDER_MODEL_PALETTES: Record<string, string[]> = {
  GOOGLE: ["#9a3412", "#c2410c", "#ea580c", "#F97316", "#fb923c", "#fdba74"],
  OPENAI: ["#1e3a8a", "#1d4ed8", "#2563eb", "#3b82f6", "#60a5fa", "#93c5fd"],
  ANTHROPIC: ["#78350f", "#92400e", "#b45309", "#d97706", "#f59e0b", "#fbbf24"],
}

function hashToUint(str: string): number {
  let h = 2166136261
  for (let i = 0; i < str.length; i++) {
    h ^= str.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return h >>> 0
}

export function colorForModel(model: string, provider: string): string {
  const key = `${provider}::${model}`
  const cached = MODEL_COLOR_CACHE.get(key)
  if (cached) return cached
  const list = PROVIDER_MODEL_PALETTES[provider] ?? ["#64748b", "#94a3b8", "#cbd5e1", "#e2e8f0"]
  const idx = hashToUint(key) % list.length
  const picked = list[idx] ?? "#94a3b8"
  MODEL_COLOR_CACHE.set(key, picked)
  return picked
}
