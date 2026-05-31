package com.eevee.usageservice.mq;

import com.eevee.usageservice.service.UserAccountDeletionAckPublisher;
import com.eevee.usageservice.service.UserAccountDeletionCleanupService;
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
                .thenReturn(new UserAccountDeletionCleanupService.CleanupResult(1, 2, 3, 4));

        listener.onMessage(body);

        verify(cleanupService).cleanup(event);
        verify(ackPublisher).publish(event);
    }

    @Test
    void onMessage_invalidPayload_rejectsWithoutRequeue() {
        UserAccountDeletionRequestedListener listener = new UserAccountDeletionRequestedListener(
                new ObjectMapper(),
                mock(UserAccountDeletionCleanupService.class),
                mock(UserAccountDeletionAckPublisher.class)
        );

        assertThatThrownBy(() -> listener.onMessage("{invalid"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }
}
