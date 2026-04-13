"use client"

import type { ReactNode } from "react"
import { createContext, useCallback, useContext, useMemo, useState } from "react"
import { X } from "lucide-react"

import { Button, cn } from "@ai-usage/ui"

export type ToastSpec = {
  id: string
  title: string
  body?: string
  createdAt: number
  ttlMs: number
}

type ToastContextValue = {
  pushToast: (spec: Omit<ToastSpec, "id" | "createdAt"> & { id?: string; createdAt?: number }) => void
}

const ToastContext = createContext<ToastContextValue | null>(null)

function randomId(): string {
  return Math.random().toString(36).slice(2)
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastSpec[]>([])

  const pushToast = useCallback((spec: Omit<ToastSpec, "id" | "createdAt"> & { id?: string; createdAt?: number }) => {
    const id = spec.id ?? randomId()
    const createdAt = spec.createdAt ?? Date.now()
    const ttlMs = spec.ttlMs
    setToasts((prev) => {
      if (prev.some((t) => t.id === id)) return prev
      const next = [...prev, { id, title: spec.title, body: spec.body, createdAt, ttlMs }]
      return next.slice(-5)
    })
    window.setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id))
    }, ttlMs)
  }, [])

  const value = useMemo<ToastContextValue>(() => ({ pushToast }), [pushToast])

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="pointer-events-none fixed bottom-4 right-4 z-50 flex w-[min(420px,calc(100vw-2rem))] flex-col gap-2">
        {toasts
          .slice()
          .sort((a, b) => b.createdAt - a.createdAt)
          .map((t) => (
            <div
              key={t.id}
              className={cn(
                "pointer-events-auto rounded-xl border bg-card px-4 py-3 shadow-lg",
                "backdrop-blur supports-[backdrop-filter]:bg-card/90"
              )}
              role="status"
              aria-live="polite"
            >
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="text-sm font-semibold leading-snug">{t.title}</p>
                  {t.body ? <p className="mt-1 whitespace-pre-wrap text-sm text-muted-foreground">{t.body}</p> : null}
                </div>
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  className="h-8 w-8 shrink-0"
                  onClick={() => setToasts((prev) => prev.filter((x) => x.id !== t.id))}
                >
                  <X className="size-4" aria-hidden />
                  <span className="sr-only">닫기</span>
                </Button>
              </div>
            </div>
          ))}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast() {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error("useToast must be used within ToastProvider")
  return ctx
}

