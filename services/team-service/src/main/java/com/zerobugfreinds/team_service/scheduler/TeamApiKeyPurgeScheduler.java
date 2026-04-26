package com.zerobugfreinds.team_service.scheduler;

import com.zerobugfreinds.team_service.service.TeamApiKeyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TeamApiKeyPurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(TeamApiKeyPurgeScheduler.class);

    private final TeamApiKeyService teamApiKeyService;

    public TeamApiKeyPurgeScheduler(TeamApiKeyService teamApiKeyService) {
        this.teamApiKeyService = teamApiKeyService;
    }

    @Scheduled(
            fixedDelayString = "${team.api-key.purge-fixed-delay-ms:3600000}",
            initialDelayString = "${team.api-key.purge-initial-delay-ms:60000}"
    )
    public void purgeExpiredDeletions() {
        int purged = teamApiKeyService.purgeExpiredDeletions();
        if (purged > 0) {
            log.info("team_api_key purge completed removedRows={}", purged);
        }
    }
}
