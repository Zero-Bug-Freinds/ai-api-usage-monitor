package com.eevee.billingservice.event;

/**
 * {@code team.events} payloads mixed with team-service domain records; billing selects by {@code eventType}.
 */
public final class TeamDomainAmqpEventTypes {

    public static final String TEAM_API_KEY_DELETED = "TEAM_API_KEY_DELETED";

    private TeamDomainAmqpEventTypes() {
    }
}
