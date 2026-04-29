"use client";

import { useEffect, useMemo, useState } from "react";

type TeamMemberUsageLogProps = {
  teamId: string;
  userId: string;
};

type LogEntry = {
  occurredAt?: string;
  provider?: string;
  model?: string;
  requestSuccessful?: boolean;
  totalTokens?: number;
  estimatedCost?: number;
};

type BffResponse = {
  logs?: {
    content?: LogEntry[];
  };
};

const BASE_URL = process.env.NEXT_PUBLIC_USAGE_BFF_BASE_URL ?? "http://localhost:8888";

export default function TeamMemberUsageLog({ teamId, userId }: TeamMemberUsageLogProps) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [rows, setRows] = useState<LogEntry[]>([]);

  const from = useMemo(() => {
    const d = new Date();
    d.setDate(d.getDate() - 7);
    return d.toISOString().slice(0, 10);
  }, []);
  const to = useMemo(() => new Date().toISOString().slice(0, 10), []);

  useEffect(() => {
    if (!teamId || !userId) {
      setRows([]);
      return;
    }
    setLoading(true);
    setError(null);
    fetch(
      `${BASE_URL}/api/v1/usage/bff/dashboard?teamId=${encodeURIComponent(teamId)}&userId=${encodeURIComponent(
        userId
      )}&from=${from}&to=${to}`
    )
      .then(async (r) => {
        if (!r.ok) {
          throw new Error(`HTTP ${r.status}`);
        }
        return (await r.json()) as BffResponse;
      })
      .then((body) => setRows(body.logs?.content ?? []))
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  }, [teamId, userId, from, to]);

  if (!userId) {
    return <div className="rounded border p-3 text-sm">팀원을 선택하면 상세 로그를 볼 수 있습니다.</div>;
  }

  if (loading) {
    return <div className="rounded border p-3 text-sm">팀원 상세 로그 로딩 중...</div>;
  }

  if (error) {
    return <div className="rounded border p-3 text-sm text-red-600">팀원 로그 로드 실패: {error}</div>;
  }

  return (
    <div className="rounded border p-3">
      <div className="mb-2 text-sm font-semibold">팀원 상세 사용 로그 ({userId})</div>
      <div className="space-y-1 text-xs">
        {rows.length === 0 ? <div>표시할 로그가 없습니다.</div> : null}
        {rows.map((row, idx) => (
          <div key={`${row.occurredAt ?? "row"}-${idx}`} className="rounded border px-2 py-1">
            {row.occurredAt} | {row.provider} | {row.model} | {row.requestSuccessful ? "OK" : "ERR"} | tokens{" "}
            {row.totalTokens ?? 0} | cost {row.estimatedCost ?? 0}
          </div>
        ))}
      </div>
    </div>
  );
}
