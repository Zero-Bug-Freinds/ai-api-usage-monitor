package com.zerobugfreinds.ai_agent_service.service;

import com.zerobugfreinds.ai_agent_service.repository.AgentAccountDeletionJdbc;
import com.zerobugfreinds.identity.events.UserAccountDeletionRequestedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Purges agent projections when Identity requests account deletion (ACK 게이트 밖, eventual cleanup).
 */
@Service
public class UserAccountDeletionCleanupService {

	private final AgentAccountDeletionJdbc agentAccountDeletionJdbc;

	public UserAccountDeletionCleanupService(AgentAccountDeletionJdbc agentAccountDeletionJdbc) {
		this.agentAccountDeletionJdbc = agentAccountDeletionJdbc;
	}

	@Transactional
	public CleanupResult cleanup(UserAccountDeletionRequestedEvent event) {
		if (event == null) {
			throw new IllegalArgumentException("event is required");
		}
		List<String> candidates = resolveLookupCandidates(event);
		int identityKeys = 0;
		int billingSignals = 0;
		int dailyTokens = 0;
		int predictionSignals = 0;
		int tokenRollups = 0;
		int budgetForecasts = 0;
		int recommendations = 0;
		int teamKeysByOwner = 0;
		for (String candidate : candidates) {
			int[] counts = agentAccountDeletionJdbc.deleteAllProjectionsForUser(candidate);
			identityKeys += counts[0];
			billingSignals += counts[1];
			dailyTokens += counts[2];
			predictionSignals += counts[3];
			tokenRollups += counts[4];
			budgetForecasts += counts[5];
			recommendations += counts[6];
			teamKeysByOwner += counts[7];
		}
		return new CleanupResult(
				identityKeys,
				billingSignals,
				dailyTokens,
				predictionSignals,
				tokenRollups,
				budgetForecasts,
				recommendations,
				teamKeysByOwner
		);
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
			int deletedIdentityApiKeyRows,
			int deletedBillingSignalRows,
			int deletedDailyCumulativeTokenRows,
			int deletedUsagePredictionRows,
			int deletedTokenRollupRows,
			int deletedBudgetForecastRows,
			int deletedRecommendationRows,
			int deletedTeamApiKeyByOwnerRows
	) {
	}
}
