function trimBaseUrl(url: string | undefined): string | null {
  const normalized = (url ?? "").trim()
  if (!normalized) return null
  return normalized.replace(/\/+$/, "")
}

export function resolveGatewayBaseUrl(): string | null {
  return trimBaseUrl(process.env.GATEWAY_URL) ?? trimBaseUrl(process.env.WEB_GATEWAY_URL)
}
