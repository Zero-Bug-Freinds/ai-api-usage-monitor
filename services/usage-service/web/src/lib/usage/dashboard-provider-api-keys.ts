import type { UsageLogApiKeyItemResponse, UsageProviderFilter } from "@/lib/usage/types"
import { DASHBOARD_API_KEY_ALL, DASHBOARD_API_KEY_NONE } from "@/lib/usage/dashboard-api-key-constants"
import { teamBffKeyToUsageOption } from "@/lib/usage/api-key-options"

/** 개인·팀 대시보드 공급사 필터 — 로그 탭 `__all__` 과 구분됨. */
export const DASHBOARD_PROVIDER_ALL = "__ALL__"

export type TeamBffApiKeyRow = {
  id: string
  alias: string
  provider: string
  updatedAt: string
  status?: string
}

export function dashboardProviderQueryParam(provider: string): UsageProviderFilter | undefined {
  if (provider === DASHBOARD_PROVIDER_ALL) return undefined
  return provider as UsageProviderFilter
}

/** 팀 BFF `GET /teams/{id}/api-keys` 본문 파싱 (공급사 필터는 호출부에서 적용). */
export function parseTeamBffApiKeysPayload(body: unknown): TeamBffApiKeyRow[] {
  if (!body || typeof body !== "object") return []
  const json = body as { apiKeys?: unknown }
  if (!Array.isArray(json.apiKeys)) return []
  const rows = (json.apiKeys as unknown[])
    .map((item): TeamBffApiKeyRow | null => {
      if (!item || typeof item !== "object") return null
      const o = item as Record<string, unknown>
      if ((typeof o.id !== "number" && typeof o.id !== "string") || typeof o.alias !== "string") return null
      if (typeof o.provider !== "string") return null
      const updatedAt = typeof o.updatedAt === "string" ? o.updatedAt : ""
      const status = typeof o.status === "string" ? o.status : undefined
      return { id: String(o.id), alias: o.alias, provider: o.provider, updatedAt, status }
    })
    .filter((x): x is TeamBffApiKeyRow => x !== null)
  rows.sort((a, b) => a.updatedAt.localeCompare(b.updatedAt))
  return rows
}

export function filterTeamBffRowsByProvider(rows: TeamBffApiKeyRow[], selectedProvider: string): TeamBffApiKeyRow[] {
  if (selectedProvider === DASHBOARD_PROVIDER_ALL) return rows
  const p = selectedProvider.toUpperCase()
  return rows.filter((r) => r.provider.toUpperCase() === p)
}

export function teamBffRowsToUsageMenuItems(rows: TeamBffApiKeyRow[]): UsageLogApiKeyItemResponse[] {
  return rows.map((row) => teamBffKeyToUsageOption(row))
}

/**
 * 팀별 나의 사용량과 동일: 선택 공급사 기준으로 필터된 키 목록에 맞춰 집계용 API Key 선택값을 맞춘다.
 * 키가 없으면 없음(NONE), 생기면 전체(ALL)로 복귀 가능.
 */
export function resolveDashboardAggregateApiKeyId(
  previous: string,
  filteredMenuKeys: UsageLogApiKeyItemResponse[],
): string {
  if (filteredMenuKeys.length === 0) return DASHBOARD_API_KEY_NONE
  if (previous === DASHBOARD_API_KEY_NONE) return DASHBOARD_API_KEY_ALL
  if (previous === DASHBOARD_API_KEY_ALL) return DASHBOARD_API_KEY_ALL
  if (filteredMenuKeys.some((x) => x.apiKeyId === previous)) return previous
  return DASHBOARD_API_KEY_ALL
}
