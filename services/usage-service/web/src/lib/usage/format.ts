const usdFormatter = new Intl.NumberFormat("en-US", {
  style: "currency",
  currency: "USD",
  minimumFractionDigits: 2,
  maximumFractionDigits: 4,
})

/** Positive amounts below this round to $0.00 at 2 decimal places; show a floor hint instead. */
const USD_SUB_MICRO_POSITIVE = 0.000001

const usdExtendedSmallFormatter = new Intl.NumberFormat("en-US", {
  style: "currency",
  currency: "USD",
  minimumFractionDigits: 2,
  maximumFractionDigits: 10,
})

const usdMicroFloorFormatter = new Intl.NumberFormat("en-US", {
  style: "currency",
  currency: "USD",
  minimumFractionDigits: 6,
  maximumFractionDigits: 6,
})

export function toNumber(value: number | string | null | undefined): number {
  if (value == null) return 0
  if (typeof value === "number") return Number.isFinite(value) ? value : 0
  const n = Number(value)
  return Number.isFinite(n) ? n : 0
}

/**
 * USD display for dashboard KPI and usage log cells.
 * - Sums and “normal” amounts use up to 4 fraction digits.
 * - Very small positive costs (below one microdollar) show as "< $0.000001" so they are not mistaken for zero.
 * - Between $0.000001 and $0.01, uses up to 10 fraction digits so values like 0.00003 stay visible.
 */
export function formatUsd(amount: number | string | null | undefined): string {
  const n = toNumber(amount)
  if (!Number.isFinite(n) || n === 0) {
    return usdFormatter.format(0)
  }
  if (n < 0) {
    return usdExtendedSmallFormatter.format(n)
  }
  if (n > 0 && n < USD_SUB_MICRO_POSITIVE) {
    return `< ${usdMicroFloorFormatter.format(USD_SUB_MICRO_POSITIVE)}`
  }
  if (n < 0.01) {
    return usdExtendedSmallFormatter.format(n)
  }
  return usdFormatter.format(n)
}

export function formatTokenCount(n: number): string {
  return `${n.toLocaleString("en-US")} tokens`
}

export function formatRequestCount(n: number): string {
  return `${n.toLocaleString("en-US")} requests`
}
