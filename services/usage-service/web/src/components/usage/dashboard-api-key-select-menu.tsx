"use client"

import {
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectSeparator,
} from "@ai-usage/ui"
import type { UsageLogApiKeyItemResponse } from "@/lib/usage/types"
import {
  partitionUsageApiKeys,
  usageApiKeyOptionLabel,
} from "@/lib/usage/api-key-options"

export type DashboardApiKeySelectMenuProps = {
  items: UsageLogApiKeyItemResponse[]
  /** Same token as usage-dashboard / logs (e.g. {@link DASHBOARD_API_KEY_ALL}). */
  allValue: string
  showAllOption: boolean
  noneValue?: string
  /** 개인·팀별 나의 사용량에서 키 0건일 때만 true — ‘없음’ 단일 항목. */
  showNoneOption?: boolean
}

/**
 * 대시보드·usagelog와 동일: 상단 사용 중, 하단 삭제됨(로그 보존), 삭제 행은 별칭+(삭제).
 * 테이블 렌더링에는 사용하지 않는다.
 */
export function DashboardApiKeySelectMenu({
  items,
  allValue,
  showAllOption,
  noneValue,
  showNoneOption,
}: DashboardApiKeySelectMenuProps) {
  if (showNoneOption && noneValue) {
    return (
      <SelectItem value={noneValue}>없음</SelectItem>
    )
  }

  const { active, deleted } = partitionUsageApiKeys(items)

  return (
    <>
      {showAllOption ? (
        <SelectItem value={allValue}>전체</SelectItem>
      ) : null}
      {active.length > 0 ? (
        <SelectGroup>
          <SelectLabel>사용 중</SelectLabel>
          {active.map((item) => (
            <SelectItem key={item.apiKeyId} value={item.apiKeyId}>
              {usageApiKeyOptionLabel(item)}
            </SelectItem>
          ))}
        </SelectGroup>
      ) : null}
      {deleted.length > 0 ? (
        <>
          {active.length > 0 ? <SelectSeparator /> : null}
          <SelectGroup>
            <SelectLabel>삭제됨 (로그 보존)</SelectLabel>
            {deleted.map((item) => (
              <SelectItem key={item.apiKeyId} value={item.apiKeyId}>
                {usageApiKeyOptionLabel(item)}
              </SelectItem>
            ))}
          </SelectGroup>
        </>
      ) : null}
    </>
  )
}
