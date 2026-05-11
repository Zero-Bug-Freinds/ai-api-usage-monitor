package com.zerobugfreinds.identity_service.mq;

import com.zerobugfreinds.identity.events.IdentityUserSyncEvent;

/**
 * Internal application event so {@link IdentityUserSyncEventPublisher} can emit to RabbitMQ after DB commit.
 */
public record IdentityUserSyncCommitted(IdentityUserSyncEvent event) {
}
