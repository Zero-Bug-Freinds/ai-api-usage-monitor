package com.zerobugfreinds.identity_service.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zerobugfreinds.identity.events.IdentityUserSyncEvent;
import com.zerobugfreinds.identity.events.IdentityUserSyncEventTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IdentityUserSyncEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Test
    void publishImmediately_sendsJsonPayloadToExchangeAndRoutingKey() throws Exception {
        IdentityUserSyncEventPublisher publisher = new IdentityUserSyncEventPublisher(
                applicationEventPublisher,
                rabbitTemplate,
                "identity.events",
                "identity.user.sync"
        );

        IdentityUserSyncEvent event = IdentityUserSyncEvent.of(
                IdentityUserSyncEventTypes.USER_REGISTERED,
                "user@example.com",
                "user@example.com",
                "User Name",
                null
        );
        publisher.publishImmediately(event);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate).convertAndSend(
                eq("identity.events"),
                eq("identity.user.sync"),
                jsonCaptor.capture()
        );

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        JsonNode node = mapper.readTree(jsonCaptor.getValue());
        assertThat(node.get("eventType").asText()).isEqualTo(IdentityUserSyncEventTypes.USER_REGISTERED);
        assertThat(node.get("userId").asText()).isEqualTo("user@example.com");
        assertThat(node.get("email").asText()).isEqualTo("user@example.com");
        assertThat(node.get("name").asText()).isEqualTo("User Name");
        assertThat(node.hasNonNull("occurredAt")).isTrue();
    }
}
