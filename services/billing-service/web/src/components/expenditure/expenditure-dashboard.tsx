"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

import { rangeLastDays } from "@/lib/expenditure/dates";
import type { AiProviderCode, ApiKeySeen, DailyPoint, ExpenditureSummary, MonthlyPoint } from "@/lib/expenditure/types";

const PROVIDERS: { value: AiProviderCode; label: string }[] = [
  { value: "GOOGLE", label: "Gemini (GOOGLE)" },
  { value: "OPENAI", label: "OpenAI" },
  { value: "ANTHROPIC", label: "Anthropic" },
];

function expenditureApiPath(path: string): string {
  const base = (process.env.NEXT_PUBLIC_BASE_PATH ?? "").replace(/\/$/, "");
  const normalized = path.startsWith("/") ? path : `/${path}`;
  return `${base}${normalized}`;
}

async function fetchJson<T>(path: string): Promise<T> {
  const res = await fetch(path, { credentials: "include", cache: "no-store" });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || `HTTP ${res.status}`);
  }
  return (await res.json()) as T;
}

export function ExpenditureDashboard() {
  const [provider, setProvider] = useState<AiProviderCode>("GOOGLE");
  const [apiKeys, setApiKeys] = useState<ApiKeySeen[]>([]);
  const [apiKeyId, setApiKeyId] = useState<string>("");
  const [range] = useState(() => rangeLastDays(30));
  const [summary, setSummary] = useState<ExpenditureSummary | null>(null);
  const [daily, setDaily] = useState<DailyPoint[]>([]);
  const [monthly, setMonthly] = useState<MonthlyPoint[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const loadApiKeys = useCallback(async () => {
    const q = new URLSearchParams();
    q.set("provider", provider);
    const list = await fetchJson<ApiKeySeen[]>(expenditureApiPath(`/api/expenditure/api-keys?${q.toString()}`));
    setApiKeys(list);
    if (list.length > 0) {
      const sorted = [...list].sort((a, b) => a.firstSeenAt.localeCompare(b.firstSeenAt));
      setApiKeyId((prev) => {
        if (prev && list.some((k) => k.apiKeyId === prev)) return prev;
        return sorted[0]?.apiKeyId ?? "";
      });
    } else {
      setApiKeyId("");
    }
  }, [provider]);

  useEffect(() => {
    void loadApiKeys().catch((e: unknown) => {
      setError(e instanceof Error ? e.message : "API 키 목록을 불러오지 못했습니다");
    });
  }, [loadApiKeys]);

  const loadSeries = useCallback(async () => {
    if (!apiKeyId) {
      setSummary(null);
      setDaily([]);
      setMonthly([]);
      return;
    }
    setLoading(true);
    setError(null);
    const qBase = new URLSearchParams({
      apiKeyId,
      from: range.from,
      to: range.to,
    });
    try {
      const qSummary = new URLSearchParams(qBase);
      qSummary.set("provider", provider);
      const s = await fetchJson<ExpenditureSummary>(expenditureApiPath(`/api/expenditure/summary?${qSummary.toString()}`));
      setSummary(s);

      const qDaily = new URLSearchParams(qBase);
      qDaily.set("provider", provider);
      const d = await fetchJson<DailyPoint[]>(expenditureApiPath(`/api/expenditure/daily?${qDaily.toString()}`));
      setDaily(d);

      const qMonthly = new URLSearchParams({ apiKeyId, from: range.from, to: range.to });
      const m = await fetchJson<MonthlyPoint[]>(expenditureApiPath(`/api/expenditure/monthly?${qMonthly.toString()}`));
      setMonthly(m);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "데이터를 불러오지 못했습니다");
    } finally {
      setLoading(false);
    }
  }, [apiKeyId, provider, range.from, range.to]);

  useEffect(() => {
    void loadSeries();
  }, [loadSeries]);

  const budgetRatio = useMemo(() => {
    if (!summary?.monthlyBudgetUsd || summary.monthlyBudgetUsd <= 0) return null;
    return Math.min(1, summary.totalCostUsd / summary.monthlyBudgetUsd);
  }, [summary]);

  const dailyChart = useMemo(
    () =>
      daily.map((p) => ({
        date: p.date.slice(5),
        usd: Number(p.costUsd),
      })),
    [daily]
  );

  const monthlyChart = useMemo(
    () =>
      monthly.map((p) => ({
        month: p.monthStartDate.slice(0, 7),
        usd: Number(p.costUsd),
        finalized: p.finalized,
      })),
    [monthly]
  );

  return (
    <div className="space-y-8">
      <header className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">지출</h1>
        <p className="text-sm text-muted-foreground">프로바이더와 API 키를 선택한 뒤 기간별 비용을 확인합니다.</p>
      </header>

      <div className="flex flex-wrap items-end gap-4">
        <label className="flex flex-col gap-1.5 text-sm">
          <span className="text-muted-foreground">프로바이더</span>
          <select
            className="h-9 min-w-[200px] rounded-md border border-input bg-background px-2 text-sm"
            value={provider}
            onChange={(e) => setProvider(e.target.value as AiProviderCode)}
          >
            {PROVIDERS.map((p) => (
              <option key={p.value} value={p.value}>
                {p.label}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1.5 text-sm">
          <span className="text-muted-foreground">API 키</span>
          <select
            className="h-9 min-w-[240px] max-w-full rounded-md border border-input bg-background px-2 text-sm"
            value={apiKeyId}
            onChange={(e) => setApiKeyId(e.target.value)}
            disabled={apiKeys.length === 0}
          >
            {apiKeys.length === 0 ? (
              <option value="">이벤트로 관측된 키 없음</option>
            ) : (
              apiKeys.map((k) => (
                <option key={`${k.provider}-${k.apiKeyId}`} value={k.apiKeyId}>
                  {k.apiKeyId.slice(0, 12)}…
                </option>
              ))
            )}
          </select>
        </label>
        <div className="text-xs text-muted-foreground">
          기간: {range.from} ~ {range.to} (30일)
        </div>
      </div>

      {error ? (
        <div className="rounded-lg border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</div>
      ) : null}

      {loading ? <p className="text-sm text-muted-foreground">불러오는 중…</p> : null}

      {summary && apiKeyId ? (
        <section className="space-y-3 rounded-xl border border-border bg-card p-4 shadow-sm">
          <h2 className="text-sm font-medium text-muted-foreground">요약</h2>
          <div className="flex flex-wrap items-baseline gap-6">
            <div>
              <p className="text-xs text-muted-foreground">총 지출 (USD)</p>
              <p className="text-2xl font-semibold tabular-nums">${summary.totalCostUsd.toFixed(4)}</p>
            </div>
            {summary.monthlyBudgetUsd != null ? (
              <div>
                <p className="text-xs text-muted-foreground">월 예산 (identity 연동 시)</p>
                <p className="text-lg font-medium tabular-nums">${summary.monthlyBudgetUsd.toFixed(2)}</p>
              </div>
            ) : (
              <p className="text-xs text-muted-foreground">월 예산: identity HTTP 계약 미구성 시 표시되지 않습니다.</p>
            )}
          </div>
          {budgetRatio != null ? (
            <div className="space-y-1">
              <div className="flex justify-between text-xs text-muted-foreground">
                <span>예산 대비</span>
                <span>{(budgetRatio * 100).toFixed(1)}%</span>
              </div>
              <div className="h-2 w-full overflow-hidden rounded-full bg-muted">
                <div
                  className="h-full rounded-full bg-primary transition-[width]"
                  style={{ width: `${Math.min(100, budgetRatio * 100)}%` }}
                />
              </div>
            </div>
          ) : null}
        </section>
      ) : null}

      {dailyChart.length > 0 ? (
        <section className="space-y-3">
          <h2 className="text-sm font-medium">일별 지출</h2>
          <div className="h-72 w-full rounded-xl border border-border bg-card p-2">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={dailyChart}>
                <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
                <XAxis dataKey="date" tick={{ fontSize: 11 }} />
                <YAxis tick={{ fontSize: 11 }} width={56} />
                <Tooltip formatter={(v: number) => [`$${v.toFixed(4)}`, "USD"]} />
                <Line type="monotone" dataKey="usd" stroke="var(--color-chart-1)" strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </section>
      ) : null}

      {monthlyChart.length > 0 ? (
        <section className="space-y-3">
          <h2 className="text-sm font-medium">월별 지출</h2>
          <div className="h-72 w-full rounded-xl border border-border bg-card p-2">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={monthlyChart}>
                <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
                <XAxis dataKey="month" tick={{ fontSize: 11 }} />
                <YAxis tick={{ fontSize: 11 }} width={56} />
                <Tooltip formatter={(v: number) => [`$${v.toFixed(4)}`, "USD"]} />
                <Bar dataKey="usd" fill="var(--color-chart-2)" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </section>
      ) : null}
    </div>
  );
}
