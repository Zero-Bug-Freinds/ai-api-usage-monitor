"use client";

import { useEffect, useMemo, useState } from "react";
import { ChevronDown, ChevronUp } from "lucide-react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import {
  Label,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@ai-usage/ui";
import { formatRequestCount } from "@web/lib/usage/format";
import { formatKstIsoDate, addKstDays } from "@web/lib/usage/kst-dates";
import { teamUsageBffBase } from "../lib/team-usage-bff-base";

type TeamMemberUsageLogProps = {
  teamId: string;
  userId: string;
  isActive: boolean;
};

type PeriodMode = "today" | "7d" | "30d";
type TeamApiKey = { id: number; alias: string; provider: string; createdAt: string };
type TeamMemberProfile = { userId: string; displayName?: string; role?: string };
type ModelAgg = { model: string; provider: string; requestCount: number };
type BffResponse = {
  byModel?: ModelAgg[];
  memberProfiles?: TeamMemberProfile[];
};
type MemberSeries = { userId: string; displayName: string; requests: number };
type ChartRow = {
  label: string;
  model: string;
  totalRequests: number;
  isOthers: boolean;
  provider: string;
  [memberKey: string]: string | number | boolean;
};
type OthersRow = { model: string; provider: string; requests: number };

type TooltipPayloadEntry = {
  dataKey?: string;
  value?: number | string;
  payload?: ChartRow;
};

const PROVIDER_ALL = "__ALL__";
const TEAM_WEB_PREFIX = "/teams";
const MODEL_REQUESTS_TOP_N = 10;
const OTHERS_LABEL = "기타 (Others)";
const OTHERS_BAR_COLOR = "#94a3b8";
const MEMBER_PALETTE = [
  "#1d4ed8",
  "#0ea5e9",
  "#16a34a",
  "#f97316",
  "#9333ea",
  "#dc2626",
  "#0891b2",
  "#4f46e5",
  "#2563eb",
  "#0f766e",
];

const memberDashboardCache = new Map<string, BffResponse>();

function teamApiUrl(path: string): string {
  const normalized = path.startsWith("/") ? path : `/${path}`;
  if (typeof window === "undefined") return `${TEAM_WEB_PREFIX}${normalized}`;
  return `${window.location.origin}${TEAM_WEB_PREFIX}${normalized}`;
}

