"use client";

import { useEffect, useMemo, useState } from "react";

type TeamDashboardProps = {
  teamId: string;
  onSelectUser: (userId: string) => void;
};

type BffResponse = {
  summary?: { totalRequests?: number; totalErrors?: number; totalInputTokens?: number; totalEstimatedCost?: number };
  memberProfiles?: Array<{ userId: string; displayName?: string; role?: string }>;
  enrichment?: { partial?: boolean; warnings?: string[] };
};

const BASE_URL = process.env.NEXT_PUBLIC_USAGE_BFF_BASE_URL ?? "http://localhost:8888";

export default function TeamDashboard({ teamId, onSelectUser }: TeamDashboardProps) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<BffResponse | null>(null);

  const from = useMemo(() => {
    const d = new Date();
    d.setDate(d.getDate() - 7);
    return d.toISOString().slice(0, 10);
  }, []);
  const to = useMemo(() => new Date().toISOString().slice(0, 10), []);

  useEffect(() => {
    if (!teamId) {
      return;
    }
    setLoading(true);
    setError(null);
    fetch(`${BASE_URL}/api/v1/usage/bff/dashboard?teamId=${encodeURIComponent(teamId)}&from=${from}&to=${to}`)
      .then(async (r) => {
        if (!r.ok) {
          throw new Error(`HTTP ${r.status}`);
        }
        return (await r.json()) as BffResponse;
      })
      .then((body) => {
        setData(body);
        const first = body.memberProfiles?.[0]?.userId;
        if (first) {
          onSelectUser(first);
        }
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  }, [teamId, from, to, onSelectUser]);

  if (!teamId) {
    return <div className="rounded border p-3 text-sm">팀 ID가 없어 팀 사용량을 표시할 수 없습니다.</div>;
  }

  if (loading) {
    return <div className="rounded border p-3 text-sm">팀 대시보드 로딩 중...</div>;
  }

  if (error) {
    return <div className="rounded border p-3 text-sm text-red-600">팀 대시보드 로드 실패: {error}</div>;
  }

  return (
    <div className="rounded border p-3">
      <div className="mb-2 text-sm font-semibold">팀 통합 사용량</div>
      <div className="grid grid-cols-2 gap-2 text-xs">
        <div>요청 수: {data?.summary?.totalRequests ?? 0}</div>
        <div>오류 수: {data?.summary?.totalErrors ?? 0}</div>
        <div>토큰: {data?.summary?.totalInputTokens ?? 0}</div>
        <div>비용: {data?.summary?.totalEstimatedCost ?? 0}</div>
      </div>
      {data?.enrichment?.partial ? (
        <div className="mt-2 rounded bg-yellow-50 p-2 text-xs text-yellow-700">
          프로필 일부 결합 실패: {(data.enrichment.warnings ?? []).join(", ")}
        </div>
      ) : null}
      <div className="mt-3 space-y-1">
        {(data?.memberProfiles ?? []).map((m) => (
          <button
            key={m.userId}
            type="button"
            className="block rounded border px-2 py-1 text-left text-xs hover:bg-gray-50"
            onClick={() => onSelectUser(m.userId)}
          >
            {m.displayName ?? m.userId} ({m.role ?? "UNKNOWN"})
          </button>
        ))}
      </div>
    </div>
  );
}
