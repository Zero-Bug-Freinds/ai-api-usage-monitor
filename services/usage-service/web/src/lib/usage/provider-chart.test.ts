import { describe, expect, it } from "vitest"
import { aggregateProviderRequestShare } from "@/lib/usage/provider-chart"

describe("aggregateProviderRequestShare", () => {
  it("aggregates request counts by provider", () => {
    const result = aggregateProviderRequestShare([
      { provider: "OPENAI", requestCount: 30 },
      { provider: "GOOGLE", requestCount: 20 },
      { provider: "OPENAI", requestCount: 10 },
    ])

    expect(result).toEqual([
      { name: "OpenAI", provider: "OPENAI", value: 40, percent: 40 / 60 },
      { name: "Gemini (Google)", provider: "GOOGLE", value: 20, percent: 20 / 60 },
    ])
  })

  it("excludes non-positive request rows", () => {
    const result = aggregateProviderRequestShare([
      { provider: "OPENAI", requestCount: 0 },
      { provider: "GOOGLE", requestCount: -4 },
      { provider: "ANTHROPIC", requestCount: 12 },
    ])

    expect(result).toEqual([
      { name: "Anthropic", provider: "ANTHROPIC", value: 12, percent: 1 },
    ])
  })

  it("returns 100 percent for single provider", () => {
    const result = aggregateProviderRequestShare([
      { provider: "GOOGLE", requestCount: 10 },
      { provider: "GOOGLE", requestCount: 15 },
    ])

    expect(result).toEqual([
      { name: "Gemini (Google)", provider: "GOOGLE", value: 25, percent: 1 },
    ])
  })
})
