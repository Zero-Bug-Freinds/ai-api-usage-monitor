/**
 * Usage 백엔드는 `from`/`to` LocalDate 구간을 UTC 자정 기준 Instant로 변환한다.
 * 브라우저에서 동일한 일자 문자열을 맞추려면 UTC 달력 기준 `YYYY-MM-DD`를 쓴다.
 */
export function formatUtcIsoDate(d: Date = new Date()): string {
  return d.toISOString().slice(0, 10)
}

export function addUtcDays(isoDate: string, deltaDays: number): string {
  const [y, m, day] = isoDate.split("-").map((n) => parseInt(n, 10))
  const t = Date.UTC(y, m - 1, day) + deltaDays * 86_400_000
  return new Date(t).toISOString().slice(0, 10)
}
