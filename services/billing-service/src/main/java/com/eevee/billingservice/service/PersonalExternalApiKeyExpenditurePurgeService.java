package com.eevee.billingservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes billing aggregates for a personal Identity external API key after physical delete.
 */
@Service
public class PersonalExternalApiKeyExpenditurePurgeService {

    private static final Logger log = LoggerFactory.getLogger(PersonalExternalApiKeyExpenditurePurgeService.class);

    private final BillingAggregationJdbc billingAggregationJdbc;

    public PersonalExternalApiKeyExpenditurePurgeService(BillingAggregationJdbc billingAggregationJdbc) {
        this.billingAggregationJdbc = billingAggregationJdbc;
    }

    @Transactional
    public void purgeForDeletedExternalApiKey(String userId, Long apiKeyId) {
        if (userId == null || userId.isBlank() || apiKeyId == null) {
            log.warn("Skip personal API key expenditure purge: missing userId or apiKeyId");
            return;
        }
        String trimmedUserId = userId.trim();
        String apiKeyIdStr = String.valueOf(apiKeyId);
        int[] counts = billingAggregationJdbc.deletePersonalAggregatesForExternalApiKey(trimmedUserId, apiKeyIdStr);
        log.info(
                "Purged personal external API key aggregates apiKeyId={} dailyRows={} monthlyRows={} seenRows={}",
                apiKeyIdStr,
                counts[0],
                counts[1],
                counts[2]
        );
    }
}
