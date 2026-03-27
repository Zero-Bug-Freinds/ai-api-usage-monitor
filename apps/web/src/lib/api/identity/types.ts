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

