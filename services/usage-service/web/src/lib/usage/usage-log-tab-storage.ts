export type UsageLogDataTab = "personal" | "team"

const STORAGE_KEY = "usage-web.logDataTab"

export function readStoredLogDataTab(): UsageLogDataTab {
    if (typeof window === "undefined") return "personal"
    try {
        const v = window.localStorage.getItem(STORAGE_KEY)
        return v === "team" ? "team" : "personal"
    } catch {
        return "personal"
    }
}

export function persistLogDataTab(tab: UsageLogDataTab): void {
    if (typeof window === "undefined") return
    try {
        window.localStorage.setItem(STORAGE_KEY, tab)
    } catch {
        /* ignore quota / private mode */
    }
}

export function logDataTabToDataContext(tab: UsageLogDataTab): "PERSONAL" | "TEAM_MEMBER_ONLY" {
    return tab === "team" ? "TEAM_MEMBER_ONLY" : "PERSONAL"
}
