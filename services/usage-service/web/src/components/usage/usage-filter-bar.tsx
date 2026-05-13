"use client"

import * as React from "react"
import { Input, Label, Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@ai-usage/ui"
import { DashboardApiKeySelectMenu } from "@/components/usage/dashboard-api-key-select-menu"
import { DASHBOARD_PROVIDER_ALL } from "@/lib/usage/dashboard-provider-api-keys"
import { formatKstIsoDate } from "@/lib/usage/kst-dates"
import type { PeriodMode, StoredDashboardPeriod } from "@/lib/usage/usage-filter-period"
import { patchPeriodMode } from "@/lib/usage/use-filter-storage"
import type { UsageLogApiKeyItemResponse } from "@/lib/usage/types"

export type UsageFilterBarTeamConfig = {
  value: string
  onValueChange: (teamId: string) => void
  teams: Array<{ id: string; name: string }>
  loading: boolean
  selectId?: string
  /** Shown under the team select (e.g. load error). */
  footer?: React.ReactNode
}

export type UsageFilterBarApiKeyConfig = {
  value: string
  onValueChange: (id: string) => void
  menuItems: UsageLogApiKeyItemResponse[]
  keysLoading: boolean
  allValue: string
  showAllOption: boolean
  noneValue?: string
  showNoneOption?: boolean
  selectId?: string
}

export type UsageFilterBarVariant = "dashboard" | "usageLog"

export type UsageFilterBarProps = {
  /** Prefix for stable ids (`dash`, `team-dash`, `log`, …). */
  idPrefix: string
  /** `usageLog`: `__all__` sentinel and provider order aligned with 상세 로그. */
  variant?: UsageFilterBarVariant
  globalDisabled?: boolean
  provider: string
  onProviderChange: (v: string) => void
  period: StoredDashboardPeriod
  onPeriodChange: (next: StoredDashboardPeriod) => void
  /** Extra copy above custom date inputs (e.g. max range note). */
  periodCustomNote?: React.ReactNode
  showTeam?: boolean
  team?: UsageFilterBarTeamConfig
  apiKey: UsageFilterBarApiKeyConfig
}

const LOG_PROVIDER_ALL = "__all__"

export function UsageFilterBar({
  idPrefix,
  variant = "dashboard",
  globalDisabled = false,
  provider,
  onProviderChange,
  period,
  onPeriodChange,
  periodCustomNote,
  showTeam = false,
  team,
  apiKey,
}: UsageFilterBarProps) {
  const todayKst = formatKstIsoDate()
  const g = globalDisabled
  const apiKeyDisabled = g || apiKey.keysLoading || apiKey.menuItems.length === 0
  const teamDisabled = team ? g || team.loading || team.teams.length === 0 : true
  const providerAllValue = variant === "usageLog" ? LOG_PROVIDER_ALL : DASHBOARD_PROVIDER_ALL

  return (
    <div className="flex flex-wrap items-end gap-4">
      <div className="space-y-2 sm:w-52">
        <Label htmlFor={`${idPrefix}-provider`}>공급사</Label>
        <Select value={provider} onValueChange={onProviderChange} disabled={g}>
          <SelectTrigger id={`${idPrefix}-provider`}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={providerAllValue}>전체</SelectItem>
            {variant === "usageLog" ? (
              <>
                <SelectItem value="OPENAI">OpenAI</SelectItem>
                <SelectItem value="ANTHROPIC">Anthropic</SelectItem>
                <SelectItem value="GOOGLE">Google</SelectItem>
              </>
            ) : (
              <>
                <SelectItem value="GOOGLE">Gemini (Google)</SelectItem>
                <SelectItem value="OPENAI">OpenAI</SelectItem>
                <SelectItem value="ANTHROPIC">Anthropic</SelectItem>
              </>
            )}
          </SelectContent>
        </Select>
      </div>

      <div className="space-y-2 sm:w-44">
        <Label htmlFor={`${idPrefix}-period`}>기간</Label>
        <Select
          value={period.mode}
          disabled={g}
          onValueChange={(v) => {
            const nextMode = v as PeriodMode
            onPeriodChange(patchPeriodMode(nextMode, todayKst, period))
          }}
        >
          <SelectTrigger id={`${idPrefix}-period`}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="today">오늘</SelectItem>
            <SelectItem value="7d">최근 7일</SelectItem>
            <SelectItem value="30d">최근 30일</SelectItem>
            <SelectItem value="custom">기간 지정</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {showTeam && team ? (
        <div className="space-y-2 sm:w-52 min-w-[10rem]">
          <Label htmlFor={team.selectId ?? `${idPrefix}-team`}>팀</Label>
          <Select
            value={team.value && team.teams.some((x) => x.id === team.value) ? team.value : undefined}
            onValueChange={team.onValueChange}
            disabled={teamDisabled}
          >
            <SelectTrigger id={team.selectId ?? `${idPrefix}-team`} className="w-full">
              <SelectValue
                placeholder={
                  team.loading ? "팀 목록 불러오는 중…" : team.teams.length === 0 ? "소속 팀 없음" : "팀 선택"
                }
              />
            </SelectTrigger>
            <SelectContent>
              {team.teams.map((tm) => (
                <SelectItem key={tm.id} value={tm.id}>
                  {tm.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          {team.footer}
        </div>
      ) : null}

      <div className="space-y-2 sm:min-w-[12rem] sm:max-w-[20rem]">
        <Label htmlFor={apiKey.selectId ?? `${idPrefix}-api-key`}>API Key</Label>
        <Select value={apiKey.value} onValueChange={apiKey.onValueChange} disabled={apiKeyDisabled}>
          <SelectTrigger id={apiKey.selectId ?? `${idPrefix}-api-key`}>
            <SelectValue
              placeholder={
                apiKey.keysLoading ? "불러오는 중…" : apiKey.menuItems.length === 0 ? "없음" : "전체"
              }
            />
          </SelectTrigger>
          <SelectContent className="max-h-[min(70vh,26rem)]">
            <DashboardApiKeySelectMenu
              items={apiKey.menuItems}
              allValue={apiKey.allValue}
              showAllOption={apiKey.showAllOption}
              noneValue={apiKey.noneValue}
              showNoneOption={apiKey.showNoneOption}
            />
          </SelectContent>
        </Select>
      </div>

      {period.mode === "custom" ? (
        <div className="flex min-w-[18rem] flex-col gap-2 lg:pb-0.5">
          {periodCustomNote ? (
            <div className="text-xs text-muted-foreground">{periodCustomNote}</div>
          ) : null}
          <div className="flex flex-wrap gap-3">
            <div className="space-y-2">
              <Label htmlFor={`${idPrefix}-custom-from`}>시작</Label>
              <Input
                id={`${idPrefix}-custom-from`}
                type="date"
                value={period.from}
                disabled={g}
                onChange={(e) => onPeriodChange({ ...period, mode: "custom", from: e.target.value })}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor={`${idPrefix}-custom-to`}>종료</Label>
              <Input
                id={`${idPrefix}-custom-to`}
                type="date"
                value={period.to}
                disabled={g}
                onChange={(e) => onPeriodChange({ ...period, mode: "custom", to: e.target.value })}
              />
            </div>
          </div>
        </div>
      ) : null}
    </div>
  )
}
