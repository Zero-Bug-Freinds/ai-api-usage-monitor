/** localStorage: JSON array of { id, name, createdAt? } */
export const CACHED_TEAM_LIST_KEY = "cached_team_list";

/** localStorage: team id string */
export const LAST_SELECTED_TEAM_KEY = "last_selected_team";

/** localStorage: KST calendar day YYYY-MM-DD of last successful team list sync */
export const LAST_SYNC_TIMESTAMP_KEY = "last_sync_timestamp";

/** localStorage: team usage MFE dashboard filters (Task35-4(2)) */
export const TEAM_USAGE_DASHBOARD_FILTERS_KEY = "team-usage-dashboard:v2";

/** sessionStorage: personal usage dashboard (usage-web) */
export const USAGE_DASHBOARD_PROVIDER_KEY = "usage-dashboard:provider:v1";

export const USAGE_DASHBOARD_PERIOD_KEY = "usage-dashboard:period:v1";

/** Dispatched before shell logout clears storage; usage apps may reset React state. */
export const AI_USAGE_LOGOUT_EVENT = "ai-usage:logout";
