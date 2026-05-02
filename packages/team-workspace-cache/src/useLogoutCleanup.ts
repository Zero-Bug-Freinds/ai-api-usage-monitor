"use client";

import * as React from "react";
import { clearTeamWorkspaceClientStorage } from "./cache";
import { AI_USAGE_LOGOUT_EVENT } from "./keys";

/**
 * Clears team/usage client storage when shell signals logout, and on cross-tab storage events.
 */
export function useLogoutCleanup(): void {
    React.useEffect(() => {
        const onLogout = () => {
            clearTeamWorkspaceClientStorage();
        };
        window.addEventListener(AI_USAGE_LOGOUT_EVENT, onLogout);
        return () => window.removeEventListener(AI_USAGE_LOGOUT_EVENT, onLogout);
    }, []);
}
