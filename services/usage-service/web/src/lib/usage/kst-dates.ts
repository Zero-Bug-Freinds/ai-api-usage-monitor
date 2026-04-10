/**
 * Dashboard `from`/`to` query strings must match usage-service:
 * {@code UsageDashboardService} uses {@code Asia/Seoul} midnight boundaries.
 */
const KST_OFFSET = "+09:00"

function pad2(n: number): string {
  return String(n).padStart(2, "0")
}

/** Calendar date in Korea Standard Time (YYYY-MM-DD) for the given instant. */
export function formatKstIsoDate(d: Date = new Date()): string {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(d)
  const y = parts.find((p) => p.type === "year")?.value
  const m = parts.find((p) => p.type === "month")?.value
  const day = parts.find((p) => p.type === "day")?.value
  if (!y || !m || !day) {
    throw new Error("formatKstIsoDate: unexpected DateTimeFormat parts")
  }
  return `${y}-${m}-${day}`
}

/** Add calendar days in KST (no DST; fixed +09:00). */
export function addKstDays(isoDate: string, deltaDays: number): string {
  const [y, m, day] = isoDate.split("-").map((n) => parseInt(n, 10))
  if (Number.isNaN(y) || Number.isNaN(m) || Number.isNaN(day)) {
    throw new Error(`addKstDays: invalid date "${isoDate}"`)
  }
  const atKstMidnight = `${y}-${pad2(m)}-${pad2(day)}T00:00:00${KST_OFFSET}`
  const ms = Date.parse(atKstMidnight) + deltaDays * 86_400_000
  return formatKstIsoDate(new Date(ms))
}
