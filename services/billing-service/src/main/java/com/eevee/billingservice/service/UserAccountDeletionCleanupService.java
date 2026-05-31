package com.eevee.billingservice.service;

import com.zerobugfreinds.identity.events.UserAccountDeletionRequestedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Purges personal billing aggregates when Identity requests account deletion.
 */
@Service
public class UserAccountDeletionCleanupService {

    private final BillingAggregationJdbc billingAggregationJdbc;

    public UserAccountDeletionCleanupService(BillingAggregationJdbc billingAggregationJdbc) {
        this.billingAggregationJdbc = billingAggregationJdbc;
    }

    @Transactional
    public CleanupResult cleanup(UserAccountDeletionRequestedEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event is required");
        }
        List<String> candidates = resolveLookupCandidates(event);
        int daily = 0;
        int monthly = 0;
        int seen = 0;
        for (String candidate : candidates) {
            int[] counts = billingAggregationJdbc.deleteAllPersonalAggregatesForUser(candidate);
            daily += counts[0];
            monthly += counts[1];
            seen += counts[2];
        }
        return new CleanupResult(daily, monthly, seen);
    }

    static List<String> resolveLookupCandidates(UserAccountDeletionRequestedEvent event) {
        Set<String> candidates = new LinkedHashSet<>();
        if (StringUtils.hasText(event.userEmail())) {
            candidates.add(event.userEmail().trim());
        }
        candidates.add(String.valueOf(event.identityUserId()));
        return new ArrayList<>(candidates);
    }

    public record CleanupResult(int deletedDailyRows, int deletedMonthlyRows, int deletedSeenRows) {
    }
}
