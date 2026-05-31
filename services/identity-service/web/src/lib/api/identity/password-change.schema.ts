import { z } from "zod"

import { SIGNUP_PASSWORD_REGEX, signupPasswordPolicyMessage } from "@/lib/api/identity/signup.schema"

export const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1, "현재 비밀번호를 입력해주세요").max(100),
    newPassword: z.string().regex(SIGNUP_PASSWORD_REGEX, signupPasswordPolicyMessage),
    newPasswordConfirm: z.string().min(1, "새 비밀번호 확인을 입력해주세요"),
  })
  .refine((data) => data.newPassword === data.newPasswordConfirm, {
    message: "비밀번호가 일치하지 않습니다",
    path: ["newPasswordConfirm"],
  })

export type ChangePasswordInput = z.infer<typeof changePasswordSchema>
