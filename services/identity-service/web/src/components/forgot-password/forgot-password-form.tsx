"use client"

import Link from "next/link"
import * as React from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { useForm } from "react-hook-form"

import { Button, Input, Label } from "@ai-usage/ui"
import { apiFetch } from "@/lib/api/client-fetch"
import { forgotPasswordSchema, type ForgotPasswordInput } from "@/lib/api/identity/password-reset.schema"
import type { ApiResponse } from "@/lib/api/identity/types"

type FormState =
  | { status: "idle" }
  | { status: "submitting" }
  | { status: "error"; message: string }
  | { status: "success"; message: string }

export function ForgotPasswordForm() {
  const [state, setState] = React.useState<FormState>({ status: "idle" })

  const form = useForm<ForgotPasswordInput>({
    resolver: zodResolver(forgotPasswordSchema),
    defaultValues: { email: "" },
    mode: "onSubmit",
  })

  async function onSubmit(values: ForgotPasswordInput) {
    setState({ status: "submitting" })

    let res: Response
    let json: ApiResponse<null> | null = null
    try {
      const result = await apiFetch<null>(
        "/api/auth/forgot-password",
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
      setState({ status: "success", message: json.message })
      return
    }

    const message = json?.message ?? "요청 처리에 실패했습니다"
    setState({ status: "error", message })
  }

  const isSubmitting = state.status === "submitting"

  if (state.status === "success") {
    return (
      <div className="w-full max-w-md rounded-xl border bg-card p-6 shadow-sm">
        <h1 className="text-xl font-semibold tracking-tight">안내</h1>
        <p className="mt-3 text-sm text-muted-foreground">{state.message}</p>
        <p className="mt-4 text-sm text-muted-foreground">
          <Link href="/login" className="font-medium text-foreground underline underline-offset-4">
            로그인으로 돌아가기
          </Link>
        </p>
      </div>
    )
  }

  return (
    <div className="w-full max-w-md rounded-xl border bg-card p-6 shadow-sm">
      <div className="space-y-1">
        <h1 className="text-xl font-semibold tracking-tight">비밀번호 찾기</h1>
        <p className="text-sm text-muted-foreground">
          가입 시 사용한 이메일을 입력하면 재설정 링크를 보냅니다.
        </p>
      </div>

      <form className="mt-6 space-y-4" onSubmit={form.handleSubmit(onSubmit)} noValidate>
        <div className="space-y-2">
          <Label htmlFor="email">이메일</Label>
          <Input
            id="email"
            type="email"
            autoComplete="email"
            placeholder="you@example.com"
            aria-invalid={!!form.formState.errors.email}
            {...form.register("email")}
          />
          {form.formState.errors.email?.message ? (
            <p className="text-sm text-destructive">{form.formState.errors.email.message}</p>
          ) : null}
        </div>

        {state.status === "error" ? (
          <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {state.message}
          </div>
        ) : null}

        <Button type="submit" className="w-full" disabled={isSubmitting}>
          {isSubmitting ? "전송 중..." : "재설정 링크 보내기"}
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