function memberUsageFetchError(status: number): string {
  if (status === 400) return "멤버 상세 조회 파라미터가 올바르지 않습니다.";
  if (status === 401 || status === 403) return "로그인 세션이 만료되었거나 접근 권한이 없습니다.";
  if (status === 404) return "멤버 상세 엔드포인트를 찾지 못했습니다.";
  if (status >= 500) return "멤버 상세 서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";
  return `멤버 상세 조회 실패 (HTTP ${status})`;
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

function usageQuery(params: Record<string, string | undefined>): string {
  const sp = new URLSearchParams();
  sp.set("mode", "TEAM_MEMBER");
  for (const [k, v] of Object.entries(params)) {
    if (!v) continue;
    sp.set(k, v);
  }
  return sp.toString();
}

function teamTotalQuery(params: Record<string, string | undefined>): string {
  const sp = new URLSearchParams();
  sp.set("mode", "TEAM_TOTAL");
  for (const [k, v] of Object.entries(params)) {
    if (!v) continue;
    sp.set(k, v);
  }
  return sp.toString();
}

function hashToUint(str: string): number {
  let h = 2166136261;
  for (let i = 0; i < str.length; i++) {
    h ^= str.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

function colorForMember(userId: string): string {
  const idx = hashToUint(userId) % MEMBER_PALETTE.length;
  return MEMBER_PALETTE[idx] ?? "#64748b";
}

function truncateLabel(v: string, max = 24): string {
  if (v.length <= max) return v;
  return `${v.slice(0, max - 1)}…`;
}

function MemberModelTooltip({
  active,
  payload,
  memberNameById,
}: {
  active?: boolean;
  payload?: readonly unknown[];
  memberNameById: Record<string, string>;
}) {
  if (!active || !payload || payload.length === 0) return null;
  const p = payload[0] as TooltipPayloadEntry;
  const row = p.payload;
  const memberKey = p.dataKey;
  if (!row || !memberKey || memberKey === "totalRequests") return null;
  const memberRequests = Number(p.value ?? 0);
  const total = Number(row.totalRequests ?? 0);
  const share = total > 0 ? (100 * memberRequests) / total : 0;
  const resolvedName = memberNameById[String(memberKey)] ?? String(memberKey);
  return (
    <div className="rounded-md border border-border bg-card px-3 py-2 text-xs shadow-sm">
      <p className="font-semibold text-foreground">모델: {row.model}</p>
      <p className="mt-1 text-muted-foreground">팀원명: {resolvedName}</p>
      <p className="text-muted-foreground">해당 모델 요청 수: {formatRequestCount(memberRequests)}</p>
      <p className="text-muted-foreground">팀 내 모델 점유율: {share.toFixed(1)}%</p>
    </div>
  );
}

export default function TeamMemberUsageLog({ teamId, userId, isActive }: TeamMemberUsageLogProps) {
  const todayKst = formatKstIsoDate();
  const [periodMode, setPeriodMode] = useState<PeriodMode>("7d");
  const [provider, setProvider] = useState<string>(PROVIDER_ALL);
  const [apiKeys, setApiKeys] = useState<TeamApiKey[]>([]);
  const [apiKeyId, setApiKeyId] = useState<string>("");
  const [keysLoading, setKeysLoading] = useState(false);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [memberRows, setMemberRows] = useState<Array<{ profile: TeamMemberProfile; byModel: ModelAgg[] }>>([]);
  const [isOthersExpanded, setIsOthersExpanded] = useState(false);

  const range = useMemo(() => presetRange(periodMode, todayKst), [periodMode, todayKst]);

  useEffect(() => {
    if (!teamId || !isActive) {
      setApiKeys([]);
      setApiKeyId("");
      return;
    }
    let cancelled = false;
    setKeysLoading(true);
    fetch(teamApiUrl(`/api/team/v1/teams/${encodeURIComponent(teamId)}/api-keys`), {
      credentials: "include",
      headers: { Accept: "application/json" },
    })
      .then(async (r) => {
        const json = (await r.json()) as { success?: boolean; data?: unknown };
        if (!r.ok || !json.success || !Array.isArray(json.data)) return [];
        return (json.data as unknown[])
          .map((item): TeamApiKey | null => {
            if (!item || typeof item !== "object") return null;
            const o = item as Record<string, unknown>;
            if (typeof o.id !== "number") return null;
            if (typeof o.alias !== "string") return null;
            if (typeof o.provider !== "string") return null;
            if (typeof o.createdAt !== "string") return null;
            return { id: o.id, alias: o.alias, provider: o.provider, createdAt: o.createdAt };
          })
          .filter((x): x is TeamApiKey => x !== null)
          .sort((a, b) => a.createdAt.localeCompare(b.createdAt));
      })
      .then((rows) => {
        if (cancelled) return;
        setApiKeys(rows);
        setApiKeyId((prev) => (prev && rows.some((x) => String(x.id) === prev) ? prev : rows[0] ? String(rows[0].id) : ""));
      })
      .catch(() => {
        if (!cancelled) {
          setApiKeys([]);
          setApiKeyId("");
        }
      })
      .finally(() => {
        if (!cancelled) setKeysLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [teamId, isActive]);

  useEffect(() => {
    // 팀 변경 시 이전 팀 데이터 잔존 방지
    setMemberRows([]);
    setIsOthersExpanded(false);
    setError(null);
  }, [teamId]);

  useEffect(() => {
    if (!isActive || !teamId) {
      setLoading(false);
      return;
    }
    const base = teamUsageBffBase();
    if (!base) {
      setError("사용량 API 베이스 URL을 확인할 수 없습니다.");
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(null);
    setMemberRows([]);
    setIsOthersExpanded(false);

    const qTotal = teamTotalQuery({
      teamId,
      from: range.from,
      to: range.to,
      provider: provider === PROVIDER_ALL ? undefined : provider,
      apiKeyId: apiKeyId || undefined,
    });

    fetch(`${base}/dashboard?${qTotal}`, {
      credentials: "include",
      headers: { Accept: "application/json" },
    })
      .then(async (r) => {
        if (!r.ok) throw new Error(memberUsageFetchError(r.status));
        return (await r.json()) as BffResponse;
      })
      .then(async (teamTotal) => {
        if (cancelled) return;
        const profiles = (teamTotal.memberProfiles ?? []).filter((p) => !!p.userId);
        if (profiles.length === 0) {
          setMemberRows([]);
          return;
        }

        const results = await Promise.all(
          profiles.map(async (profile) => {
            const cacheKey = [teamId, profile.userId, range.from, range.to, provider, apiKeyId].join("|");
            const cached = memberDashboardCache.get(cacheKey);
            if (cached) {
              return { profile, byModel: cached.byModel ?? [] };
            }
            const qMember = usageQuery({
              teamId,
              userId: profile.userId,
              from: range.from,
              to: range.to,
              provider: provider === PROVIDER_ALL ? undefined : provider,
              apiKeyId: apiKeyId || undefined,
            });
            const r = await fetch(`${base}/dashboard?${qMember}`, {
              credentials: "include",
              headers: { Accept: "application/json" },
            });
            if (!r.ok) throw new Error(memberUsageFetchError(r.status));
            const body = (await r.json()) as BffResponse;
            memberDashboardCache.set(cacheKey, body);
            return { profile, byModel: body.byModel ?? [] };
          }),
        );
        if (!cancelled) {
          setMemberRows(results);
        }
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
  }, [isActive, teamId, range.from, range.to, provider, apiKeyId]);

  const memberSeries = useMemo<MemberSeries[]>(() => {
    return memberRows.map(({ profile, byModel }) => ({
      userId: profile.userId,
      displayName: profile.displayName?.trim() || profile.userId,
      requests: byModel.reduce((sum, m) => sum + Math.max(0, m.requestCount), 0),
    }));
  }, [memberRows]);

  const modelRows = useMemo(() => {
    const modelMap = new Map<
      string,
      {
        model: string;
        provider: string;
        totalRequests: number;
        byMember: Map<string, number>;
      }
    >();
    for (const { profile, byModel } of memberRows) {
      for (const m of byModel) {
        const req = Math.max(0, m.requestCount);
        if (req <= 0) continue;
        const key = `${m.provider}::${m.model}`;
        const existing = modelMap.get(key);
        if (!existing) {
          const byMember = new Map<string, number>();
          byMember.set(profile.userId, req);
          modelMap.set(key, {
            model: m.model,
            provider: m.provider,
            totalRequests: req,
            byMember,
          });
          continue;
        }
        existing.totalRequests += req;
        existing.byMember.set(profile.userId, (existing.byMember.get(profile.userId) ?? 0) + req);
      }
    }
    return [...modelMap.values()].sort((a, b) => b.totalRequests - a.totalRequests);
  }, [memberRows]);

  const topRows = useMemo(() => modelRows.slice(0, MODEL_REQUESTS_TOP_N), [modelRows]);
  const othersRaw = useMemo(() => modelRows.slice(MODEL_REQUESTS_TOP_N), [modelRows]);
  const othersTotal = useMemo(() => othersRaw.reduce((s, r) => s + r.totalRequests, 0), [othersRaw]);
  const hasOthers = othersRaw.length > 0;

  const chartRows = useMemo<ChartRow[]>(() => {
    const rows: ChartRow[] = topRows.map((row) => {
      const base: ChartRow = {
        label: truncateLabel(row.model),
        model: row.model,
        totalRequests: row.totalRequests,
        isOthers: false,
        provider: row.provider,
      };
      for (const member of memberSeries) {
        base[member.userId] = row.byMember.get(member.userId) ?? 0;
      }
      return base;
    });

    if (hasOthers) {
      const othersByMember = new Map<string, number>();
      for (const row of othersRaw) {
        for (const [memberId, req] of row.byMember.entries()) {
          othersByMember.set(memberId, (othersByMember.get(memberId) ?? 0) + req);
        }
      }
      const othersRow: ChartRow = {
        label: OTHERS_LABEL,
        model: OTHERS_LABEL,
        totalRequests: othersTotal,
        isOthers: true,
        provider: "OTHERS",
      };
      for (const member of memberSeries) {
        othersRow[member.userId] = othersByMember.get(member.userId) ?? 0;
      }
      rows.push(othersRow);
    }
    return rows;
  }, [topRows, hasOthers, othersRaw, othersTotal, memberSeries]);

  const othersRows = useMemo<OthersRow[]>(
    () =>
      othersRaw.map((r) => ({
        model: r.model,
        provider: r.provider,
        requests: r.totalRequests,
      })),
    [othersRaw],
  );

  const othersMax = useMemo(() => Math.max(...othersRows.map((r) => r.requests), 1), [othersRows]);
  const othersRequestSum = useMemo(
    () => Math.max(othersRows.reduce((s, r) => s + r.requests, 0), 1),
    [othersRows],
  );
  const hasData = chartRows.some((r) => r.totalRequests > 0);
  const memberNameById = useMemo(
    () =>
      memberSeries.reduce<Record<string, string>>((acc, cur) => {
        acc[cur.userId] = cur.displayName;
        return acc;
      }, {}),
    [memberSeries],
  );

  if (!isActive) {
    return (
      <div className="rounded-lg border border-border bg-card p-4 text-sm text-muted-foreground">
        멤버 상세 탭을 선택하면 데이터를 불러옵니다.
      </div>
    );
  }

  return (
    <div className="w-full min-w-0 space-y-6">
      <div className="flex flex-wrap items-end gap-4">
        <div className="space-y-2 sm:w-44">
          <Label htmlFor="member-period">기간</Label>
          <Select value={periodMode} onValueChange={(v) => setPeriodMode(v as PeriodMode)}>
            <SelectTrigger id="member-period">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="today">오늘</SelectItem>
              <SelectItem value="7d">최근 7일</SelectItem>
              <SelectItem value="30d">최근 30일</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-2 sm:w-52">
          <Label>API Key</Label>
          <Select value={apiKeyId} onValueChange={setApiKeyId} disabled={keysLoading || apiKeys.length === 0}>
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
          <Label>공급사</Label>
          <Select value={provider} onValueChange={setProvider}>
            <SelectTrigger>
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

      {error ? (
        <p className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {error}
        </p>
      ) : null}

      {loading ? (
        <div className="space-y-4" aria-busy="true">
          <div className="h-[360px] animate-pulse rounded-lg border border-border bg-muted/40" />
          <div className="h-[220px] animate-pulse rounded-lg border border-border bg-muted/40" />
        </div>
      ) : null}

      {!loading && !error && !hasData ? (
        <section className="rounded-lg border border-border p-4 shadow-sm">
          <h2 className="mb-4 text-lg font-medium">팀원별 모델 활용 패턴</h2>
          <div className="h-[360px] min-h-[360px] w-full rounded-lg border border-dashed border-border bg-muted/20" />
          <p className="mt-3 text-center text-sm text-muted-foreground">
            선택한 팀/필터에서 멤버 모델 사용 데이터가 없습니다.
          </p>
        </section>
      ) : null}

      {!loading && !error && hasData ? (
        <section className="rounded-lg border border-border p-4 shadow-sm">
          <h2 className="mb-4 text-lg font-medium">팀원별 모델 활용 패턴 및 점유 분석</h2>
          <div className="h-[360px] min-h-[360px] w-full">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart
                data={chartRows}
                margin={{ top: 8, right: 16, left: 8, bottom: 24 }}
                onClick={(state) => {
                  const payload = (state as { activePayload?: TooltipPayloadEntry[] } | undefined)?.activePayload;
                  const row = payload?.[0]?.payload;
                  if (row?.isOthers) {
                    setIsOthersExpanded((prev) => !prev);
                  }
                }}
              >
                <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                <XAxis dataKey="label" tick={{ fontSize: 11 }} interval={0} angle={-15} textAnchor="end" height={56} />
                <YAxis tick={{ fontSize: 11 }} />
                <Tooltip content={<MemberModelTooltip memberNameById={memberNameById} />} shared={false} />
                {memberSeries.map((member) => (
                  <Bar
                    key={member.userId}
                    dataKey={member.userId}
                    stackId="modelReq"
                    name={member.displayName}
                    fill={colorForMember(member.userId)}
                    isAnimationActive={false}
                  >
                    {chartRows.map((row) => (
                      <Cell
                        key={`${row.model}:${member.userId}`}
                        fill={row.isOthers ? OTHERS_BAR_COLOR : colorForMember(member.userId)}
                        style={{ cursor: row.isOthers ? "pointer" : "default" }}
                      />
                    ))}
                  </Bar>
                ))}
              </BarChart>
            </ResponsiveContainer>
          </div>

          <div className="mt-3 flex flex-wrap gap-x-4 gap-y-2 text-xs text-muted-foreground">
            {memberSeries.map((member) => (
              <div key={member.userId} className="inline-flex items-center gap-1.5">
                <span className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: colorForMember(member.userId) }} />
                <span>{member.displayName}</span>
              </div>
            ))}
          </div>

          <p className="mt-3 text-xs text-muted-foreground">
            상위 {MODEL_REQUESTS_TOP_N}개 모델 + 기타로 표시됩니다. 기타 막대를 클릭하면 상세 미니바가 펼쳐집니다.
          </p>

          <div
            className={[
              "mt-3 overflow-hidden rounded-md border border-border/70 bg-muted/20 transition-all duration-300 ease-out",
              isOthersExpanded ? "max-h-[24rem] opacity-100" : "max-h-0 opacity-0 border-transparent",
            ].join(" ")}
          >
            {othersRows.length === 0 ? null : (
              <div className="p-3">
                <button
                  type="button"
                  className="mb-3 inline-flex items-center gap-1.5 text-sm font-medium text-foreground"
                  onClick={() => setIsOthersExpanded((prev) => !prev)}
                >
                  {isOthersExpanded ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
                  기타 모델 상세 내역
                </button>
                <div className="space-y-2">
                  {othersRows.map((row) => {
                    const widthPct = Math.max(4, Math.round((row.requests / othersMax) * 100));
                    const sharePct = (row.requests / othersRequestSum) * 100;
                    return (
                      <div key={`${row.provider}:${row.model}`} className="grid grid-cols-[minmax(0,1fr)_9rem] gap-3">
                        <div className="min-w-0">
                          <p className="truncate text-sm font-medium" title={row.model}>
                            {row.model}
                          </p>
                          <p className="text-[11px] text-muted-foreground">{row.provider}</p>
                          <div className="mt-1 h-2 w-full rounded bg-muted">
                            <div className="h-2 rounded bg-slate-500/80" style={{ width: `${widthPct}%` }} />
                          </div>
                        </div>
                        <p className="self-end text-right text-xs tabular-nums text-muted-foreground">
                          {formatRequestCount(row.requests)} ({sharePct.toFixed(1)}%)
                        </p>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}
          </div>
        </section>
      ) : null}

      {!loading && !error && userId ? (
        <p className="text-xs text-muted-foreground">
          현재 선택된 사용자 힌트: <span className="font-medium text-foreground">{userId}</span> (멤버 전체 집계 기준으로 표시 중)
        </p>
      ) : null}
    </div>
  );
}
