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

/** Identity `GET /api/auth/session`의 `data` 본문과 동기화 */
export type SessionResponse = {
  email: string
  role: Role
  authenticated: boolean
}

