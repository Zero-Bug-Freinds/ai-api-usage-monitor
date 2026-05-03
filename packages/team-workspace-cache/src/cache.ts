import {
    AI_USAGE_LOGOUT_EVENT,
    CACHED_TEAM_LIST_KEY,
    LAST_SELECTED_TEAM_KEY,
    LAST_SYNC_TIMESTAMP_KEY,
    TEAM_USAGE_DASHBOARD_FILTERS_KEY,
    USAGE_DASHBOARD_PERIOD_KEY,
    USAGE_DASHBOARD_PROVIDER_KEY,
} from "./keys";

export type CachedTeamItem = {
    id: string;
    name: string;
    createdAt?: string;
};

export function readCachedTeamList(): CachedTeamItem[] {
    if (typeof localStorage === "undefined") return [];
    try {
        const raw = localStorage.getItem(CACHED_TEAM_LIST_KEY);
        if (!raw) return [];
        const parsed = JSON.parse(raw) as unknown;
        if (!Array.isArray(parsed)) return [];
        return parsed
            .map((item): CachedTeamItem | null => {
                if (!item || typeof item !== "object") return null;
                const o = item as Record<string, unknown>;
                if (typeof o.id !== "string" || typeof o.name !== "string") return null;
                return {
                    id: o.id,
                    name: o.name,
                    createdAt: typeof o.createdAt === "string" ? o.createdAt : undefined,
                };
            })
            .filter((x): x is CachedTeamItem => x !== null);
    } catch {
        return [];
    }
}

export function writeCachedTeamList(teams: CachedTeamItem[]): void {
    if (typeof localStorage === "undefined") return;
    try {
        localStorage.setItem(CACHED_TEAM_LIST_KEY, JSON.stringify(teams));
    } catch {
        /* ignore */
    }
}

export function readLastSelectedTeamId(): string | null {
    if (typeof localStorage === "undefined") return null;
    try {
        const v = localStorage.getItem(LAST_SELECTED_TEAM_KEY);
        return v && v.trim() !== "" ? v.trim() : null;
    } catch {
        return null;
    }
}

export function writeLastSelectedTeamId(teamId: string): void {
    if (typeof localStorage === "undefined") return;
    try {
        if (!teamId) {
            localStorage.removeItem(LAST_SELECTED_TEAM_KEY);
            return;
        }
        localStorage.setItem(LAST_SELECTED_TEAM_KEY, teamId);
    } catch {
        /* ignore */
    }
}

export function readLastSyncDayKst(): string | null {
    if (typeof localStorage === "undefined") return null;
    try {
        const v = localStorage.getItem(LAST_SYNC_TIMESTAMP_KEY);
        return v && /^\d{4}-\d{2}-\d{2}$/.test(v) ? v : null;
    } catch {
        return null;
    }
}

export function writeLastSyncDayKst(dayYyyyMmDd: string): void {
    if (typeof localStorage === "undefined") return;
    try {
        localStorage.setItem(LAST_SYNC_TIMESTAMP_KEY, dayYyyyMmDd);
    } catch {
        /* ignore */
    }
}

/**
 * Removes team workspace and related usage UI keys from local/session storage.
 * Does not call localStorage.clear() (shell may still do a full clear after logout).
 */
export function clearTeamWorkspaceClientStorage(): void {
    try {
        if (typeof localStorage !== "undefined") {
            localStorage.removeItem(CACHED_TEAM_LIST_KEY);
            localStorage.removeItem(LAST_SELECTED_TEAM_KEY);
            localStorage.removeItem(LAST_SYNC_TIMESTAMP_KEY);
            localStorage.removeItem(TEAM_USAGE_DASHBOARD_FILTERS_KEY);
        }
    } catch {
        /* ignore */
    }
    try {
        if (typeof sessionStorage !== "undefined") {
            sessionStorage.removeItem(USAGE_DASHBOARD_PROVIDER_KEY);
            sessionStorage.removeItem(USAGE_DASHBOARD_PERIOD_KEY);
        }
    } catch {
        /* ignore */
    }
}

export function dispatchLogoutEvent(): void {
    if (typeof window === "undefined") return;
    try {
        window.dispatchEvent(new CustomEvent(AI_USAGE_LOGOUT_EVENT, { bubbles: true }));
    } catch {
        /* ignore */
    }
}
