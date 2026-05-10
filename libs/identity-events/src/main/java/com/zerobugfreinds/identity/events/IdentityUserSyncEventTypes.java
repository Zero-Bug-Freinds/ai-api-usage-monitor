package com.zerobugfreinds.identity.events;

/**
 * {@link IdentityUserSyncEvent#eventType()} values published by identity-service.
 */
public final class IdentityUserSyncEventTypes {

    /** User row created at signup completion. */
    public static final String USER_REGISTERED = "USER_REGISTERED";

    /** User row materialized on first login (lazy provisioning). */
    public static final String USER_FIRST_LOGIN_RECORDED = "USER_FIRST_LOGIN_RECORDED";

    /** Email or display name changed. */
    public static final String USER_PROFILE_UPDATED = "USER_PROFILE_UPDATED";

    /** One-off backfill / operator replay; team-service treats as upsert. */
    public static final String USER_SYNC_BACKFILL = "USER_SYNC_BACKFILL";

    private IdentityUserSyncEventTypes() {
    }
}
