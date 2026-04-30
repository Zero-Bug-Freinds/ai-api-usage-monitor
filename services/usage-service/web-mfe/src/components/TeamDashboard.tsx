"use client";

import * as React from "react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ComposedChart,
  Legend,
  Line,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import {
  Button,
  Input,
  Label,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@ai-usage/ui";
import { formatKstIsoDate, addKstDays } from "@web/lib/usage/kst-dates";
import { formatRequestCount, formatTokenCount, formatUsd, toNumber } from "@web/lib/usage/format";

const STORAGE_KEY = "team-usage-dashboard:v2";
const PROVIDER_ALL = "__ALL__";
const TEAM_WEB_PREFIX = "/teams";

type TeamDashboardProps = {
  teamId: string;
  onSelectUser: (userId: string) => void;
};

type UsageSeriesPoint = {
  bucketLabel: string;
  requestCount: number;
  errorCount: number;
  inputTokens: number;
  estimatedCost: number | string;
};

type ModelAgg = {
  model: string;
  provider: string;
  requestCount: number;
  inputTokens: number;
  estimatedReasoningTokens: number;
  outputTokens: number;
};

type BffResponse = {
  summary?: {
    totalRequests?: number;
    totalErrors?: number;
    totalInputTokens?: number;
    totalEstimatedCost?: number | string;
  };
  usageSeries?: UsageSeriesPoint[];
  usageSeriesUnit?: "HOUR" | "DAY" | "MONTH";
  byModel?: ModelAgg[];
  memberProfiles?: Array<{ userId: string; displayName?: string; role?: string }>;
  enrichment?: { partial?: boolean; warnings?: string[] };
};

type TeamSummary = { id: string; name: string; createdAt?: string };
type TeamApiKey = { id: number; alias: string; provider: string; createdAt: string };

type PeriodMode = "today" | "7d" | "30d" | "custom";

type StoredFilters = {
  provider: string;
  periodMode: PeriodMode;
  from: string;
  to: string;
  teamId: string;
  apiKeyId: string;
};

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const AnyLegend = Legend as any;

function teamApiUrl(path: string): string {
  const normalized = path.startsWith("/") ? path : `/${path}`;
  if (typeof window === "undefined") {
    return `${TEAM_WEB_PREFIX}${normalized}`;
  }
  return `${window.location.origin}${TEAM_WEB_PREFIX}${normalized}`;
}

function usageBffBase(): string {
  return (process.env.NEXT_PUBLIC_USAGE_BFF_BASE_URL ?? "").replace(/\/+$/, "") || "";
}

function buildUsageDashboardQuery(params: Record<string, string | undefined | null>): string {
  const sp = new URLSearchParams();
  sp.set("mode", "TEAM_TOTAL");
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === "") continue;
    sp.set(k, String(v));
  }
  return sp.toString();
}

function presetRange(mode: PeriodMode, todayKst: string): { from: string; to: string } {
  switch (mode) {
    case "today":
      return { from: todayKst, to: todayKst };
    case "7d":
      return { from: addKstDays(todayKst, -6), to: todayKst };
    case "30d":
      return { from: addKstDays(todayKst, -29), to: todayKst };
    default:
      return { from: todayKst, to: todayKst };
  }
}

function readStoredFilters(_todayKst: string): Partial<StoredFilters> {
  if (typeof localStorage === "undefined") return {};
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return {};
    return JSON.parse(raw) as Partial<StoredFilters>;
  } catch {
    return {};
  }
}

function writeStoredFilters(v: StoredFilters) {
  if (typeof localStorage === "undefined") return;
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(v));
  } catch {
    /* ignore */
  }
}

function kstDaysInclusive(fromIso: string, toIso: string): number {
  const a = Date.parse(`${fromIso}T12:00:00+09:00`);
  const b = Date.parse(`${toIso}T12:00:00+09:00`);
  if (Number.isNaN(a) || Number.isNaN(b) || b < a) return 1;
  return Math.floor((b - a) / 86_400_000) + 1;
}

function kpiPeriodPrefix(fromIso: string, toIso: string, todayIso: string): string {
  const n = kstDaysInclusive(fromIso, toIso);
  if (n === 1 && fromIso === todayIso && toIso === todayIso) return "오늘의";
  if (n === 7) return "7일간";
  if (n === 30) return "30일간";
  return `${n}일간`;
}

