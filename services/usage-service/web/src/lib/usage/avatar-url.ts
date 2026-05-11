/**
 * Deterministic string → hex seed for DiceBear (no raw userId in URL).
 */
function deterministicSeedHex(input: string): string {
  let h1 = 2166136261
  let h2 = 5381
  for (let i = 0; i < input.length; i++) {
    const c = input.charCodeAt(i)
    h1 ^= c
    h1 = Math.imul(h1, 16777619) >>> 0
    h2 = (Math.imul(h2, 33) + c) >>> 0
  }
  return [h1, h2].map((n) => n.toString(16).padStart(8, "0")).join("")
}

export type AvatarConfig = {
  style?: string
  /** CSS color or DiceBear color token; default transparent */
  backgroundColor?: string
}

const DEFAULT_STYLE = "dylan"
const DICEBEAR_BASE = "https://api.dicebear.com/7.x"

/**
 * Returns a stable DiceBear SVG URL for the given seed (typically {@code userId}).
 * Seed is hashed client-side before being sent as the {@code seed} query param.
 */
export function generateAvatarUrl(seed: string, config?: AvatarConfig): string {
  const style = config?.style?.trim() || DEFAULT_STYLE
  const bg = config?.backgroundColor?.trim() || "transparent"
  const hashed = deterministicSeedHex(seed.trim() || "anonymous")
  const params = new URLSearchParams({ seed: hashed, backgroundColor: bg })
  return `${DICEBEAR_BASE}/${encodeURIComponent(style)}/svg?${params.toString()}`
}
