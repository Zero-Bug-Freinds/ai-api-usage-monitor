import { z } from "zod"

export const deleteAccountSchema = z.object({
  password: z.string().min(1, "비밀번호를 입력해주세요").max(100),
})

export type DeleteAccountInput = z.infer<typeof deleteAccountSchema>
