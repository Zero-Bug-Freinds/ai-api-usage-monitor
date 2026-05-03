export function formatIsoDate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

export function rangeLastDays(days: number): { from: string; to: string } {
  const to = new Date();
  const from = new Date(to);
  from.setDate(from.getDate() - (days - 1));
  return { from: formatIsoDate(from), to: formatIsoDate(to) };
}

function formatKstYmd(instant: Date): string {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(instant);
  const y = parts.find((p) => p.type === "year")?.value ?? "1970";
  const m = parts.find((p) => p.type === "month")?.value ?? "01";
  const d = parts.find((p) => p.type === "day")?.value ?? "01";
  return `${y}-${m}-${d}`;
}

/** First day of current calendar month in Asia/Seoul (YYYY-MM-01). */
export function currentMonthStartKst(): string {
  const ymd = formatKstYmd(new Date());
  return `${ymd.slice(0, 7)}-01`;
}

/**
 * Inclusive date range for "this month" in Asia/Seoul, aligned with billing {@code agg_date} (KST calendar days).
 */
export function currentMonthRangeKst(): { from: string; to: string } {
  return { from: currentMonthStartKst(), to: formatKstYmd(new Date()) };
}
