package com.eevee.billingservice.mq;

import com.eevee.billingservice.service.UserAccountDeletionAckPublisher;
import com.eevee.billingservice.service.UserAccountDeletionCleanupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobugfreinds.identity.events.UserAccountDeletionRequestedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAccountDeletionRequestedListenerTest {

    @Test
    void onMessage_cleanupThenAck() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        UserAccountDeletionCleanupService cleanupService = mock(UserAccountDeletionCleanupService.class);
        UserAccountDeletionAckPublisher ackPublisher = mock(UserAccountDeletionAckPublisher.class);
        UserAccountDeletionRequestedListener listener =
                new UserAccountDeletionRequestedListener(objectMapper, cleanupService, ackPublisher);
        UserAccountDeletionRequestedEvent event = UserAccountDeletionRequestedEvent.of(11L, "member@test.com");
        String body = objectMapper.writeValueAsString(event);
        when(cleanupService.cleanup(event))
                .thenReturn(new UserAccountDeletionCleanupService.CleanupResult(1, 2, 3));

        listener.onMessage(body);

        verify(cleanupService).cleanup(event);
        verify(ackPublisher).publish(event);
    }

    @Test
    void onMessage_invalidPayload_rejectsWithoutRequeue() {
        ObjectMapper objectMapper = new ObjectMapper();
        UserAccountDeletionRequestedListener listener = new UserAccountDeletionRequestedListener(
                objectMapper,
                mock(UserAccountDeletionCleanupService.class),
                mock(UserAccountDeletionAckPublisher.class)
        );

        assertThatThrownBy(() -> listener.onMessage("{invalid"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }
}
