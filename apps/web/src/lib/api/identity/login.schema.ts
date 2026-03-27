import { z } from "zod"

export const loginRequestSchema = z.object({
  email: z.string().email("이메일 형식이 올바르지 않습니다"),
  password: z.string().min(8, "비밀번호는 8자 이상이어야 합니다").max(100, "비밀번호는 100자 이하여야 합니다"),
})

export type LoginRequestInput = z.infer<typeof loginRequestSchema>
