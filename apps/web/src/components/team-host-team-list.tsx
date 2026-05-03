"use client";

import * as React from "react";
import type { CachedTeamItem } from "@ai-usage/team-workspace-cache";
import { ChevronRight, Search } from "lucide-react";

export type TeamHostTeamListProps = {
  teams: CachedTeamItem[];
  selectedTeamId: string;
  onSelectTeam: (teamId: string) => void;
};

/**
 * 호스트 좌측 팀 목록(Task37-11). `team-management-view` 좌측 aside와 유사한 zinc·레이아웃만 맞춘다.
 */
export function TeamHostTeamList({ teams, selectedTeamId, onSelectTeam }: TeamHostTeamListProps) {
  const [keyword, setKeyword] = React.useState("");

  const filtered = React.useMemo(() => {
    const k = keyword.trim().toLowerCase();
    if (k === "") return teams;
    return teams.filter((t) => t.name.toLowerCase().includes(k));
  }, [teams, keyword]);

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="shrink-0 border-b border-zinc-200 px-4 py-4">
        <div className="flex items-center justify-between gap-2">
          <h2 className="text-base font-semibold text-zinc-900">팀 목록</h2>
        </div>
        <p className="mt-1 text-xs text-zinc-500">팀을 선택하면 우측 탭 영역에서 상세를 다룹니다.</p>
        <div className="relative mt-3">
          <Search
            className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-zinc-400"
            aria-hidden
          />
          <input
            id="team-host-search"
            className="h-10 w-full rounded-md border border-zinc-300 bg-white pl-9 pr-3 text-sm outline-none transition focus:border-zinc-400 focus:ring-2 focus:ring-zinc-200"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="팀 이름 검색"
            autoComplete="off"
          />
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto p-3">
        {filtered.length === 0 ? (
          <p className="px-2 py-3 text-sm text-zinc-500">
            {keyword.trim() ? "검색된 팀이 없습니다" : "참여 중인 팀이 없습니다."}
          </p>
        ) : (
          <ul className="space-y-2">
            {filtered.map((team) => {
              const isSelected = selectedTeamId === team.id;
              return (
                <li key={team.id}>
                  <button
                    type="button"
                    className={`w-full rounded-lg border px-3 py-2 text-left transition ${
                      isSelected
                        ? "border-zinc-900 bg-white shadow-sm"
                        : "border-zinc-200 bg-white hover:border-zinc-300 hover:bg-zinc-50"
                    }`}
                    onClick={() => onSelectTeam(team.id)}
                  >
                    <div className="flex items-center gap-2">
                      {isSelected ? (
                        <ChevronRight className="h-4 w-4 shrink-0 text-zinc-700" aria-hidden />
                      ) : (
                        <ChevronRight className="h-4 w-4 shrink-0 text-zinc-400" aria-hidden />
                      )}
                      <span className="truncate text-sm font-medium text-zinc-900">{team.name}</span>
                    </div>
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </div>
  );
}
