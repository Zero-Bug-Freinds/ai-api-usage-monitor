import { z } from "zod"

import { SIGNUP_PASSWORD_REGEX, signupPasswordPolicyMessage } from "@/lib/api/identity/signup.schema"

export const forgotPasswordSchema = z.object({
  email: z.string().email("이메일 형식이 올바르지 않습니다"),
})

export type ForgotPasswordInput = z.infer<typeof forgotPasswordSchema>

export const resetPasswordSchema = z
  .object({
    token: z.string().min(1, "재설정 토큰이 없습니다").max(512),
    password: z.string().regex(SIGNUP_PASSWORD_REGEX, signupPasswordPolicyMessage),
    passwordConfirm: z.string().min(1, "비밀번호 확인을 입력해주세요"),
  })
  .refine((data) => data.password === data.passwordConfirm, {
    message: "비밀번호가 일치하지 않습니다",
    path: ["passwordConfirm"],
  })

export type ResetPasswordInput = z.infer<typeof resetPasswordSchema>
