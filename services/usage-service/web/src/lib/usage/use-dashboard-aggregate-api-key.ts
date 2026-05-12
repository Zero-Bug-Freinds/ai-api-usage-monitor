"use client"

import * as React from "react"
import type { UsageLogApiKeyItemResponse } from "@/lib/usage/types"
import { resolveDashboardAggregateApiKeyId } from "@/lib/usage/dashboard-provider-api-keys"

/**
 * 공급사·팀(또는 개인) 컨텍스트에 따라 필터된 API Key 목록이 바뀔 때 집계 키 선택값을 동기화한다.
 * PERSONAL / TEAM_MEMBER_ONLY 대시보드 및 팀 집계 화면에서 공통 사용.
 */
export function useDashboardAggregateApiKeySync(
  filteredMenuKeys: UsageLogApiKeyItemResponse[],
  apiKeyId: string,
  setApiKeyId: React.Dispatch<React.SetStateAction<string>>,
  enabled: boolean,
): void {
  React.useEffect(() => {
    if (!enabled) return
    const next = resolveDashboardAggregateApiKeyId(apiKeyId, filteredMenuKeys)
    if (next !== apiKeyId) setApiKeyId(next)
  }, [enabled, filteredMenuKeys, apiKeyId, setApiKeyId])
}
