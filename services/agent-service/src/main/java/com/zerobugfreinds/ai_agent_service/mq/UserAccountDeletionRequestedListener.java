package com.zerobugfreinds.ai_agent_service.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zerobugfreinds.ai_agent_service.service.UserAccountDeletionCleanupService;
import com.zerobugfreinds.identity.events.UserAccountDeletionRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Identity 회원 탈퇴 요청 시 agent 프로젝션을 정리한다. Identity ACK 게이트에는 포함되지 않는다.
 */
@Component
public class UserAccountDeletionRequestedListener {

	private static final Logger log = LoggerFactory.getLogger(UserAccountDeletionRequestedListener.class);

	private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());

	private final UserAccountDeletionCleanupService cleanupService;

	public UserAccountDeletionRequestedListener(UserAccountDeletionCleanupService cleanupService) {
		this.cleanupService = cleanupService;
	}

	@RabbitListener(queues = "${identity.account-deletion-event.agent.queue:agent.account-deletion.requested.queue}")
	public void onMessage(String body) {
		try {
			UserAccountDeletionRequestedEvent event = JSON.readValue(body, UserAccountDeletionRequestedEvent.class);
			UserAccountDeletionCleanupService.CleanupResult result = cleanupService.cleanup(event);
			log.info(
					"Handled UserAccountDeletionRequestedEvent identityUserId={} userEmail={} identityKeys={} billingSignals={} dailyTokens={} predictionSignals={} tokenRollups={} budgetForecasts={} recommendations={} teamKeysByOwner={}",
					event.identityUserId(),
					event.userEmail(),
					result.deletedIdentityApiKeyRows(),
					result.deletedBillingSignalRows(),
					result.deletedDailyCumulativeTokenRows(),
					result.deletedUsagePredictionRows(),
					result.deletedTokenRollupRows(),
					result.deletedBudgetForecastRows(),
					result.deletedRecommendationRows(),
					result.deletedTeamApiKeyByOwnerRows()
			);
		} catch (Exception e) {
			log.error("Invalid or failed UserAccountDeletionRequestedEvent payload: {}", e.getMessage());
			throw new AmqpRejectAndDontRequeueException(e);
		}
	}
}