type MainRow = {
  label: string;
  requestCount: number;
  successRate: number;
  errorRate: number;
};

function mainChartTitle(unit: string | undefined): string {
  if (unit === "HOUR") return "시간별 요청·성공률·오류율";
  if (unit === "DAY") return "일별 요청·성공률·오류율";
  if (unit === "MONTH") return "월별 요청·성공률·오류율";
  return "요청·성공률·오류율";
}

function stabilityRateDomain(rows: MainRow[]): [number, number] {
  let max = 0;
  for (const r of rows) {
    max = Math.max(max, r.successRate, r.errorRate);
  }
  const hi = Math.max(100, Math.ceil(max * 1.1));
  return [0, hi];
}

function hashToUint(str: string): number {
  let h = 2166136261;
  for (let i = 0; i < str.length; i++) {
    h ^= str.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

const MODEL_PALETTE = ["#9a3412", "#c2410c", "#ea580c", "#F97316", "#fb923c", "#fdba74", "#64748b"];

function colorForModel(model: string, provider: string): string {
  const key = `${provider}::${model}`;
  const idx = hashToUint(key) % MODEL_PALETTE.length;
  return MODEL_PALETTE[idx] ?? "#94a3b8";
}

function truncateModelLabel(model: string, max = 36): string {
  if (model.length <= max) return model;
  return `${model.slice(0, max - 1)}…`;
}

export default function TeamDashboard({ teamId: routeTeamId, onSelectUser }: TeamDashboardProps) {
  const todayKst = formatKstIsoDate();
  const stored = readStoredFilters(todayKst);

  const [dashProvider, setDashProvider] = React.useState(stored.provider ?? PROVIDER_ALL);
  const [periodMode, setPeriodMode] = React.useState<PeriodMode>((stored.periodMode as PeriodMode) ?? "today");
  const [customFrom, setCustomFrom] = React.useState(stored.from ?? todayKst);
  const [customTo, setCustomTo] = React.useState(stored.to ?? todayKst);

  const range = React.useMemo(() => {
    if (periodMode === "custom") {
      return { from: customFrom, to: customTo };
    }
    return presetRange(periodMode, todayKst);
  }, [periodMode, customFrom, customTo, todayKst]);

  const [teams, setTeams] = React.useState<TeamSummary[]>([]);
  const [teamsErr, setTeamsErr] = React.useState<string | null>(null);
  const [selectedTeamId, setSelectedTeamId] = React.useState(stored.teamId || routeTeamId || "");

  const [apiKeys, setApiKeys] = React.useState<TeamApiKey[]>([]);
  const [keysLoading, setKeysLoading] = React.useState(false);
  const [selectedApiKeyId, setSelectedApiKeyId] = React.useState(stored.apiKeyId ?? "");

  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [data, setData] = React.useState<BffResponse | null>(null);
  const [refresh, setRefresh] = React.useState(0);

  React.useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await fetch(teamApiUrl("/api/team/v1/me/teams"), {
          credentials: "include",
          headers: { Accept: "application/json" },
        });
        const json = (await res.json()) as { success?: boolean; data?: unknown };
        if (!res.ok || !json.success || !Array.isArray(json.data)) {
          setTeamsErr(json && typeof json === "object" && "message" in json ? String((json as { message?: unknown }).message) : "팀 목록 실패");
          return;
        }
        const list = (json.data as unknown[])
          .map((item): TeamSummary | null => {
            if (!item || typeof item !== "object") return null;
            const o = item as Record<string, unknown>;
            if (typeof o.id !== "string" && typeof o.id !== "number") return null;
            if (typeof o.name !== "string") return null;
            return {
              id: String(o.id),
              name: o.name,
              createdAt: typeof o.createdAt === "string" ? o.createdAt : undefined,
            };
          })
          .filter((x): x is TeamSummary => x !== null);
        if (cancelled) return;
        setTeams(list);
        setTeamsErr(null);
        setSelectedTeamId((prev) => {
          if (prev && list.some((t) => t.id === prev)) return prev;
          if (routeTeamId && list.some((t) => t.id === routeTeamId)) return routeTeamId;
          return list[0]?.id ?? "";
        });
      } catch {
        if (!cancelled) setTeamsErr("팀 목록을 불러오지 못했습니다");
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [routeTeamId]);

  React.useEffect(() => {
    if (!selectedTeamId) {
      setApiKeys([]);
      return;
    }
    let cancelled = false;
    setKeysLoading(true);
    fetch(teamApiUrl(`/api/team/v1/teams/${encodeURIComponent(selectedTeamId)}/api-keys`), {
      credentials: "include",
      headers: { Accept: "application/json" },
    })
      .then(async (r) => {
        const json = (await r.json()) as { success?: boolean; data?: unknown };
        if (!r.ok || !json.success || !Array.isArray(json.data)) {
          return [];
        }
        const rows = (json.data as unknown[])
          .map((item): TeamApiKey | null => {
            if (!item || typeof item !== "object") return null;
            const o = item as Record<string, unknown>;
            if (typeof o.id !== "number") return null;
            if (typeof o.createdAt !== "string") return null;
            if (typeof o.alias !== "string") return null;
            if (typeof o.provider !== "string") return null;
            return {
              id: o.id,
              alias: o.alias,
              provider: o.provider,
              createdAt: o.createdAt,
            };
          })
          .filter((x): x is TeamApiKey => x !== null);
        return rows.sort((a, b) => a.createdAt.localeCompare(b.createdAt));
      })
      .then((sorted) => {
        if (cancelled) return;
        setApiKeys(sorted);
        setSelectedApiKeyId((prev) => {
          if (prev && sorted.some((k) => String(k.id) === prev)) return prev;
          return sorted[0] ? String(sorted[0].id) : "";
        });
      })
      .catch(() => {
        if (!cancelled) setApiKeys([]);
      })
      .finally(() => {
        if (!cancelled) setKeysLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [selectedTeamId]);

  const effectiveTeamId = selectedTeamId || routeTeamId;

  React.useEffect(() => {
    writeStoredFilters({
      provider: dashProvider,
      periodMode,
      from: range.from,
      to: range.to,
      teamId: effectiveTeamId,
      apiKeyId: selectedApiKeyId,
    });
  }, [dashProvider, periodMode, range.from, range.to, effectiveTeamId, selectedApiKeyId]);

  React.useEffect(() => {
    if (!effectiveTeamId) {
      setData(null);
      return;
    }
    const base = usageBffBase() || (typeof window !== "undefined" ? window.location.origin : "");
    if (!base) {
      setError("사용량 API 베이스 URL을 확인할 수 없습니다");
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(null);
    const q = buildUsageDashboardQuery({
      teamId: effectiveTeamId,
      from: range.from,
      to: range.to,
      provider: dashProvider === PROVIDER_ALL ? undefined : dashProvider,
      apiKeyId: selectedApiKeyId || undefined,
    });
    fetch(`${base}/api/v1/usage/bff/dashboard?${q}`, {
      credentials: "include",
      headers: { Accept: "application/json" },
    })
      .then(async (r) => {
        if (!r.ok) throw new Error(`HTTP ${r.status}`);
        return (await r.json()) as BffResponse;
      })
      .then((body) => {
        if (cancelled) return;
        setData(body);
        const first = body.memberProfiles?.[0]?.userId;
        if (first) onSelectUser(first);
      })
      .catch((e: Error) => {
        if (!cancelled) setError(e.message);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [effectiveTeamId, range.from, range.to, dashProvider, selectedApiKeyId, refresh, onSelectUser]);

  const mainRows: MainRow[] = React.useMemo(() => {
    const series = data?.usageSeries ?? [];
    return series.map((row) => {
      const successCount = Math.max(0, row.requestCount - row.errorCount);
      const successRate = row.requestCount > 0 ? (100 * successCount) / row.requestCount : 0;
      const errorRate = row.requestCount > 0 ? (100 * row.errorCount) / row.requestCount : 0;
      const label =
        data?.usageSeriesUnit === "HOUR" ? row.bucketLabel.replace(":00", "시") : row.bucketLabel;
      return { label, requestCount: row.requestCount, successRate, errorRate };
    });
  }, [data?.usageSeries, data?.usageSeriesUnit]);

  const rateDomain = React.useMemo(() => stabilityRateDomain(mainRows), [mainRows]);

  const summary = data?.summary;
  const rangeRequests = summary?.totalRequests ?? 0;
  const rangeErrors = summary?.totalErrors ?? 0;
  const rangeTokens = summary?.totalInputTokens ?? 0;
  const rangeCost = toNumber(summary?.totalEstimatedCost);
  const rangeSuccess = rangeRequests > 0 ? Math.max(0, rangeRequests - rangeErrors) : 0;
  const successRatePercent = rangeRequests > 0 ? (100 * rangeSuccess) / rangeRequests : 0;
  const periodPrefix = kpiPeriodPrefix(range.from, range.to, todayKst);
  const errorSubStyle = rangeErrors >= 1 ? "text-red-500" : "text-foreground";

  const pieData = React.useMemo(() => {
    const models = data?.byModel ?? [];
    const totalReq = models.reduce((s, m) => s + m.requestCount, 0);
    if (totalReq <= 0) return [];
    return models
      .filter((m) => m.requestCount > 0)
      .map((m) => ({
        name: truncateModelLabel(m.model),
        fullName: m.model,
        provider: m.provider,
        value: m.requestCount,
        percent: m.requestCount / totalReq,
      }));
  }, [data?.byModel]);

  const barModelData = React.useMemo(() => {
    const models = data?.byModel ?? [];
    return [...models]
      .filter((m) => m.requestCount > 0)
      .sort((a, b) => b.requestCount - a.requestCount)
      .slice(0, 15)
      .map((m) => ({
        label: truncateModelLabel(m.model, 28),
        fullName: m.model,
        provider: m.provider,
        requests: m.requestCount,
      }));
  }, [data?.byModel]);

  const hasMainData =
    (summary && summary.totalRequests && summary.totalRequests > 0) ||
    (data?.usageSeries && data.usageSeries.some((r) => r.requestCount > 0));

  if (!routeTeamId && !effectiveTeamId && teams.length === 0 && teamsErr) {
    return <p className="text-sm text-destructive">{teamsErr}</p>;
  }

  return (
    <div className="w-full min-h-full pb-6">
      <header className="mb-6 flex flex-col gap-4 border-b border-border pb-6 sm:flex-row sm:items-start sm:justify-between">
        <div className="space-y-2">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-2xl font-semibold tracking-tight">팀 사용량</h1>
            <span className="rounded-full border border-border bg-muted/50 px-2 py-0.5 text-xs font-medium text-muted-foreground">
              팀
            </span>
          </div>
          <p className="text-sm text-muted-foreground">
            자세한 비용 내역은 &apos;지출&apos; 메뉴를 통해 확인하세요. 집계 구간은 KST 기준입니다.
          </p>
        </div>
        <Button type="button" variant="outline" size="sm" disabled={loading} onClick={() => setRefresh((n) => n + 1)}>
          새로고침
        </Button>
      </header>

      <div className="mb-6 flex flex-col gap-4 lg:flex-row lg:flex-wrap lg:items-end">
        <div className="space-y-2 sm:w-44">
          <Label htmlFor="team-dash-period">기간</Label>
          <Select
            value={periodMode}
            onValueChange={(v) => {
              const next = v as PeriodMode;
              setPeriodMode(next);
              if (next !== "custom") {
                const pr = presetRange(next, todayKst);
                setCustomFrom(pr.from);
                setCustomTo(pr.to);
              }
            }}
          >
            <SelectTrigger id="team-dash-period">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="today">오늘</SelectItem>
              <SelectItem value="7d">최근 7일</SelectItem>
              <SelectItem value="30d">최근 30일</SelectItem>
              <SelectItem value="custom">사용자 지정</SelectItem>
            </SelectContent>
          </Select>
        </div>

        {periodMode === "custom" ? (
          <div className="flex flex-wrap gap-3">
            <div className="space-y-2">
              <Label htmlFor="team-from">시작</Label>
              <Input id="team-from" type="date" value={customFrom} onChange={(e) => setCustomFrom(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="team-to">종료</Label>
              <Input id="team-to" type="date" value={customTo} onChange={(e) => setCustomTo(e.target.value)} />
            </div>
          </div>
        ) : null}

        <div className="space-y-2 sm:w-52">
          <Label>팀</Label>
          <Select value={effectiveTeamId} onValueChange={setSelectedTeamId}>
            <SelectTrigger>
              <SelectValue placeholder="팀 선택" />
            </SelectTrigger>
            <SelectContent>
              {teams.map((t) => (
                <SelectItem key={t.id} value={t.id}>
                  {t.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-2 sm:w-52">
          <Label>API Key</Label>
          <Select
            value={selectedApiKeyId}
            onValueChange={setSelectedApiKeyId}
            disabled={keysLoading || apiKeys.length === 0}
          >
            <SelectTrigger>
              <SelectValue placeholder={keysLoading ? "불러오는 중…" : "키 선택"} />
            </SelectTrigger>
            <SelectContent>
              {apiKeys.map((k) => (
                <SelectItem key={k.id} value={String(k.id)}>
                  {k.alias} ({k.provider})
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-2 sm:w-52">
          <Label htmlFor="team-dash-provider">공급사</Label>
          <Select value={dashProvider} onValueChange={setDashProvider}>
            <SelectTrigger id="team-dash-provider">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={PROVIDER_ALL}>전체</SelectItem>
              <SelectItem value="GOOGLE">Gemini (Google)</SelectItem>
              <SelectItem value="OPENAI">OpenAI</SelectItem>
              <SelectItem value="ANTHROPIC">Anthropic</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>

      {teamsErr ? <p className="mb-4 text-sm text-amber-700">{teamsErr}</p> : null}
      {error ? (
        <p className="mb-6 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {error}
        </p>
      ) : null}

      {loading ? <p className="mb-8 text-sm text-muted-foreground">불러오는 중…</p> : null}

      {!loading && !error && effectiveTeamId ? (
        <>
          <section className="mb-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <div className="rounded-lg border border-border bg-card p-4 shadow-sm">
              <p className="text-xs font-medium text-muted-foreground">{periodPrefix} 총 비용 (USD)</p>
              <p className="mt-1 text-2xl font-semibold tabular-nums">{formatUsd(rangeCost)}</p>
            </div>
            <div className="rounded-lg border border-border bg-card p-4 shadow-sm">
              <p className="text-xs font-medium text-muted-foreground">{periodPrefix} 총 요청 수</p>
              <p className="mt-1 text-2xl font-semibold tabular-nums">{formatRequestCount(rangeRequests)}</p>
            </div>
            <div className="rounded-lg border border-border bg-card p-4 shadow-sm">
              <p className="text-xs font-medium text-muted-foreground">{periodPrefix} 총 입력 토큰</p>
              <p className="mt-1 text-2xl font-semibold tabular-nums">{formatTokenCount(rangeTokens)}</p>
            </div>
            <div className="rounded-lg border border-border bg-card p-4 shadow-sm">
              <p className="text-xs font-medium text-muted-foreground">{periodPrefix} 성공률</p>
              <p className="mt-1 text-2xl font-semibold tabular-nums">{successRatePercent.toFixed(1)}%</p>
              <p className={`mt-2 text-xs tabular-nums ${errorSubStyle}`}>
                오류 {rangeErrors.toLocaleString("en-US")}건 / 총 {rangeRequests.toLocaleString("en-US")}건
              </p>
            </div>
          </section>

          {!hasMainData ? (
            <p className="mb-10 text-center text-sm text-muted-foreground">선택한 기간·필터에 대한 사용 데이터가 없습니다</p>
          ) : null}

          <section className="mb-8 w-full min-w-0 rounded-lg border border-border p-4 shadow-sm">
            <h2 className="mb-4 text-lg font-medium">{mainChartTitle(data?.usageSeriesUnit)}</h2>
            <div className="h-[380px] min-h-[380px] w-full min-w-0">
              <ResponsiveContainer width="100%" height="100%">
                <ComposedChart data={mainRows}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                  <XAxis dataKey="label" tick={{ fontSize: 11 }} />
                  <YAxis
                    yAxisId="left"
                    tick={{ fontSize: 11 }}
                    label={{ value: "요청 수 (건)", angle: -90, position: "insideLeft", offset: 2 }}
                  />
                  <YAxis
                    yAxisId="right"
                    orientation="right"
                    domain={rateDomain}
                    tick={{ fontSize: 11 }}
                    tickFormatter={(v) => `${Number(v).toFixed(1)}%`}
                    label={{ value: "성공/오류율 (%)", angle: 90, position: "insideRight", offset: 2 }}
                  />
                  <Tooltip
                    formatter={(value: number | string, name: string) => [
                      typeof value === "number" && name.includes("률") ? `${value.toFixed(1)}%` : value,
                      name,
                    ]}
                  />
                  <AnyLegend />
                  <Bar yAxisId="left" dataKey="requestCount" name="총 요청 수" fill="#a3a3a3" radius={[4, 4, 0, 0]} />
                  <Line
                    yAxisId="right"
                    type="monotone"
                    dataKey="successRate"
                    name="성공률"
                    stroke="#10b981"
                    strokeWidth={2}
                    dot={false}
                  />
                  <Line
                    yAxisId="right"
                    type="monotone"
                    dataKey="errorRate"
                    name="오류율"
                    stroke="#f43f5e"
                    strokeWidth={2}
                    dot={false}
                  />
                </ComposedChart>
              </ResponsiveContainer>
            </div>
          </section>

          <div className="mb-8 grid min-w-0 gap-6 lg:grid-cols-2">
            <section className="min-w-0 rounded-lg border border-border p-4 shadow-sm">
              <h2 className="mb-4 text-lg font-medium">모델별 요청 비중</h2>
              <div className="flex min-h-[300px] flex-col items-center gap-4 sm:flex-row">
                <div className="h-[260px] w-full max-w-[260px] shrink-0">
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie
                        data={pieData.length > 0 ? pieData : [{ name: "—", value: 1, fullName: "__empty__", provider: "GOOGLE", percent: 1 }]}
                        dataKey="value"
                        nameKey="name"
                        cx="50%"
                        cy="50%"
                        innerRadius="52%"
                        outerRadius="78%"
                        paddingAngle={2}
                        isAnimationActive={pieData.length > 0}
                        label={pieData.length === 0 ? false : ({ index }) => String((index ?? 0) + 1)}
                      >
                        {(pieData.length > 0 ? pieData : [{ name: "—", fullName: "__empty__", provider: "GOOGLE", value: 1 }]).map((entry, i) => (
                          <Cell
                            key={`cell-${entry.fullName}-${i}`}
                            fill={
                              pieData.length === 0 ? "var(--border)" : colorForModel(entry.fullName ?? "", entry.provider ?? "")
                            }
                            fillOpacity={pieData.length === 0 ? 0.35 : 1}
                          />
                        ))}
                      </Pie>
                      <Tooltip formatter={(v: number) => formatRequestCount(v)} />
                    </PieChart>
                  </ResponsiveContainer>
                </div>
                <ul className="max-h-[220px] w-full flex-1 space-y-1 overflow-auto text-xs">
                  {pieData.map((p) => (
                    <li key={p.fullName} className="flex justify-between gap-2">
                      <span className="truncate text-muted-foreground">{p.name}</span>
                      <span className="tabular-nums">{(p.percent * 100).toFixed(1)}%</span>
                    </li>
                  ))}
                </ul>
              </div>
            </section>

            <section className="min-w-0 rounded-lg border border-border p-4 shadow-sm">
              <h2 className="mb-4 text-lg font-medium">모델별 요청 수 (상위)</h2>
              <div className="h-[300px] min-h-[300px] w-full min-w-0">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={barModelData} layout="vertical" margin={{ left: 8, right: 8 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                    <XAxis type="number" tick={{ fontSize: 11 }} />
                    <YAxis type="category" dataKey="label" width={100} tick={{ fontSize: 10 }} />
                    <Tooltip />
                    <Bar dataKey="requests" name="요청 수" fill="#64748b" radius={[0, 4, 4, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </section>
          </div>

          {data?.enrichment?.partial ? (
            <div className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-900">
              프로필 일부 결합 실패: {(data.enrichment.warnings ?? []).join(", ")}
            </div>
          ) : null}
        </>
      ) : null}
    </div>
  );
}
