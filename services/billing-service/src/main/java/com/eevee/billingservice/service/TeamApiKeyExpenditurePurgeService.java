package com.eevee.billingservice.service;

import com.eevee.billingservice.repository.BillingTeamApiKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes billing aggregates and the team API key read-model row after physical delete.
 */
@Service
public class TeamApiKeyExpenditurePurgeService {

    private static final Logger log = LoggerFactory.getLogger(TeamApiKeyExpenditurePurgeService.class);

    private final TeamApiKeyAggregationJdbc teamApiKeyAggregationJdbc;
    private final BillingTeamApiKeyRepository teamApiKeyRepository;

    public TeamApiKeyExpenditurePurgeService(
            TeamApiKeyAggregationJdbc teamApiKeyAggregationJdbc,
            BillingTeamApiKeyRepository teamApiKeyRepository
    ) {
        this.teamApiKeyAggregationJdbc = teamApiKeyAggregationJdbc;
        this.teamApiKeyRepository = teamApiKeyRepository;
    }

    @Transactional
    public void purgeForDeletedTeamApiKey(long teamApiKeyId) {
        int[] counts = teamApiKeyAggregationJdbc.deleteAggregatesForTeamApiKey(teamApiKeyId);
        log.info(
                "Purged team API key aggregates teamApiKeyId={} dailyRows={} monthlyRows={}",
                teamApiKeyId,
                counts[0],
                counts[1]
        );
        teamApiKeyRepository.findById(teamApiKeyId).ifPresent(teamApiKeyRepository::delete);
    }
}
