import { z } from "zod"

export const externalKeyProviderSchema = z.enum(["GEMINI", "OPENAI", "ANTHROPIC"], {
  message: "provider는 GEMINI/OPENAI/ANTHROPIC 중 하나여야 합니다",
})

export const createExternalKeyRequestSchema = z.object({
  provider: externalKeyProviderSchema,
  externalKey: z
    .string({ message: "externalKey는 문자열이어야 합니다" })
    .trim()
    .min(1, "externalKey는 필수입니다"),
  alias: z.string({ message: "alias는 문자열이어야 합니다" }).trim().min(1, "alias는 필수입니다"),
})

export type CreateExternalKeyRequestInput = z.infer<typeof createExternalKeyRequestSchema>
