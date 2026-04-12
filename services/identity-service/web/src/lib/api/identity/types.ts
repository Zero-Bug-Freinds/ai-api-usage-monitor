export type ApiResponse<T> = {
  success: boolean
  message: string
  data: T | null
}

export type Role = "USER" | "ADMIN"

export type SignupRequest = {
  email: string
  password: string
  passwordConfirm: string
  name: string
  role: Role
}

export type SignupResponse = {
  userId: number
  email: string
  name: string
  role: Role
}

export type LoginRequest = {
  email: string
  password: string
}

export type LoginResponse = {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
}

export type ExternalKeyProvider = "GEMINI" | "OPENAI" | "ANTHROPIC"

export type CreateExternalKeyRequest = {
  provider: ExternalKeyProvider
  externalKey: string
  alias: string
  monthlyBudgetUsd: number
}

export type UpdateExternalKeyRequest = {
  alias: string
  provider?: ExternalKeyProvider
  externalKey?: string
  monthlyBudgetUsd: number
}

/** Identity `POST /api/auth/external-keys`의 `data` 본문과 동기화 */
export type CreateExternalKeyResponseData = {
  id: number
  provider: ExternalKeyProvider
  alias: string
  createdAt: string
  monthlyBudgetUsd?: number | null
}

/** Identity `GET /api/auth/external-keys`의 `data` 아이템과 동기화 */
export type ExternalKeySummary = {
  id: number
  provider: ExternalKeyProvider
  alias: string
  createdAt: string
  monthlyBudgetUsd?: number | null
  /** ISO-8601, 삭제 예정일 때만 */
  deletionRequestedAt?: string | null
  /** ISO-8601, 영구 삭제 예정 시각(유예 종료) */
  permanentDeletionAt?: string | null
  /** 삭제 요청 시 선택한 유예 기간(일), 삭제 예정일 때만 */
  deletionGraceDays?: number | null
}

/** Identity `GET /api/auth/external-keys`의 `data` 본문과 동기화 */
export type ExternalKeyListResponseData = ExternalKeySummary[]

/** Identity `GET /api/auth/session`의 `data` 본문과 동기화 */
export type SessionResponse = {
  email: string
  role: Role
  authenticated: boolean
}

