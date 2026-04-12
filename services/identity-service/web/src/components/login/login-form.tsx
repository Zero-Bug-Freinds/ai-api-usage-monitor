"use client"

import Link from "next/link"
import { useRouter, useSearchParams } from "next/navigation"
import * as React from "react"
import { Eye, EyeOff } from "lucide-react"
import { zodResolver } from "@hookform/resolvers/zod"
import { useForm } from "react-hook-form"

import { Button, Input, Label } from "@ai-usage/ui"
import { apiFetch } from "@/lib/api/client-fetch"
import { navigateAfterLogin } from "@/lib/auth/cross-app-navigation"
import { getSafeNextPath } from "@/lib/auth/safe-next-path"
import { loginRequestSchema, type LoginRequestInput } from "@/lib/api/identity/login.schema"
import type { ApiResponse } from "@/lib/api/identity/types"

type FormState = { status: "idle" } | { status: "submitting" } | { status: "error"; message: string }

function safeMessage(err: unknown, fallback: string) {
  return typeof err === "string" ? err : fallback
}

function statusFallbackMessage(status: number): string {
  if (status === 401) return "이메일 또는 비밀번호가 올바르지 않습니다"
  if (status >= 500) return "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
  return "로그인에 실패했습니다"
}

export function LoginForm() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const [state, setState] = React.useState<FormState>({ status: "idle" })
  const [showPassword, setShowPassword] = React.useState(false)

  const form = useForm<LoginRequestInput>({
    resolver: zodResolver(loginRequestSchema),
    defaultValues: {
      email: "",
      password: "",
    },
    mode: "onSubmit",
  })

  async function onSubmit(values: LoginRequestInput) {
    setState({ status: "submitting" })

    let res: Response
    let json: ApiResponse<null> | null = null
    try {
      const result = await apiFetch<null>(
        "/api/auth/login",
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          credentials: "include",
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

    if (res.ok && json && json.success) {
      const next = getSafeNextPath(searchParams.get("next"))
      navigateAfterLogin(next, router)
      return
    }

    const message = json?.message ?? statusFallbackMessage(res.status)
    setState({ status: "error", message: safeMessage(message, statusFallbackMessage(res.status)) })
  }

  const isSubmitting = state.status === "submitting"

  return (
    <div className="w-full max-w-md rounded-xl border bg-card p-6 shadow-sm">
      <div className="space-y-1">
        <h1 className="text-xl font-semibold tracking-tight">로그인</h1>
        <p className="text-sm text-muted-foreground">이메일과 비밀번호로 로그인합니다.</p>
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

        <div className="space-y-2">
          <div className="flex items-center justify-between gap-2">
            <Label htmlFor="password">비밀번호</Label>
            <Link
              href="/forgot-password"
              className="text-xs font-medium text-muted-foreground underline underline-offset-4 hover:text-foreground"
            >
              비밀번호를 잊으셨나요?
            </Link>
          </div>
          <div className="flex gap-1">
            <Input
              id="password"
              className="min-w-0 flex-1"
              type={showPassword ? "text" : "password"}
              autoComplete="current-password"
              placeholder="비밀번호를 입력하세요"
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

        {state.status === "error" ? (
          <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {state.message}
          </div>
        ) : null}

        <Button type="submit" className="w-full" disabled={isSubmitting}>
          {isSubmitting ? "로그인 중..." : "로그인"}
        </Button>
      </form>

      <p className="mt-4 text-sm text-muted-foreground">
        아직 계정이 없나요?{" "}
        <Link href="/signup" className="font-medium text-foreground underline underline-offset-4">
          회원가입
        </Link>
      </p>
    </div>
  )
}
