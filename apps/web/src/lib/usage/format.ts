const usdFormatter = new Intl.NumberFormat("en-US", {
  style: "currency",
  currency: "USD",
  minimumFractionDigits: 2,
  maximumFractionDigits: 4,
})

export function toNumber(value: number | string | null | undefined): number {
  if (value == null) return 0
  if (typeof value === "number") return Number.isFinite(value) ? value : 0
  const n = Number(value)
  return Number.isFinite(n) ? n : 0
}

export function formatUsd(amount: number | string | null | undefined): string {
  return usdFormatter.format(toNumber(amount))
}

export function formatTokenCount(n: number): string {
  return `${n.toLocaleString("en-US")} tokens`
}

export function formatRequestCount(n: number): string {
  return `${n.toLocaleString("en-US")} requests`
}
