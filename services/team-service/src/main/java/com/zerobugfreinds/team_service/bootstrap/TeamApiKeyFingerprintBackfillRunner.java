package com.zerobugfreinds.team_service.bootstrap;

import com.zerobugfreinds.team_service.service.TeamApiKeyFingerprintBackfillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(5000)
@ConditionalOnProperty(name = "api.internal.fingerprint-backfill.enabled", havingValue = "true")
public class TeamApiKeyFingerprintBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TeamApiKeyFingerprintBackfillRunner.class);

    private final TeamApiKeyFingerprintBackfillService backfillService;

    public TeamApiKeyFingerprintBackfillRunner(TeamApiKeyFingerprintBackfillService backfillService) {
        this.backfillService = backfillService;
    }

    @Override
    public void run(ApplicationArguments args) {
        int total = 0;
        while (true) {
            int n = backfillService.fillNextBatch();
            total += n;
            if (n == 0) {
                break;
            }
        }
        log.info("team_api_key fingerprint backfill runner finished updatedApprox={}", total);
    }
}
