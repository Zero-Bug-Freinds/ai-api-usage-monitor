import { z } from "zod"

/** Identity `SignupRequest` `@Pattern` 메시지와 동일 (services/identity-service) */
export const signupPasswordPolicyMessage =
  "비밀번호는 소문자/숫자/특수문자를 각각 1개 이상 포함하고 대문자 없이 8~100자여야 합니다"

/** 백엔드 `SignupRequest` 정규식과 동일 */
const SIGNUP_PASSWORD_REGEX =
  /^(?=.*[a-z])(?=.*\d)(?=.*[^a-zA-Z0-9])(?=\S+$)[^A-Z]{8,100}$/

export const signupRequestSchema = z
  .object({
    email: z.string().email("이메일 형식이 올바르지 않습니다"),
    password: z.string().regex(SIGNUP_PASSWORD_REGEX, signupPasswordPolicyMessage),
    passwordConfirm: z.string().min(1, "비밀번호 확인을 입력해주세요"),
    name: z.string().min(1, "이름은 필수입니다").max(100, "이름은 100자 이하여야 합니다"),
  })
  .refine((data) => data.password === data.passwordConfirm, {
    message: "비밀번호가 일치하지 않습니다",
    path: ["passwordConfirm"],
  })

export type SignupRequestInput = z.infer<typeof signupRequestSchema>
