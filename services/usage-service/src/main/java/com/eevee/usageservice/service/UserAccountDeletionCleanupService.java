package com.eevee.usageservice.service;

import com.eevee.usageservice.repository.UsageAccountDeletionJdbc;
import com.zerobugfreinds.identity.events.UserAccountDeletionRequestedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Purges personal usage data when Identity requests account deletion.
 * Team-context usage rows are retained (team audit trail).
 */
@Service
public class UserAccountDeletionCleanupService {

    private final UsageAccountDeletionJdbc usageAccountDeletionJdbc;

    public UserAccountDeletionCleanupService(UsageAccountDeletionJdbc usageAccountDeletionJdbc) {
        this.usageAccountDeletionJdbc = usageAccountDeletionJdbc;
    }

    @Transactional
    public CleanupResult cleanup(UserAccountDeletionRequestedEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event is required");
        }
        List<String> candidates = resolveLookupCandidates(event);
        int logs = 0;
        int metadata = 0;
        int summary = 0;
        int cumulative = 0;
        for (String candidate : candidates) {
            int[] counts = usageAccountDeletionJdbc.deletePersonalDataForUser(candidate);
            logs += counts[0];
            metadata += counts[1];
            summary += counts[2];
            cumulative += counts[3];
        }
        return new CleanupResult(logs, metadata, summary, cumulative);
    }

    static List<String> resolveLookupCandidates(UserAccountDeletionRequestedEvent event) {
        Set<String> candidates = new LinkedHashSet<>();
        if (StringUtils.hasText(event.userEmail())) {
            candidates.add(event.userEmail().trim());
        }
        candidates.add(String.valueOf(event.identityUserId()));
        return new ArrayList<>(candidates);
    }

    public record CleanupResult(
            int deletedLogRows,
            int deletedMetadataRows,
            int deletedSummaryRows,
            int deletedCumulativeTokenRows
    ) {
    }
}
