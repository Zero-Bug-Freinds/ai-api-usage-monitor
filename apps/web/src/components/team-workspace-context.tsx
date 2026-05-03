"use client";

import * as React from "react";
import type { NextRouter } from "next/router";
import {
    kstCalendarDateString,
    readCachedTeamList,
    readLastSyncDayKst,
    writeCachedTeamList,
    writeLastSyncDayKst,
    type CachedTeamItem,
} from "@ai-usage/team-workspace-cache";

export type TeamWorkspaceContextValue = {
    teams: CachedTeamItem[];
    syncError: string | null;
    /** 네트워크로 팀 목록을 다시 가져옵니다(생성 직후 등). */
    requestTeamResync: () => void;
};

const TeamWorkspaceContext = React.createContext<TeamWorkspaceContextValue | null>(null);

function isNavigationReload(): boolean {
    if (typeof window === "undefined" || !window.performance) return false;
    const nav = window.performance.getEntriesByType("navigation")[0] as PerformanceNavigationTiming | undefined;
    return nav?.type === "reload";
}

function shouldSyncFromNetwork(cached: CachedTeamItem[]): boolean {
    if (cached.length === 0) return true;
    if (isNavigationReload()) return true;
    const today = kstCalendarDateString();
    const last = readLastSyncDayKst();
    return last !== today;
}

function parseMyTeamsPayload(data: unknown): CachedTeamItem[] {
    if (!Array.isArray(data)) return [];
    return data
        .map((item): CachedTeamItem | null => {
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
        .filter((x): x is CachedTeamItem => x !== null);
}

export function TeamWorkspaceProvider({
    children,
    router,
}: {
    children: React.ReactNode;
    router: NextRouter;
}) {
    const [teams, setTeams] = React.useState<CachedTeamItem[]>(() => readCachedTeamList());
    const [syncError, setSyncError] = React.useState<string | null>(null);
    const [syncNonce, setSyncNonce] = React.useState(0);

    const requestTeamResync = React.useCallback(() => {
        setSyncNonce((n) => n + 1);
    }, []);

    React.useEffect(() => {
        if (!router.isReady) return;
        /** basePath `/teams` 사용 시 index 페이지의 pathname은 `/`이지 `/teams`가 아님 */

        const cached = readCachedTeamList();
        setTeams(cached);

        const forceNetwork = syncNonce > 0;
        if (!forceNetwork && !shouldSyncFromNetwork(cached)) {
            return;
        }

        let cancelled = false;
        (async () => {
            try {
                const res = await fetch(`${window.location.origin}/teams/api/team/v1/me/teams`, {
                    credentials: "include",
                    headers: { Accept: "application/json" },
                });
                const json = (await res.json()) as { success?: boolean; data?: unknown; message?: string };
                if (!res.ok || !json.success || !Array.isArray(json.data)) {
                    if (!cancelled) {
                        setSyncError(
                            typeof json.message === "string" ? json.message : "팀 목록을 갱신하지 못했습니다",
                        );
                    }
                    return;
                }
                const list = parseMyTeamsPayload(json.data);
                if (cancelled) return;
                writeCachedTeamList(list);
                writeLastSyncDayKst(kstCalendarDateString());
                setTeams(list);
                setSyncError(null);
            } catch {
                if (!cancelled) setSyncError("팀 목록을 갱신하지 못했습니다");
            }
        })();

        return () => {
            cancelled = true;
        };
    }, [router.isReady, router.pathname, syncNonce]);

    const value = React.useMemo(
        () => ({
            teams,
            syncError,
            requestTeamResync,
        }),
        [teams, syncError, requestTeamResync],
    );

    return <TeamWorkspaceContext.Provider value={value}>{children}</TeamWorkspaceContext.Provider>;
}

export function useTeamWorkspace(): TeamWorkspaceContextValue {
    const ctx = React.useContext(TeamWorkspaceContext);
    if (!ctx) {
        throw new Error("useTeamWorkspace must be used within TeamWorkspaceProvider");
    }
    return ctx;
}
