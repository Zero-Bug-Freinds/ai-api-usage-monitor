"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

import { currentMonthStartKst, rangeLastDays } from "@/lib/expenditure/dates";
import type {
  AiProviderCode,
  ApiKeySeen,
  DailyPoint,
  ExpenditureSummary,
  MonthlyPoint,
  TeamMonthRollup,
} from "@/lib/expenditure/types";

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

async function fetchJsonPost<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(path, {
    method: "POST",
    credentials: "include",
    cache: "no-store",
    headers: { Accept: "application/json", "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || `HTTP ${res.status}`);
  }
  return (await res.json()) as T;
}

function formatUsdTooltip(value: unknown): [string, string] {
  if (value == null) {
    return ["—", "USD"];
  }
  const raw = Array.isArray(value) ? value[0] : value;
  const n = typeof raw === "number" ? raw : Number(raw);
  if (Number.isNaN(n)) {
    return ["—", "USD"];
  }
  return [`$${n.toFixed(4)}`, "USD"];
}

async function fetchTeamMemberUserIds(teamId: string): Promise<string[]> {
  const res = await fetch(`/api/team/v1/teams/${encodeURIComponent(teamId)}/members`, {
    method: "GET",
    credentials: "include",
    headers: { Accept: "application/json" },
    cache: "no-store",
  });
  if (!res.ok) {
    return [];
  }
  const json: unknown = await res.json();
  if (typeof json !== "object" || json === null) return [];
  const rec = json as Record<string, unknown>;
  if (rec.success !== true || !Array.isArray(rec.data)) return [];
  return rec.data.filter((x): x is string => typeof x === "string");
}

export function ExpenditureDashboard() {
  const [viewMode, setViewMode] = useState<"personal" | "team">("personal");
  const [provider, setProvider] = useState<AiProviderCode>("GOOGLE");
  const [apiKeys, setApiKeys] = useState<ApiKeySeen[]>([]);
  const [apiKeyId, setApiKeyId] = useState<string>("");
  const [range] = useState(() => rangeLastDays(30));
  const [summary, setSummary] = useState<ExpenditureSummary | null>(null);
  const [daily, setDaily] = useState<DailyPoint[]>([]);
  const [monthly, setMonthly] = useState<MonthlyPoint[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const [teamIdInput, setTeamIdInput] = useState("");
  const [teamRollup, setTeamRollup] = useState<TeamMonthRollup | null>(null);
  const [teamLoading, setTeamLoading] = useState(false);

  const loadApiKeys = useCallback(async () => {
    const q = new URLSearchParams();
    q.set("provider", provider);
    const list = await fetchJson<ApiKeySeen[]>(expenditureApiPath(`/api/expenditure/api-keys?${q.toString()}`));
    const sortedOldestFirst = [...list].sort((a, b) => a.firstSeenAt.localeCompare(b.firstSeenAt));
    setApiKeys(sortedOldestFirst);
    if (sortedOldestFirst.length > 0) {
      setApiKeyId((prev) => {
        if (prev && sortedOldestFirst.some((k) => k.apiKeyId === prev)) return prev;
        return sortedOldestFirst[0]?.apiKeyId ?? "";
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
      const s = await fetchJson<ExpenditureSummary>(
        expenditureApiPath(`/api/expenditure/summary?${qSummary.toString()}`)
      );
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

  const refreshPersonal = useCallback(async () => {
    setError(null);
    try {
      await loadApiKeys();
      await loadSeries();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "새로고침에 실패했습니다");
    }
  }, [loadApiKeys, loadSeries]);

  const loadTeamRollup = useCallback(async () => {
    const tid = teamIdInput.trim();
    if (!tid) {
      setError("팀 ID를 입력하세요.");
      return;
    }
    setTeamLoading(true);
    setError(null);
    try {
      const userIds = await fetchTeamMemberUserIds(tid);
      if (userIds.length === 0) {
        setTeamRollup(null);
        setError(
          "팀 멤버를 불러오지 못했습니다. 단일 오리진(예: identity :3000)에서 지출을 열었는지, 팀 ID가 맞는지 확인하세요."
        );
        return;
      }
      const monthStart = currentMonthStartKst();
      const raw = await fetchJsonPost<{
        totalCostUsd: number;
        byUser: { userId: string; costUsd: number }[];
      }>(expenditureApiPath("/api/expenditure/team/month-rollup"), {
        userIds,
        monthStartDate: monthStart,
      });
      setTeamRollup({
        totalCostUsd: Number(raw.totalCostUsd),
        byUser: raw.byUser.map((r) => ({ userId: r.userId, costUsd: Number(r.costUsd) })),
      });
    } catch (e: unknown) {
      setTeamRollup(null);
      setError(e instanceof Error ? e.message : "팀 집계를 불러오지 못했습니다");
    } finally {
      setTeamLoading(false);
    }
  }, [teamIdInput]);

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

  const teamMemberChart = useMemo(
    () =>
      (teamRollup?.byUser ?? []).map((r) => ({
        user: r.userId.length > 10 ? `${r.userId.slice(0, 8)}…` : r.userId,
        usd: Number(r.costUsd),
      })),
    [teamRollup]
  );

  return (
    <div className="space-y-8">
      <header className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">지출</h1>
        <p className="text-sm text-muted-foreground">
          프로바이더와 API 키를 선택한 뒤 기간별 비용을 확인합니다. 표시 금액은 실제 결제가 아니라 사용 로그·단가 기준 추정(USD)입니다.
        </p>
      </header>

      <div
        className="rounded-lg border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-xs text-amber-950 dark:text-amber-100"
        role="note"
      >
        비결제 모니터링: 모든 수치는 API 호출 로그와 모델 단가로 계산된 시뮬레이션 지출이며, 집계·월 경계는 KST(Asia/Seoul) 기준입니다.
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <span className="text-sm text-muted-foreground">보기</span>
        <div className="inline-flex rounded-md border border-border p-0.5">
          <button
            type="button"
            className={`rounded px-3 py-1.5 text-sm ${viewMode === "personal" ? "bg-muted font-medium" : "text-muted-foreground"}`}
            onClick={() => setViewMode("personal")}
          >
            개인
          </button>
          <button
            type="button"
            className={`rounded px-3 py-1.5 text-sm ${viewMode === "team" ? "bg-muted font-medium" : "text-muted-foreground"}`}
            onClick={() => setViewMode("team")}
          >
            팀
          </button>
        </div>
        {viewMode === "personal" ? (
          <button
            type="button"
            className="ml-2 rounded-md border border-border bg-background px-3 py-1.5 text-sm hover:bg-muted"
            onClick={() => void refreshPersonal()}
          >
            새로고침
          </button>
        ) : null}
      </div>

      {viewMode === "team" ? (
        <section className="space-y-4 rounded-xl border border-border bg-card p-4 shadow-sm">
          <h2 className="text-sm font-medium">팀 당월 누적 (billing 집계)</h2>
          <p className="text-xs text-muted-foreground">
            팀 서비스에서 멤버 목록을 불러온 뒤, 해당 사용자들의 월별 지출을 합산합니다. 팀 API는 단일 오리진(identity 경유)에서만 호출됩니다.
          </p>
          <div className="flex flex-wrap items-end gap-2">
            <label className="flex flex-col gap-1 text-sm">
              <span className="text-muted-foreground">팀 ID</span>
              <input
                className="h-9 min-w-[220px] rounded-md border border-input bg-background px-2 text-sm"
                value={teamIdInput}
                onChange={(e) => setTeamIdInput(e.target.value)}
                placeholder="team-uuid"
              />
            </label>
            <button
              type="button"
              className="h-9 rounded-md bg-primary px-3 text-sm text-primary-foreground disabled:opacity-50"
              disabled={teamLoading}
              onClick={() => void loadTeamRollup()}
            >
              {teamLoading ? "불러오는 중…" : "집계"}
            </button>
          </div>
          <p className="text-xs text-muted-foreground">집계 월 시작일(KST): {currentMonthStartKst()}</p>
          {teamRollup ? (
            <div className="space-y-3">
              <p className="text-lg font-semibold tabular-nums">
                팀 당월 합계: ${teamRollup.totalCostUsd.toFixed(4)} USD
              </p>
              {teamMemberChart.length > 0 ? (
                <div className="h-72 w-full rounded-xl border border-border bg-card p-2">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={teamMemberChart}>
                      <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
                      <XAxis dataKey="user" tick={{ fontSize: 10 }} interval={0} angle={-25} textAnchor="end" height={64} />
                      <YAxis tick={{ fontSize: 11 }} width={56} />
                      <Tooltip formatter={formatUsdTooltip} />
                      <Bar dataKey="usd" fill="var(--color-chart-3)" radius={[4, 4, 0, 0]} />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              ) : (
                <p className="text-sm text-muted-foreground">이번 달 집계된 비용이 없습니다.</p>
              )}
            </div>
          ) : null}
        </section>
      ) : null}

      {viewMode === "personal" ? (
        <>
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
              <span className="text-muted-foreground">API 키 (이벤트 관측)</span>
              <select
                className="h-9 min-w-[240px] max-w-full rounded-md border border-input bg-background px-2 text-sm"
                value={apiKeyId}
                onChange={(e) => setApiKeyId(e.target.value)}
                disabled={apiKeys.length === 0}
              >
                {apiKeys.length === 0 ? (
                  <option value="">사용 데이터가 없습니다</option>
                ) : (
                  <optgroup label="사용 중 (이벤트로 관측된 키)">
                    {apiKeys.map((k) => (
                      <option key={`${k.provider}-${k.apiKeyId}`} value={k.apiKeyId}>
                        {k.apiKeyId.slice(0, 12)}… ({k.provider})
                      </option>
                    ))}
                  </optgroup>
                )}
              </select>
            </label>
            <div className="text-xs text-muted-foreground">
              기간: {range.from} ~ {range.to} (30일)
            </div>
          </div>

          {error ? (
            <div className="rounded-lg border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {error}
            </div>
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
                  <p className="text-xs text-muted-foreground">
                    월 예산: identity HTTP 계약이 없거나 미설정이면 표시되지 않습니다.
                  </p>
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

          {!loading && apiKeyId && daily.length === 0 && monthly.length === 0 && !error ? (
            <p className="text-sm text-muted-foreground">선택한 기간에 사용 데이터가 없습니다.</p>
          ) : null}

          {dailyChart.length > 0 ? (
            <section className="space-y-3">
              <h2 className="text-sm font-medium">일별 지출</h2>
              <div className="h-72 w-full rounded-xl border border-border bg-card p-2">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={dailyChart}>
                    <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
                    <XAxis dataKey="date" tick={{ fontSize: 11 }} />
                    <YAxis tick={{ fontSize: 11 }} width={56} />
                    <Tooltip formatter={formatUsdTooltip} />
                    <Bar dataKey="usd" fill="var(--color-chart-1)" radius={[4, 4, 0, 0]} />
                  </BarChart>
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
                    <Tooltip formatter={formatUsdTooltip} />
                    <Bar dataKey="usd" fill="var(--color-chart-2)" radius={[4, 4, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </section>
          ) : null}
        </>
      ) : null}
    </div>
  );
}
