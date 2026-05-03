"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

import { currentMonthRangeKst, currentMonthStartKst, rangeLastDays } from "@/lib/expenditure/dates";
import { formatUsd, formatUsdTooltip } from "@/lib/expenditure/money";
import type {
  AiProviderCode,
  DailyPoint,
  ExpenditureSummary,
  MonthlyBudgetStatus,
  MonthlyPoint,
  TeamMonthRollup,
} from "@/lib/expenditure/types";

type IdentityExternalKeyProvider = "GEMINI" | "OPENAI" | "ANTHROPIC";

type RegisteredExternalKey = {
  id: number | string;
  provider: IdentityExternalKeyProvider;
  alias: string;
};

const PROVIDERS: { value: AiProviderCode; label: string }[] = [
  { value: "GOOGLE", label: "Gemini (GOOGLE)" },
  { value: "OPENAI", label: "OpenAI" },
  { value: "ANTHROPIC", label: "Anthropic" },
];

const MAX_RANGE_DAYS = 400;

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

function mapBillingProviderToIdentity(p: AiProviderCode): IdentityExternalKeyProvider {
  if (p === "GOOGLE") return "GEMINI";
  return p;
}

function parseRegisteredExternalKeys(json: unknown): RegisteredExternalKey[] {
  if (typeof json === "object" && json !== null && Array.isArray((json as { data?: unknown }).data)) {
    return parseRegisteredExternalKeys((json as { data: unknown }).data);
  }
  if (!Array.isArray(json)) return [];
  const out: RegisteredExternalKey[] = [];
  for (const x of json) {
    if (typeof x !== "object" || x === null) continue;
    const o = x as Record<string, unknown>;
    const id = o.id;
    const prov = o.provider;
    const alias = o.alias;
    const idOk = typeof id === "number" || (typeof id === "string" && id.length > 0);
    if (!idOk) continue;
    if (prov !== "GEMINI" && prov !== "OPENAI" && prov !== "ANTHROPIC") continue;
    if (typeof alias !== "string") continue;
    out.push({ id, provider: prov, alias });
  }
  return out;
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

async function fetchTeamMemberUserIds(teamId: string): Promise<string[]> {
  const res = await fetch(`/api/team/v1/teams/${encodeURIComponent(teamId)}/members`, {
    method: "GET",
    credentials: "include",
    headers: { Accept: "application/json" },
    cache: "no-store",
  });
  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(text || `팀 멤버 조회 실패 (HTTP ${res.status})`);
  }
  const json: unknown = await res.json();
  if (typeof json !== "object" || json === null) return [];
  const rec = json as Record<string, unknown>;
  if (rec.success !== true || !Array.isArray(rec.data)) return [];
  return rec.data.filter((x): x is string => typeof x === "string");
}

type MyTeam = { id: string; name: string };

