import { addKstDays, formatKstIsoDate } from "@/lib/usage/kst-dates"

export type PeriodMode = "today" | "7d" | "30d" | "custom"

export type StoredDashboardPeriod = {
  mode: PeriodMode
  from: string
  to: string
}

export function isPeriodMode(v: unknown): v is PeriodMode {
  return v === "today" || v === "7d" || v === "30d" || v === "custom"
}

export function presetRangeForMode(mode: PeriodMode, todayKst: string): { from: string; to: string } {
  switch (mode) {
    case "today":
      return { from: todayKst, to: todayKst }
    case "7d":
      return { from: addKstDays(todayKst, -6), to: todayKst }
    case "30d":
      return { from: addKstDays(todayKst, -29), to: todayKst }
    default:
      return { from: todayKst, to: todayKst }
  }
}

export function defaultPeriod(todayIso?: string): StoredDashboardPeriod {
  const t = todayIso ?? formatKstIsoDate()
  return { mode: "today", from: t, to: t }
}

export function parseStoredPeriod(raw: string | null, todayKst: string): StoredDashboardPeriod {
  if (!raw) return defaultPeriod(todayKst)
  try {
    const parsed = JSON.parse(raw) as Partial<StoredDashboardPeriod>
    const mode = isPeriodMode(parsed.mode) ? parsed.mode : "today"
    const from = typeof parsed.from === "string" && parsed.from.length > 0 ? parsed.from : todayKst
    const to = typeof parsed.to === "string" && parsed.to.length > 0 ? parsed.to : todayKst
    return { mode, from, to }
  } catch {
    return defaultPeriod(todayKst)
  }
}
