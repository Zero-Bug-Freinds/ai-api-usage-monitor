import { describe, expect, it } from "vitest"
import {
  collectCommonDetailEntries,
  formatDetailLabel,
  hasValidProviderTokenDetails,
  OPENAI_DETAIL_KEYS_IN_DEDICATED_UI,
  shouldMaskDetailKey,
} from "@/lib/usage/log-provider-token-details"

describe("hasValidProviderTokenDetails", () => {
  it("treats 0 and false as valid", () => {
    expect(hasValidProviderTokenDetails({ count: 0 })).toBe(true)
    expect(hasValidProviderTokenDetails({ ok: false })).toBe(true)
  })

  it("rejects null, empty string, and empty object", () => {
    expect(hasValidProviderTokenDetails(null)).toBe(false)
    expect(hasValidProviderTokenDetails(undefined)).toBe(false)
    expect(hasValidProviderTokenDetails({})).toBe(false)
    expect(hasValidProviderTokenDetails({ note: "" })).toBe(false)
  })

  it("accepts nested valid values", () => {
    expect(hasValidProviderTokenDetails({ meta: { cached: 0 } })).toBe(true)
  })
})

describe("shouldMaskDetailKey", () => {
  it("does not mask token count fields", () => {
    expect(shouldMaskDetailKey("prompt_cached_tokens")).toBe(false)
    expect(shouldMaskDetailKey("completionTokens")).toBe(false)
    expect(shouldMaskDetailKey("cachedTokens")).toBe(false)
  })

  it("masks sensitive key patterns", () => {
    expect(shouldMaskDetailKey("apiKey")).toBe(true)
    expect(shouldMaskDetailKey("raw_request_body")).toBe(true)
    expect(shouldMaskDetailKey("authorization")).toBe(true)
  })
})

describe("collectCommonDetailEntries", () => {
  it("sorts alphabetically by label and excludes OpenAI dedicated keys", () => {
    const entries = collectCommonDetailEntries(
      {
        zebra_metric: 1,
        alpha_metric: 2,
        prompt_cached_tokens: 99,
      },
      { excludeKeys: OPENAI_DETAIL_KEYS_IN_DEDICATED_UI }
    )
    expect(entries.map((e) => e.key)).toEqual(["alpha_metric", "zebra_metric"])
  })

  it("masks sensitive values in display", () => {
    const entries = collectCommonDetailEntries({ api_key: "sk-live" })
    expect(entries[0]?.displayValue).toBe("******")
    expect(entries[0]?.masked).toBe(true)
  })

  it("renders nested object within max depth", () => {
    const entries = collectCommonDetailEntries({
      outer: { inner_count: 3 },
    })
    expect(entries.some((e) => e.key === "outer.inner_count")).toBe(true)
  })
})

describe("formatDetailLabel", () => {
  it("formats snake_case keys", () => {
    expect(formatDetailLabel("completion_reasoning_tokens")).toBe("Completion Reasoning Tokens")
  })
})
