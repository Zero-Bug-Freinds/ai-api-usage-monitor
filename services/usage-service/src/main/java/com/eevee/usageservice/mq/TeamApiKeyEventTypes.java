package com.eevee.usageservice.mq;

public final class TeamApiKeyEventTypes {

    public static final String TEAM_API_KEY_REGISTERED = "TEAM_API_KEY_REGISTERED";
    public static final String TEAM_API_KEY_UPDATED = "TEAM_API_KEY_UPDATED";
    public static final String TEAM_API_KEY_DELETED = "TEAM_API_KEY_DELETED";
    public static final String TEAM_API_KEY_DELETION_SCHEDULED = "TEAM_API_KEY_DELETION_SCHEDULED";
    public static final String TEAM_API_KEY_DELETION_CANCELLED = "TEAM_API_KEY_DELETION_CANCELLED";

    private TeamApiKeyEventTypes() {
    }
}
