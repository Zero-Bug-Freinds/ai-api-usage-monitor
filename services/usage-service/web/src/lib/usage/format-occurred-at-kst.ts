/** 사용 로그 `occurredAt`(ISO 등)을 한국 표준시로 표시한다. 파싱 실패 시 원문을 그대로 둔다. */
const kstFormatter = new Intl.DateTimeFormat("ko-KR", {
  timeZone: "Asia/Seoul",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
  hour12: false,
})

export function formatOccurredAtKst(isoOrRaw: string): string {
  const d = new Date(isoOrRaw)
  if (Number.isNaN(d.getTime())) return isoOrRaw
  return kstFormatter.format(d)
}