async function fetchMyTeams(): Promise<MyTeam[]> {
  const res = await fetch("/api/team/v1/me/teams", {
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

  return rec.data
    .filter((x): x is Record<string, unknown> => typeof x === "object" && x !== null)
    .map((x) => ({ id: String(x.id ?? ""), name: String(x.name ?? "") }))
    .filter((t) => t.id.length > 0);
}

export function ExpenditureDashboard() {
  const [viewMode, setViewMode] = useState<"personal" | "team">("personal");
  const [provider, setProvider] = useState<AiProviderCode>("GOOGLE");
  const [registeredKeys, setRegisteredKeys] = useState<RegisteredExternalKey[]>([]);
  const [keysLoadError, setKeysLoadError] = useState<string | null>(null);
  const [apiKeyId, setApiKeyId] = useState<string>("");
  const [rangePreset, setRangePreset] = useState<"last7" | "last30" | "last90" | "thisMonth" | "custom">("last30");
  const [customFrom, setCustomFrom] = useState<string>(() => rangeLastDays(30).from);
  const [customTo, setCustomTo] = useState<string>(() => rangeLastDays(30).to);
  const [summary, setSummary] = useState<ExpenditureSummary | null>(null);
  const [monthlyBudget, setMonthlyBudget] = useState<MonthlyBudgetStatus | null>(null);
  const [daily, setDaily] = useState<DailyPoint[]>([]);
  const [monthly, setMonthly] = useState<MonthlyPoint[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [meUserId, setMeUserId] = useState<string | null>(null);

  const [teams, setTeams] = useState<MyTeam[]>([]);
  const [selectedTeamId, setSelectedTeamId] = useState("");
  const [teamRollup, setTeamRollup] = useState<TeamMonthRollup | null>(null);
  const [teamLoading, setTeamLoading] = useState(false);
  const [teamsLoading, setTeamsLoading] = useState(false);
  const [teamMonth, setTeamMonth] = useState<string>(() => currentMonthStartKst().slice(0, 7));

  const range = useMemo(() => {
    if (rangePreset === "last7") return rangeLastDays(7);
    if (rangePreset === "last30") return rangeLastDays(30);
    if (rangePreset === "last90") return rangeLastDays(90);
    if (rangePreset === "thisMonth") {
      return currentMonthRangeKst();
    }
    return { from: customFrom, to: customTo };
  }, [customFrom, customTo, rangePreset]);

  const rangeUi = useMemo(() => {
    const iso = /^\d{4}-\d{2}-\d{2}$/;
    if (!iso.test(range.from) || !iso.test(range.to)) {
      return { ok: false as const, message: "날짜 형식이 올바르지 않습니다 (YYYY-MM-DD)" };
    }
    const fromD = new Date(`${range.from}T00:00:00Z`);
    const toD = new Date(`${range.to}T00:00:00Z`);
    if (Number.isNaN(fromD.getTime()) || Number.isNaN(toD.getTime())) {
      return { ok: false as const, message: "날짜 값이 올바르지 않습니다" };
    }
    if (fromD.getTime() > toD.getTime()) {
      return { ok: false as const, message: "from 날짜가 to 날짜보다 이후입니다" };
    }
    const diffDays = Math.floor((toD.getTime() - fromD.getTime()) / (24 * 60 * 60 * 1000)) + 1;
    if (diffDays > MAX_RANGE_DAYS) {
      return { ok: false as const, message: `최대 ${MAX_RANGE_DAYS}일 범위까지 조회할 수 있습니다` };
    }
    return { ok: true as const, days: diffDays };
  }, [range.from, range.to]);

  const loadRegisteredKeys = useCallback(async () => {
    try {
      const raw = await fetchJson<unknown>(expenditureApiPath("/api/expenditure/registered-keys"));
      setRegisteredKeys(parseRegisteredExternalKeys(raw));
      setKeysLoadError(null);
    } catch (e: unknown) {
      setRegisteredKeys([]);
      setKeysLoadError(e instanceof Error ? e.message : "등록된 API 키 목록을 불러오지 못했습니다");
    }
  }, []);

  useEffect(() => {
    void loadRegisteredKeys();
  }, [loadRegisteredKeys]);

  const keysForProvider = useMemo(() => {
    const want = mapBillingProviderToIdentity(provider);
    return registeredKeys
      .filter((k) => k.provider === want)
      .slice()
      .sort((a, b) => a.alias.localeCompare(b.alias, "ko"));
  }, [registeredKeys, provider]);

  useEffect(() => {
    if (keysForProvider.length === 0) {
      setApiKeyId("");
      return;
    }
    setApiKeyId((prev) => {
      const ids = new Set(keysForProvider.map((k) => String(k.id)));
      if (prev && ids.has(prev)) return prev;
      return String(keysForProvider[0]?.id ?? "");
    });
  }, [keysForProvider]);

  useEffect(() => {
    void fetchJson<{ userId: string }>(expenditureApiPath("/api/expenditure/me"))
      .then((r) => setMeUserId(typeof r.userId === "string" && r.userId.length > 0 ? r.userId : null))
      .catch(() => setMeUserId(null));
  }, []);

  const loadMonthlyBudget = useCallback(async () => {
    const monthFrom = currentMonthStartKst();
    const monthTo = rangeLastDays(1).to;
    try {
      const q = new URLSearchParams({ from: monthFrom, to: monthTo });
      const b = await fetchJson<MonthlyBudgetStatus>(
        expenditureApiPath(`/api/expenditure/monthly-budget-status?${q.toString()}`)
      );
      setMonthlyBudget(b);
    } catch {
      setMonthlyBudget(null);
    }
  }, []);

  useEffect(() => {
    if (viewMode !== "personal") return;
    void loadMonthlyBudget();
  }, [loadMonthlyBudget, viewMode]);

  const loadSeries = useCallback(async () => {
    if (!apiKeyId) {
      setSummary(null);
      setDaily([]);
      setMonthly([]);
      return;
    }
    if (!rangeUi.ok) {
      setSummary(null);
      setDaily([]);
      setMonthly([]);
      setError(rangeUi.message);
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
  }, [apiKeyId, provider, range.from, range.to, rangeUi]);

  useEffect(() => {
    void loadSeries();
  }, [loadSeries]);

  const refreshPersonal = useCallback(async () => {
    setError(null);
    try {
      await loadMonthlyBudget();
      await loadRegisteredKeys();
      await loadSeries();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "새로고침에 실패했습니다");
    }
  }, [loadMonthlyBudget, loadRegisteredKeys, loadSeries]);

  const loadTeams = useCallback(async () => {
    setTeamsLoading(true);
    setError(null);
    try {
      const list = await fetchMyTeams();
      setTeams(list);
      setSelectedTeamId((prev) => {
        if (prev && list.some((t) => t.id === prev)) return prev;
        return list[0]?.id ?? "";
      });
    } catch (e: unknown) {
      setTeams([]);
      setSelectedTeamId("");
      setError(e instanceof Error ? e.message : "내 팀 목록을 불러오지 못했습니다");
    } finally {
      setTeamsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (viewMode !== "team") return;
    void loadTeams();
  }, [loadTeams, viewMode]);

  const loadTeamRollup = useCallback(async () => {
    const tid = selectedTeamId.trim();
    if (!tid) {
      setError("팀을 선택하세요.");
      return;
    }
    setTeamLoading(true);
    setError(null);
    try {
      const userIds = await fetchTeamMemberUserIds(tid);
      if (userIds.length === 0) {
        setTeamRollup(null);
        setError("팀에 멤버가 없습니다.");
        return;
      }
      const monthStart = `${teamMonth}-01`;
      const raw = await fetchJsonPost<{
        totalCostUsd: number;
        byUser: { userId: string; costUsd: number }[];
      }>(expenditureApiPath("/api/expenditure/team/month-rollup"), {
        teamId: tid,
        userIds,
        monthStartDate: monthStart,
      });
      setTeamRollup({
        totalCostUsd: Number(raw.totalCostUsd),
        byUser: raw.byUser.map((r) => ({ userId: r.userId, costUsd: Number(r.costUsd) })),
      });
    } catch (e: unknown) {
      setTeamRollup(null);
      setError(
        e instanceof Error
          ? e.message
          : "팀 집계를 불러오지 못했습니다 (단일 오리진/권한/게이트웨이 설정을 확인하세요)"
      );
    } finally {
      setTeamLoading(false);
    }
  }, [selectedTeamId, teamMonth]);

  const budgetUi = useMemo(() => {
    const total = monthlyBudget?.totalCostUsd ?? null;
    const budget = monthlyBudget?.monthlyBudgetUsd ?? null;
    if (total == null) return null;
    if (budget == null || budget <= 0) {
      return { totalCostUsd: total, monthlyBudgetUsd: null as number | null, remainingUsd: null as number | null, pct: null as number | null };
    }
    const remaining = Math.max(0, budget - total);
    const pct = Math.min(100, (total / budget) * 100);
    return { totalCostUsd: total, monthlyBudgetUsd: budget, remainingUsd: remaining, pct };
  }, [monthlyBudget]);

  const keyBudgetUi = useMemo(() => {
    const total = summary?.totalCostUsd ?? null;
    const budget = summary?.monthlyBudgetUsd ?? null;
    if (total == null) return null;
    if (budget == null || budget <= 0) {
      return { totalCostUsd: total, monthlyBudgetUsd: null as number | null, remainingUsd: null as number | null, pct: null as number | null };
    }
    const remaining = Math.max(0, budget - total);
    const pct = Math.min(100, (total / budget) * 100);
    return { totalCostUsd: total, monthlyBudgetUsd: budget, remainingUsd: remaining, pct };
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
        <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
          <span>사용자: {meUserId ?? "—"}</span>
          <span className="opacity-60">•</span>
          <span>
            예산 연동:{" "}
            {monthlyBudget?.monthlyBudgetUsd != null
              ? "표시됨"
              : monthlyBudget
                ? "미표시 (Identity 연동 꺼짐/미설정/예산 없음 가능)"
                : "—"}
          </span>
        </div>
      </header>

      {viewMode === "personal" && budgetUi ? (
        <section className="space-y-3 rounded-xl border border-border bg-card p-4 shadow-sm">
          <div className="flex flex-wrap items-end justify-between gap-4">
            <div className="space-y-1">
              <h2 className="text-sm font-medium text-muted-foreground">월 예산 대비 (전체)</h2>
              {budgetUi.monthlyBudgetUsd != null ? (
                <p className="text-xs text-muted-foreground">
                  이번 달 지출 {formatUsd(budgetUi.totalCostUsd)} / 월 예산 ${budgetUi.monthlyBudgetUsd.toFixed(2)} (잔여 $
                  {(budgetUi.remainingUsd ?? 0).toFixed(2)})
                </p>
              ) : (
                <p className="text-xs text-muted-foreground">
                  월 예산: identity HTTP 연동이 없거나 미설정이면 표시되지 않습니다.
                </p>
              )}
            </div>
            {budgetUi.pct != null ? (
              <div className="text-right">
                <p className="text-xs text-muted-foreground">진행률</p>
                <p className="text-lg font-semibold tabular-nums">{budgetUi.pct.toFixed(1)}%</p>
              </div>
            ) : null}
          </div>

          {budgetUi.pct != null ? (
            <div className="space-y-1">
              <div className="flex justify-between text-xs text-muted-foreground">
                <span>0%</span>
                <span>100%</span>
              </div>
              <div className="h-2 w-full overflow-hidden rounded-full bg-muted">
                <div
                  className="h-full rounded-full bg-primary transition-[width]"
                  style={{ width: `${Math.min(100, Math.max(0, budgetUi.pct))}%` }}
                />
              </div>
            </div>
          ) : null}
        </section>
      ) : null}

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

      {keysLoadError ? (
        <div className="rounded-lg border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">
          등록 키: {keysLoadError}
        </div>
      ) : null}

      {error ? (
        <div className="rounded-lg border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {error}
        </div>
      ) : null}

      {viewMode === "team" ? (
        <section className="space-y-4 rounded-xl border border-border bg-card p-4 shadow-sm">
          <h2 className="text-sm font-medium">팀 당월 누적 (billing 집계)</h2>
          <p className="text-xs text-muted-foreground">
            팀 서비스에서 멤버 목록을 불러온 뒤, 해당 사용자들의 월별 지출을 합산합니다. 팀 API는 단일 오리진(identity 경유)에서만 호출됩니다.
          </p>
          <div className="flex flex-wrap items-end gap-2">
            <label className="flex flex-col gap-1 text-sm">
              <span className="text-muted-foreground">내 팀</span>
              <select
                className="h-9 min-w-[240px] rounded-md border border-input bg-background px-2 text-sm"
                value={selectedTeamId}
                onChange={(e) => {
                  setSelectedTeamId(e.target.value);
                  setTeamRollup(null);
                }}
                disabled={teamsLoading || teams.length === 0}
              >
                {teamsLoading ? (
                  <option value="">불러오는 중…</option>
                ) : teams.length === 0 ? (
                  <option value="">표시할 팀이 없습니다</option>
                ) : (
                  teams.map((t) => (
                    <option key={t.id} value={t.id}>
                      {t.name || t.id}
                    </option>
                  ))
                )}
              </select>
            </label>
            <label className="flex flex-col gap-1 text-sm">
              <span className="text-muted-foreground">월</span>
              <input
                className="h-9 w-[160px] rounded-md border border-input bg-background px-2 text-sm"
                type="month"
                value={teamMonth}
                onChange={(e) => {
                  setTeamMonth(e.target.value);
                  setTeamRollup(null);
                }}
              />
            </label>
            <button
              type="button"
              className="h-9 rounded-md border border-border bg-background px-3 text-sm hover:bg-muted disabled:opacity-50"
              disabled={teamsLoading}
              onClick={() => void loadTeams()}
            >
              {teamsLoading ? "불러오는 중…" : "팀 목록 새로고침"}
            </button>
            <button
              type="button"
              className="h-9 rounded-md bg-primary px-3 text-sm text-primary-foreground disabled:opacity-50"
              disabled={teamLoading || teamsLoading || !selectedTeamId}
              onClick={() => void loadTeamRollup()}
            >
              {teamLoading ? "불러오는 중…" : "집계"}
            </button>
          </div>
          <p className="text-xs text-muted-foreground">집계 월 시작일: {teamMonth}-01 (KST 기준 월)</p>
          {teamRollup ? (
            <div className="space-y-3">
              <p className="text-lg font-semibold tabular-nums">
                팀 당월 합계: {formatUsd(teamRollup.totalCostUsd)} USD
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
              <span className="text-muted-foreground">등록된 API 키 (Identity)</span>
              <select
                className="h-9 min-w-[240px] max-w-full rounded-md border border-input bg-background px-2 text-sm"
                value={apiKeyId}
                onChange={(e) => setApiKeyId(e.target.value)}
                disabled={keysForProvider.length === 0}
              >
                {keysForProvider.length === 0 ? (
                  <option value="">사용 데이터가 없습니다</option>
                ) : (
                  <optgroup label="등록된 키">
                    {keysForProvider.map((k) => (
                      <option key={`${k.provider}-${k.id}`} value={String(k.id)}>
                        {k.alias}
                      </option>
                    ))}
                  </optgroup>
                )}
              </select>
            </label>
            <div className="flex flex-wrap items-end gap-3">
              <div className="space-y-1">
                <p className="text-xs text-muted-foreground">기간</p>
                <div className="flex flex-wrap items-center gap-1.5">
                  <button
                    type="button"
                    className={`h-8 rounded-md border border-border px-2 text-xs ${rangePreset === "last7" ? "bg-muted font-medium" : "bg-background hover:bg-muted"}`}
                    onClick={() => setRangePreset("last7")}
                  >
                    최근 7일
                  </button>
                  <button
                    type="button"
                    className={`h-8 rounded-md border border-border px-2 text-xs ${rangePreset === "last30" ? "bg-muted font-medium" : "bg-background hover:bg-muted"}`}
                    onClick={() => setRangePreset("last30")}
                  >
                    최근 30일
                  </button>
                  <button
                    type="button"
                    className={`h-8 rounded-md border border-border px-2 text-xs ${rangePreset === "last90" ? "bg-muted font-medium" : "bg-background hover:bg-muted"}`}
                    onClick={() => setRangePreset("last90")}
                  >
                    최근 90일
                  </button>
                  <button
                    type="button"
                    className={`h-8 rounded-md border border-border px-2 text-xs ${rangePreset === "thisMonth" ? "bg-muted font-medium" : "bg-background hover:bg-muted"}`}
                    onClick={() => setRangePreset("thisMonth")}
                  >
                    이번 달
                  </button>
                  <button
                    type="button"
                    className={`h-8 rounded-md border border-border px-2 text-xs ${rangePreset === "custom" ? "bg-muted font-medium" : "bg-background hover:bg-muted"}`}
                    onClick={() => setRangePreset("custom")}
                  >
                    직접 선택
                  </button>
                </div>
              </div>
              {rangePreset === "custom" ? (
                <div className="flex flex-wrap items-end gap-2">
                  <label className="flex flex-col gap-1 text-sm">
                    <span className="text-xs text-muted-foreground">from</span>
                    <input
                      className="h-8 w-[150px] rounded-md border border-input bg-background px-2 text-xs"
                      type="date"
                      value={customFrom}
                      onChange={(e) => setCustomFrom(e.target.value)}
                    />
                  </label>
                  <label className="flex flex-col gap-1 text-sm">
                    <span className="text-xs text-muted-foreground">to</span>
                    <input
                      className="h-8 w-[150px] rounded-md border border-input bg-background px-2 text-xs"
                      type="date"
                      value={customTo}
                      onChange={(e) => setCustomTo(e.target.value)}
                    />
                  </label>
                </div>
              ) : null}
              <div className="text-xs text-muted-foreground">
                {range.from} ~ {range.to}
                {rangeUi.ok ? ` (${rangeUi.days}일)` : ""}
              </div>
            </div>
          </div>

          {loading ? <p className="text-sm text-muted-foreground">불러오는 중…</p> : null}

          {summary && apiKeyId ? (
            <section className="space-y-3 rounded-xl border border-border bg-card p-4 shadow-sm">
              <h2 className="text-sm font-medium text-muted-foreground">요약</h2>
              <div className="flex flex-wrap items-baseline gap-6">
                <div>
                  <p className="text-xs text-muted-foreground">총 지출 (USD)</p>
                  <p className="text-2xl font-semibold tabular-nums">{formatUsd(summary.totalCostUsd)}</p>
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
            </section>
          ) : null}

          {apiKeyId && keyBudgetUi ? (
            <section className="space-y-3 rounded-xl border border-border bg-card p-4 shadow-sm">
              <div className="flex flex-wrap items-end justify-between gap-4">
                <div className="space-y-1">
                  <h2 className="text-sm font-medium text-muted-foreground">월 예산 대비 (선택한 키)</h2>
                  {keyBudgetUi.monthlyBudgetUsd != null ? (
                    <p className="text-xs text-muted-foreground">
                      이번 달 지출 {formatUsd(keyBudgetUi.totalCostUsd)} / 월 예산 ${keyBudgetUi.monthlyBudgetUsd.toFixed(2)} (잔여 $
                      {(keyBudgetUi.remainingUsd ?? 0).toFixed(2)})
                    </p>
                  ) : (
                    <p className="text-xs text-muted-foreground">
                      월 예산: identity HTTP 연동이 없거나 미설정이면 표시되지 않습니다.
                    </p>
                  )}
                </div>
                {keyBudgetUi.pct != null ? (
                  <div className="text-right">
                    <p className="text-xs text-muted-foreground">진행률</p>
                    <p className="text-lg font-semibold tabular-nums">{keyBudgetUi.pct.toFixed(1)}%</p>
                  </div>
                ) : null}
              </div>

              {keyBudgetUi.pct != null ? (
                <div className="space-y-1">
                  <div className="flex justify-between text-xs text-muted-foreground">
                    <span>0%</span>
                    <span>100%</span>
                  </div>
                  <div className="h-2 w-full overflow-hidden rounded-full bg-muted">
                    <div
                      className="h-full rounded-full bg-primary transition-[width]"
                      style={{ width: `${Math.min(100, Math.max(0, keyBudgetUi.pct))}%` }}
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
