import type { UsageLogApiKeyItemResponse } from "@/lib/usage/types"

export function isDeletedApiKeyItem(item: UsageLogApiKeyItemResponse): boolean {
  return item.status === "DELETED"
}

/** UsageLog / dashboard dropdown label; 삭제 키는 별칭 우측에 (삭제). */
export function usageApiKeyOptionLabel(item: UsageLogApiKeyItemResponse): string {
  const alias = item.alias?.trim()
  if (!alias) return "별칭 없음"
  return item.status === "DELETED" ? `${alias} (삭제)` : alias
}

export function partitionUsageApiKeys(items: UsageLogApiKeyItemResponse[]): {
  active: UsageLogApiKeyItemResponse[]
  deleted: UsageLogApiKeyItemResponse[]
} {
  const active = items.filter((x) => !isDeletedApiKeyItem(x))
  const deleted = items.filter((x) => isDeletedApiKeyItem(x))
  return { active, deleted }
}

/** BFF `GET /teams/{id}/api-keys` row shape → usage log option (status for 삭제 섹션). */
export function teamBffKeyToUsageOption(row: { id: string; alias: string; status?: string }): UsageLogApiKeyItemResponse {
  const st = row.status
  const status: UsageLogApiKeyItemResponse["status"] =
    st === "DELETED" || st === "DELETION_REQUESTED" || st === "ACTIVE" ? st : "ACTIVE"
  return { apiKeyId: row.id, alias: row.alias, status }
}
