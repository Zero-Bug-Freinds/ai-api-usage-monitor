package com.zerobugfreinds.team_service.mq;

import com.zerobugfreinds.team_service.service.IdentityUserSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class IdentityUserSyncListener {

    private static final Logger log = LoggerFactory.getLogger(IdentityUserSyncListener.class);

    private final IdentityUserSyncService identityUserSyncService;

    public IdentityUserSyncListener(IdentityUserSyncService identityUserSyncService) {
        this.identityUserSyncService = identityUserSyncService;
    }

    @RabbitListener(queues = "${identity.user-sync.queue:team.identity.user-sync.queue}")
    public void onMessage(String payload) {
        try {
            identityUserSyncService.syncUser(payload);
        } catch (Exception e) {
            log.error("사용자 동기화 이벤트 처리 실패. payload: {}, error: {}", payload, e.getMessage(), e);
            throw new AmqpRejectAndDontRequeueException(e);
        }
    }
}
