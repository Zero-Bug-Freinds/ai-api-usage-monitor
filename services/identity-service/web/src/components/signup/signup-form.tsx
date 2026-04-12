"use client"

import Link from "next/link"
import { useRouter } from "next/navigation"
import * as React from "react"
import { Eye, EyeOff } from "lucide-react"
import { zodResolver } from "@hookform/resolvers/zod"
import { useForm } from "react-hook-form"

import { Button, Input, Label } from "@ai-usage/ui"
import { apiFetch } from "@/lib/api/client-fetch"
import {
  signupPasswordPolicyMessage,
  signupRequestSchema,
  type SignupRequestInput,
} from "@/lib/api/identity/signup.schema"
import type { ApiResponse, SignupResponse } from "@/lib/api/identity/types"

type FormState =
  | { status: "idle" }
  | { status: "submitting" }
  | { status: "error"; message: string }

function safeMessage(err: unknown, fallback: string) {
  return typeof err === "string" ? err : fallback
}

export function SignupForm() {
  const router = useRouter()
  const [state, setState] = React.useState<FormState>({ status: "idle" })
  const [showPassword, setShowPassword] = React.useState(false)
  const [showPasswordConfirm, setShowPasswordConfirm] = React.useState(false)

  const form = useForm<SignupRequestInput>({
    resolver: zodResolver(signupRequestSchema),
    defaultValues: {
      email: "",
      password: "",
      passwordConfirm: "",
      name: "",
    },
    mode: "onSubmit",
  })

  async function onSubmit(values: SignupRequestInput) {
    setState({ status: "submitting" })

    let res: Response
    let json: ApiResponse<SignupResponse> | ApiResponse<null> | null = null
    try {
      const result = await apiFetch<SignupResponse>(
        "/api/auth/signup",
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

    if (res.ok && json?.success === true) {
      router.replace("/login")
      return
    }

    const message = json?.message ?? "회원가입에 실패했습니다"
    setState({ status: "error", message: safeMessage(message, "회원가입에 실패했습니다") })
  }

  const isSubmitting = state.status === "submitting"

  return (
    <div className="w-full max-w-md rounded-xl border bg-card p-6 shadow-sm">
      <div className="space-y-1">
        <h1 className="text-xl font-semibold tracking-tight">회원가입</h1>
        <p className="text-sm text-muted-foreground">
          이메일과 비밀번호로 계정을 생성합니다.
        </p>
      </div>

      <form
        className="mt-6 space-y-4"
        onSubmit={form.handleSubmit(onSubmit)}
        noValidate
      >
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
            <p className="text-sm text-destructive">
              {form.formState.errors.email.message}
            </p>
          ) : null}
        </div>

        <div className="space-y-2">
          <Label htmlFor="password">비밀번호</Label>
          <div className="flex gap-1">
            <Input
              id="password"
              className="min-w-0 flex-1"
              type={showPassword ? "text" : "password"}
              autoComplete="new-password"
              placeholder="예: abc123!@"
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
            <p className="text-sm text-destructive">
              {form.formState.errors.password.message}
            </p>
          ) : (
            <p className="text-xs text-muted-foreground">{signupPasswordPolicyMessage}</p>
          )}
        </div>

        <div className="space-y-2">
          <Label htmlFor="passwordConfirm">비밀번호 확인</Label>
          <div className="flex gap-1">
            <Input
              id="passwordConfirm"
              className="min-w-0 flex-1"
              type={showPasswordConfirm ? "text" : "password"}
              autoComplete="new-password"
              placeholder="비밀번호를 다시 입력하세요"
              aria-invalid={!!form.formState.errors.passwordConfirm}
              {...form.register("passwordConfirm")}
            />
            <button
              type="button"
              className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-md border border-input bg-background text-muted-foreground hover:bg-muted disabled:opacity-50"
              aria-label={showPasswordConfirm ? "비밀번호 확인 숨기기" : "비밀번호 확인 보기"}
              disabled={isSubmitting}
              onClick={() => setShowPasswordConfirm((v) => !v)}
            >
              {showPasswordConfirm ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            </button>
          </div>
          {form.formState.errors.passwordConfirm?.message ? (
            <p className="text-sm text-destructive">
              {form.formState.errors.passwordConfirm.message}
            </p>
          ) : null}
        </div>

        <div className="space-y-2">
          <Label htmlFor="name">이름</Label>
          <Input
            id="name"
            type="text"
            autoComplete="name"
            placeholder="홍길동"
            aria-invalid={!!form.formState.errors.name}
            {...form.register("name")}
          />
          {form.formState.errors.name?.message ? (
            <p className="text-sm text-destructive">{form.formState.errors.name.message}</p>
          ) : null}
        </div>

        {state.status === "error" ? (
          <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {state.message}
          </div>
        ) : null}

        <Button type="submit" className="w-full" disabled={isSubmitting}>
          {isSubmitting ? "가입 중..." : "회원가입"}
        </Button>
      </form>
      <p className="mt-4 text-sm text-muted-foreground">
        이미 계정이 있나요?{" "}
        <Link href="/login" className="font-medium text-foreground underline underline-offset-4">
          로그인
        </Link>
      </p>
    </div>
  )
}

