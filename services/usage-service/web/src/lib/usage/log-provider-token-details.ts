/** Keys rendered by OpenAI dedicated UI — excluded from common Key/Value section. */
export const OPENAI_DETAIL_KEYS_IN_DEDICATED_UI = new Set([
  "prompt_cached_tokens",
  "prompt_audio_tokens",
  "completion_reasoning_tokens",
  "completion_audio_tokens",
  "completion_accepted_prediction_tokens",
  "completion_rejected_prediction_tokens",
])

export type ProviderTokenDetailEntry = {
  key: string
  label: string
  displayValue: string
  masked: boolean
}

const MASK_KEY_SUBSTRINGS = [
  "secret",
  "apikey",
  "api_key",
  "authorization",
  "credential",
  "rawrequestbody",
  "raw_request_body",
] as const

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value)
}

function isEmptyString(value: unknown): boolean {
  return typeof value === "string" && value.trim() === ""
}

/** null, undefined, "", {} are invalid; 0 and false are valid. */
export function isValidDetailValue(value: unknown): boolean {
  if (value === null || value === undefined) return false
  if (isEmptyString(value)) return false
  if (isPlainObject(value)) {
    return Object.keys(value).length > 0 && Object.values(value).some(isValidDetailValue)
  }
  if (Array.isArray(value)) {
    return value.length > 0 && value.some(isValidDetailValue)
  }
  return true
}

export function hasValidProviderTokenDetails(
  details: Record<string, unknown> | null | undefined
): boolean {
  if (details == null) return false
  if (!isPlainObject(details)) return false
  return Object.keys(details).length > 0 && Object.values(details).some(isValidDetailValue)
}

export function formatDetailLabel(key: string): string {
  const leaf = key.split(".").pop() ?? key
  const words = leaf
    .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
    .replace(/_/g, " ")
    .trim()
    .split(/\s+/)
  if (words.length === 0) return key
  return words.map((w) => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase()).join(" ")
}

export function shouldMaskDetailKey(key: string): boolean {
  const leaf = (key.split(".").pop() ?? key).toLowerCase()
  if (leaf.endsWith("tokens") || leaf.endsWith("_tokens")) return false
  if (
    /^(prompt|completion|cached|total|estimated|reasoning|audio|prediction)(_)?tokens?$/.test(leaf)
  ) {
    return false
  }
  if (MASK_KEY_SUBSTRINGS.some((s) => leaf.includes(s))) return true
  if (leaf === "token" || (leaf.endsWith("_token") && !leaf.endsWith("_tokens"))) return true
  return false
}

function formatDisplayValue(value: unknown, masked: boolean): string {
  if (masked) return "******"
  if (value === null || value === undefined) return "—"
  if (typeof value === "boolean") return value ? "true" : "false"
  if (typeof value === "number" && Number.isFinite(value)) {
    return value.toLocaleString("en-US")
  }
  if (typeof value === "string") return value
  try {
    return JSON.stringify(value)
  } catch {
    return String(value)
  }
}

export type CollectCommonDetailEntriesOptions = {
  excludeKeys?: Set<string>
  maxDepth?: number
}

export function collectCommonDetailEntries(
  details: Record<string, unknown>,
  options: CollectCommonDetailEntriesOptions = {}
): ProviderTokenDetailEntry[] {
  const excludeKeys = options.excludeKeys ?? new Set<string>()
  const maxDepth = options.maxDepth ?? 2
  const raw: ProviderTokenDetailEntry[] = []

  const walk = (obj: Record<string, unknown>, prefix: string, depth: number) => {
    for (const [key, value] of Object.entries(obj)) {
      if (excludeKeys.has(key)) continue
      const path = prefix ? `${prefix}.${key}` : key
      const masked = shouldMaskDetailKey(path)

      if (!isValidDetailValue(value)) continue

      const atMaxDepth = depth >= maxDepth
      if (atMaxDepth || (!isPlainObject(value) && !Array.isArray(value))) {
        raw.push({
          key: path,
          label: formatDetailLabel(path),
          displayValue: formatDisplayValue(value, masked),
          masked,
        })
        continue
      }

      if (Array.isArray(value)) {
        raw.push({
          key: path,
          label: formatDetailLabel(path),
          displayValue: formatDisplayValue(value, masked),
          masked,
        })
        continue
      }

      if (isPlainObject(value)) {
        walk(value, path, depth + 1)
      }
    }
  }

  walk(details, "", 0)
  return raw.sort((a, b) => a.label.localeCompare(b.label, "en"))
}

export function openAiDedicatedDetailsSum(row: {
  promptCachedTokens?: number | null
  promptAudioTokens?: number | null
  completionReasoningTokens?: number | null
  completionAudioTokens?: number | null
  completionAcceptedPredictionTokens?: number | null
  completionRejectedPredictionTokens?: number | null
}): number {
  return (
    toLongOrZero(row.promptCachedTokens) +
    toLongOrZero(row.promptAudioTokens) +
    toLongOrZero(row.completionReasoningTokens) +
    toLongOrZero(row.completionAudioTokens) +
    toLongOrZero(row.completionAcceptedPredictionTokens) +
    toLongOrZero(row.completionRejectedPredictionTokens)
  )
}

function toLongOrZero(v: number | null | undefined): number {
  return typeof v === "number" && Number.isFinite(v) ? v : 0
}
