import { z } from "zod"

export const roleSchema = z.enum(["USER", "ADMIN"])

export const signupRequestSchema = z.object({
  email: z.string().email("이메일 형식이 올바르지 않습니다"),
  password: z
    .string()
    .min(8, "비밀번호는 8자 이상이어야 합니다")
    .max(100, "비밀번호는 100자 이하여야 합니다"),
  name: z.string().min(1, "이름은 필수입니다").max(100, "이름은 100자 이하여야 합니다"),
  role: roleSchema,
})

export type SignupRequestInput = z.infer<typeof signupRequestSchema>

