package com.eevee.billingservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobugfreinds.identity.events.UserAccountDeletionAcknowledgedEvent;
import com.zerobugfreinds.identity.events.UserAccountDeletionRequestedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class UserAccountDeletionAckPublisherTest {

    @Test
    void publish_serializesSourceBilling() throws Exception {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        UserAccountDeletionAckPublisher publisher = new UserAccountDeletionAckPublisher(
                rabbitTemplate,
                objectMapper,
                "identity.events",
                "identity.user.account-deletion-ack"
        );

        publisher.publish(UserAccountDeletionRequestedEvent.of(10L, "user@test.com"));

        verify(rabbitTemplate).convertAndSend(
                eq("identity.events"),
                eq("identity.user.account-deletion-ack"),
                bodyCaptor.capture()
        );
        UserAccountDeletionAcknowledgedEvent ackEvent =
                objectMapper.readValue(bodyCaptor.getValue(), UserAccountDeletionAcknowledgedEvent.class);
        assertThat(ackEvent.source()).isEqualTo(UserAccountDeletionAcknowledgedEvent.SOURCE_BILLING);
        assertThat(ackEvent.identityUserId()).isEqualTo(10L);
        assertThat(ackEvent.userEmail()).isEqualTo("user@test.com");
    }
}
