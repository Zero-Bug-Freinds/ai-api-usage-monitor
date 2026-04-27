package com.zerobugfreinds.identity_service.mq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zerobugfreinds.identity_service.service.RefreshTokenRevocationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * TEAM_MEMBER_REMOVED 이벤트를 수신해 사용자 토큰을 무효화한다.
 */
@Component
public class TeamMemberRemovedEventListener {

    private static final Logger log = LoggerFactory.getLogger(TeamMemberRemovedEventListener.class);
    private static final String TEAM_MEMBER_REMOVED = "TEAM_MEMBER_REMOVED";
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());

    private final RefreshTokenRevocationService refreshTokenRevocationService;

    public TeamMemberRemovedEventListener(RefreshTokenRevocationService refreshTokenRevocationService) {
        this.refreshTokenRevocationService = refreshTokenRevocationService;
    }

    @RabbitListener(queues = "${identity.team-member-removed.queue:identity.team.member-removed.queue}")
    public void onTeamDomainEvent(String body) {
        try {
            TeamMemberRemovedEvent event = JSON.readValue(body, TeamMemberRemovedEvent.class);
            if (!TEAM_MEMBER_REMOVED.equals(event.eventType())) {
                return;
            }
            Long removedUserId = parseUserId(event.removedUserId());
            refreshTokenRevocationService.revokeAllByUserId(removedUserId, event.teamId(), event.occurredAt());
        } catch (Exception ex) {
            log.error("Invalid team domain event payload for TEAM_MEMBER_REMOVED: {}", ex.getMessage());
            throw new AmqpRejectAndDontRequeueException(ex);
        }
    }

    private Long parseUserId(String rawUserId) {
        if (rawUserId == null || rawUserId.isBlank()) {
            throw new IllegalArgumentException("removedUserId가 비어 있습니다");
        }
        return Long.parseLong(rawUserId.trim());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TeamMemberRemovedEvent(
            String eventType,
            String teamId,
            String removedUserId,
            java.time.Instant occurredAt
    ) {
    }
}
