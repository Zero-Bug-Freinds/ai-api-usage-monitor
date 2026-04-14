package com.zerobugfreinds.team_service.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zerobugfreinds.identity.events.UserAccountDeletionRequestedEvent;
import com.zerobugfreinds.team_service.service.UserAccountDeletionAckPublisher;
import com.zerobugfreinds.team_service.service.UserAccountDeletionCleanupService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAccountDeletionRequestedListenerTest {

	@Test
	void onMessage_cleansUpAndPublishesAck() throws Exception {
		ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
		UserAccountDeletionCleanupService cleanupService = mock(UserAccountDeletionCleanupService.class);
		UserAccountDeletionAckPublisher ackPublisher = mock(UserAccountDeletionAckPublisher.class);
		UserAccountDeletionRequestedListener listener =
				new UserAccountDeletionRequestedListener(objectMapper, cleanupService, ackPublisher);
		UserAccountDeletionRequestedEvent event = UserAccountDeletionRequestedEvent.of(11L, "member@test.com");
		String body = objectMapper.writeValueAsString(event);
		when(cleanupService.cleanupByUserId("member@test.com"))
				.thenReturn(new UserAccountDeletionCleanupService.CleanupResult(1L, 2L));

		listener.onMessage(body);

		verify(cleanupService).cleanupByUserId("member@test.com");
		verify(ackPublisher).publish(event);
	}

	@Test
	void onMessage_rejectsInvalidPayload() {
		ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
		UserAccountDeletionCleanupService cleanupService = mock(UserAccountDeletionCleanupService.class);
		UserAccountDeletionAckPublisher ackPublisher = mock(UserAccountDeletionAckPublisher.class);
		UserAccountDeletionRequestedListener listener =
				new UserAccountDeletionRequestedListener(objectMapper, cleanupService, ackPublisher);

		assertThatThrownBy(() -> listener.onMessage("{invalid-json"))
				.isInstanceOf(AmqpRejectAndDontRequeueException.class);
	}
}
