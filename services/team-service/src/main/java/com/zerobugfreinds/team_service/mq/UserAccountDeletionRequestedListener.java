package com.zerobugfreinds.team_service.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobugfreinds.identity.events.UserAccountDeletionRequestedEvent;
import com.zerobugfreinds.team_service.service.UserAccountDeletionAckPublisher;
import com.zerobugfreinds.team_service.service.UserAccountDeletionCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class UserAccountDeletionRequestedListener {

	private static final Logger log = LoggerFactory.getLogger(UserAccountDeletionRequestedListener.class);

	private final ObjectMapper objectMapper;
	private final UserAccountDeletionCleanupService cleanupService;
	private final UserAccountDeletionAckPublisher ackPublisher;

	public UserAccountDeletionRequestedListener(
			ObjectMapper objectMapper,
			UserAccountDeletionCleanupService cleanupService,
			UserAccountDeletionAckPublisher ackPublisher
	) {
		this.objectMapper = objectMapper;
		this.cleanupService = cleanupService;
		this.ackPublisher = ackPublisher;
	}

	@RabbitListener(queues = "${identity.account-deletion-event.team.queue:team.account-deletion.requested.queue}")
	public void onMessage(String body) {
		try {
			UserAccountDeletionRequestedEvent event = objectMapper.readValue(body, UserAccountDeletionRequestedEvent.class);
			UserAccountDeletionCleanupService.CleanupResult result = cleanupService.cleanupByUserId(event.userEmail());
			log.info(
					"Handled UserAccountDeletionRequestedEvent userEmail={} deletedMemberships={} deletedInvitations={}",
					event.userEmail(),
					result.deletedMemberships(),
					result.deletedInvitations()
			);
			ackPublisher.publish(event);
		} catch (Exception e) {
			log.error("Invalid or failed UserAccountDeletionRequestedEvent payload: {}", e.getMessage());
			throw new AmqpRejectAndDontRequeueException(e);
		}
	}
}
