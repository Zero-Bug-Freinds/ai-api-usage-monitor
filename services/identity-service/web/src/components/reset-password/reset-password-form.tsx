"use client"

import Link from "next/link"
import { useRouter, useSearchParams } from "next/navigation"
import * as React from "react"
import { Eye, EyeOff } from "lucide-react"
import { zodResolver } from "@hookform/resolvers/zod"
import { useForm } from "react-hook-form"

import { Button, Input, Label } from "@ai-usage/ui"
import { apiFetch } from "@/lib/api/client-fetch"
import { resetPasswordSchema, type ResetPasswordInput } from "@/lib/api/identity/password-reset.schema"
import type { ApiResponse } from "@/lib/api/identity/types"

type FormState = { status: "idle" } | { status: "submitting" } | { status: "error"; message: string }

export function ResetPasswordForm() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const tokenFromUrl = searchParams.get("token")?.trim() ?? ""

  const [state, setState] = React.useState<FormState>({ status: "idle" })
  const [showPassword, setShowPassword] = React.useState(false)
  const [showConfirm, setShowConfirm] = React.useState(false)

  const form = useForm<ResetPasswordInput>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: {
      token: tokenFromUrl,
      password: "",
      passwordConfirm: "",
    },
    mode: "onSubmit",
  })

  React.useEffect(() => {
    form.setValue("token", tokenFromUrl)
  }, [tokenFromUrl, form])

  async function onSubmit(values: ResetPasswordInput) {
    setState({ status: "submitting" })

    let res: Response
    let json: ApiResponse<null> | null = null
    try {
      const result = await apiFetch<null>(
        "/api/auth/reset-password",
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(values),
        },
        { authRequired: false }
      )
      res = result.response
      json = result.json
    } catch {
      setState({ status: "error", message: "네트워크 오류가 발생했습니다. 잠시 후 다시 시도해주세요." })
      return
    }

    if (res.ok && json?.success) {
      router.replace("/login")
      return
    }

    const message = json?.message ?? "비밀번호 재설정에 실패했습니다"
    setState({ status: "error", message })
  }

  const isSubmitting = state.status === "submitting"

  if (!tokenFromUrl) {
    return (
      <div className="w-full max-w-md rounded-xl border bg-card p-6 shadow-sm">
        <h1 className="text-xl font-semibold tracking-tight">링크가 올바르지 않습니다</h1>
        <p className="mt-3 text-sm text-muted-foreground">
          이메일로 받은 재설정 링크로 다시 접속하거나, 비밀번호 찾기를 다시 요청해 주세요.
        </p>
        <p className="mt-4 text-sm">
          <Link href="/forgot-password" className="font-medium text-foreground underline underline-offset-4">
            비밀번호 찾기
          </Link>
        </p>
      </div>
    )
  }

  return (
    <div className="w-full max-w-md rounded-xl border bg-card p-6 shadow-sm">
      <div className="space-y-1">
        <h1 className="text-xl font-semibold tracking-tight">새 비밀번호 설정</h1>
        <p className="text-sm text-muted-foreground">회원가입과 동일한 비밀번호 규칙이 적용됩니다.</p>
      </div>

      <form className="mt-6 space-y-4" onSubmit={form.handleSubmit(onSubmit)} noValidate>
        <input type="hidden" {...form.register("token")} />

        <div className="space-y-2">
          <Label htmlFor="password">새 비밀번호</Label>
          <div className="flex gap-1">
            <Input
              id="password"
              className="min-w-0 flex-1"
              type={showPassword ? "text" : "password"}
              autoComplete="new-password"
              placeholder="새 비밀번호"
              aria-invalid={!!form.formState.errors.password}
              {...form.register("password")}
            />
            <button
              type="button"
              className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-md border border-input bg-background text-muted-foreground hover:bg-muted disabled:opacity-50"
              aria-label={showPassword ? "비밀번호 숨기기" : "비밀번호 보기"}
              disabled={isSubmitting}
              onClick={() => setShowPassword((v) => !v)}
            >
              {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            </button>
          </div>
          {form.formState.errors.password?.message ? (
            <p className="text-sm text-destructive">{form.formState.errors.password.message}</p>
          ) : null}
        </div>

        <div className="space-y-2">
          <Label htmlFor="passwordConfirm">새 비밀번호 확인</Label>
          <div className="flex gap-1">
            <Input
              id="passwordConfirm"
              className="min-w-0 flex-1"
              type={showConfirm ? "text" : "password"}
              autoComplete="new-password"
              placeholder="비밀번호 확인"
              aria-invalid={!!form.formState.errors.passwordConfirm}
              {...form.register("passwordConfirm")}
            />
            <button
              type="button"
              className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-md border border-input bg-background text-muted-foreground hover:bg-muted disabled:opacity-50"
              aria-label={showConfirm ? "비밀번호 확인 숨기기" : "비밀번호 확인 보기"}
              disabled={isSubmitting}
              onClick={() => setShowConfirm((v) => !v)}
            >
              {showConfirm ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            </button>
          </div>
          {form.formState.errors.passwordConfirm?.message ? (
            <p className="text-sm text-destructive">{form.formState.errors.passwordConfirm.message}</p>
          ) : null}
        </div>

        {state.status === "error" ? (
          <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {state.message}
          </div>
        ) : null}

        <Button type="submit" className="w-full" disabled={isSubmitting}>
          {isSubmitting ? "저장 중..." : "비밀번호 변경"}
        </Button>
      </form>

      <p className="mt-4 text-sm text-muted-foreground">
        <Link href="/login" className="font-medium text-foreground underline underline-offset-4">
          로그인으로 돌아가기
        </Link>
      </p>
    </div>
  )
}
