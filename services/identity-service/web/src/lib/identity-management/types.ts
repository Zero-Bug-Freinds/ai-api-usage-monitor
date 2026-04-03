import type { ApiResponse } from "@/lib/api/identity/types"

/**
 * Identity `GET /api/v1/me/organizations` 등 관리 API의 `data` 형태(팀원 B 구현과 맞출 것).
 * 필드는 최소 집합이며, 백엔드 확장 시 여기에 추가한다.
 */
export type OrganizationSummary = {
  id: string
  name: string
  description?: string | null
}

export type TeamSummary = {
  id: string
  name: string
  organizationId?: string | null
  description?: string | null
}

export type { ApiResponse }
