package com.zerobugfreinds.ai_agent_service.service;

import com.zerobugfreinds.ai_agent_service.dto.PolicyRecommendationRequest;
import com.zerobugfreinds.ai_agent_service.dto.PolicyRecommendationResponse;
import com.zerobugfreinds.ai_agent_service.dto.RecommendationLevel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PolicyRecommendationAgentServiceTest {

	@Test
	void recommend_returnsWarn_whenUtilizationOver80Percent() {
		PolicyRecommendationAgentService service = new PolicyRecommendationAgentService(
				mock(BillingSignalSnapshotService.class),
				mock(ExternalModelCatalogService.class),
				mock(DailyCumulativeTokenSnapshotService.class),
				mock(UsagePredictionSignalSnapshotService.class),
				mock(UsageRecordedTokenRollupService.class)
		);
		PolicyRecommendationRequest request = new PolicyRecommendationRequest(
				"user@test.com",
				"team-1",
				"OPENAI",
				"gpt-4o",
				BigDecimal.valueOf(100),
				BigDecimal.valueOf(85)
		);

		PolicyRecommendationResponse response = service.recommend(request);

		assertThat(response.recommendationLevel()).isEqualTo(RecommendationLevel.WARN);
		assertThat(response.utilizationRatePercent()).isEqualByComparingTo("85.00");
	}
}
