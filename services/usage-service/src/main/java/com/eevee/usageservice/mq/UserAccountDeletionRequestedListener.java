package com.eevee.usageservice.mq;

import com.eevee.usageservice.service.UserAccountDeletionAckPublisher;
import com.eevee.usageservice.service.UserAccountDeletionCleanupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobugfreinds.identity.events.UserAccountDeletionRequestedEvent;
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

    @RabbitListener(queues = "${identity.account-deletion-event.usage.queue:usage.account-deletion.requested.queue}")
    public void onMessage(String body) {
        try {
            UserAccountDeletionRequestedEvent event = objectMapper.readValue(body, UserAccountDeletionRequestedEvent.class);
            UserAccountDeletionCleanupService.CleanupResult result = cleanupService.cleanup(event);
            log.info(
                    "Handled UserAccountDeletionRequestedEvent identityUserId={} userEmail={} deletedLogs={} deletedMetadata={} deletedSummary={} deletedCumulative={}",
                    event.identityUserId(),
                    event.userEmail(),
                    result.deletedLogRows(),
                    result.deletedMetadataRows(),
                    result.deletedSummaryRows(),
                    result.deletedCumulativeTokenRows()
            );
            ackPublisher.publish(event);
        } catch (Exception e) {
            log.error("Invalid or failed UserAccountDeletionRequestedEvent payload: {}", e.getMessage());
            throw new AmqpRejectAndDontRequeueException(e);
        }
    }
}
