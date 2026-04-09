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
  monthlyBudgetUsd: z
    .number({ message: "monthlyBudgetUsd는 숫자여야 합니다" })
    .min(0, "monthlyBudgetUsd는 0 이상이어야 합니다")
    .multipleOf(0.01, "monthlyBudgetUsd는 소수점 둘째 자리까지 입력할 수 있습니다")
    .optional(),
})

export const updateExternalKeyRequestSchema = z
  .object({
    provider: externalKeyProviderSchema.optional(),
    externalKey: z.string({ message: "externalKey는 문자열이어야 합니다" }).trim().optional(),
    alias: z.string({ message: "alias는 문자열이어야 합니다" }).trim().min(1, "alias는 필수입니다"),
    monthlyBudgetUsd: z
      .number({ message: "monthlyBudgetUsd는 숫자여야 합니다" })
      .min(0, "monthlyBudgetUsd는 0 이상이어야 합니다")
      .multipleOf(0.01, "monthlyBudgetUsd는 소수점 둘째 자리까지 입력할 수 있습니다")
      .nullable()
      .optional(),
  })
  .superRefine((value, ctx) => {
    if (value.externalKey && !value.provider) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["provider"],
        message: "externalKey를 수정할 때 provider는 필수입니다",
      })
    }
  })

export type CreateExternalKeyRequestInput = z.infer<typeof createExternalKeyRequestSchema>
export type UpdateExternalKeyRequestInput = z.infer<typeof updateExternalKeyRequestSchema>
