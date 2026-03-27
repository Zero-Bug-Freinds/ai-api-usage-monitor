"use client"

import Link from "next/link"
import * as React from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { useForm, Controller } from "react-hook-form"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import {
  signupPasswordPolicyMessage,
  signupRequestSchema,
  type SignupRequestInput,
} from "@/lib/api/identity/signup.schema"
import type { ApiResponse, SignupResponse } from "@/lib/api/identity/types"

type FormState =
  | { status: "idle" }
  | { status: "submitting" }
  | { status: "success"; message: string; data: SignupResponse }
  | { status: "error"; message: string }

function safeMessage(err: unknown, fallback: string) {
  return typeof err === "string" ? err : fallback
}

export function SignupForm() {
  const [state, setState] = React.useState<FormState>({ status: "idle" })

  const form = useForm<SignupRequestInput>({
    resolver: zodResolver(signupRequestSchema),
    defaultValues: {
      email: "",
      password: "",
      passwordConfirm: "",
      name: "",
      role: "USER",
    },
    mode: "onSubmit",
  })

  async function onSubmit(values: SignupRequestInput) {
    setState({ status: "submitting" })

    let res: Response
    try {
      res = await fetch("/api/auth/signup", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(values),
      })
    } catch {
      setState({ status: "error", message: "네트워크 오류가 발생했습니다. 잠시 후 다시 시도해주세요." })
      return
    }

    let json: ApiResponse<SignupResponse> | ApiResponse<null> | null = null
    try {
      json = (await res.json()) as ApiResponse<SignupResponse>
    } catch {
      json = null
    }

    if (res.ok && json && json.success && json.data) {
      setState({ status: "success", message: json.message, data: json.data })
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
          <Input
            id="password"
            type="password"
            autoComplete="new-password"
            placeholder="예: abc123!@"
            aria-invalid={!!form.formState.errors.password}
            {...form.register("password")}
          />
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
          <Input
            id="passwordConfirm"
            type="password"
            autoComplete="new-password"
            placeholder="비밀번호를 다시 입력하세요"
            aria-invalid={!!form.formState.errors.passwordConfirm}
            {...form.register("passwordConfirm")}
          />
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

        <div className="space-y-2">
          <Label>역할</Label>
          <Controller
            control={form.control}
            name="role"
            render={({ field }) => (
              <Select value={field.value} onValueChange={field.onChange}>
                <SelectTrigger className="w-full">
                  <SelectValue placeholder="역할 선택" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="USER">USER</SelectItem>
                  <SelectItem value="ADMIN">ADMIN</SelectItem>
                </SelectContent>
              </Select>
            )}
          />
          {form.formState.errors.role?.message ? (
            <p className="text-sm text-destructive">{form.formState.errors.role.message}</p>
          ) : (
            <p className="text-xs text-muted-foreground">기본값은 USER 입니다.</p>
          )}
        </div>

        {state.status === "error" ? (
          <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {state.message}
          </div>
        ) : null}

        {state.status === "success" ? (
          <div className="rounded-lg border bg-muted px-3 py-2 text-sm">
            <p className="font-medium">{state.message || "회원가입이 완료되었습니다"}</p>
            <p className="mt-1 text-muted-foreground">
              {state.data.email} ({state.data.role})
            </p>
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

