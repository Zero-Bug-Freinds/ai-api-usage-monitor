package com.zerobugfreinds.ai_agent_service.mq;

import com.eevee.usage.events.UsageCostFinalizedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zerobugfreinds.ai_agent_service.dto.BillingCostCorrectedEvent;
import com.zerobugfreinds.ai_agent_service.service.BillingSignalSnapshotService;
import com.zerobugfreinds.ai_agent_service.service.EventDebugService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BillingOutboundEventListenerTest {

	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

	@Mock
	private BillingSignalSnapshotService billingSignalSnapshotService;

	@Mock
	private EventDebugService eventDebugService;

	private BillingOutboundEventListener listener;

	@BeforeEach
	void setUp() {
		listener = new BillingOutboundEventListener(objectMapper, billingSignalSnapshotService, eventDebugService);
	}

	@Test
	void usageCostFinalized_resolvesApiKeyIdFromCaseInsensitiveHeader() throws Exception {
		String body =
				"{\"schemaVersion\":1,\"eventId\":\"550e8400-e29b-41d4-a716-446655440001\","
						+ "\"estimatedCostUsd\":0.02,\"finalizedAt\":\"2026-05-10T10:00:00Z\"}";
		Message message = MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
				.setHeader("API_KEY_ID", "88")
				.setHeader("userId", "u1")
				.build();

		listener.onUsageCostFinalized(message);

		ArgumentCaptor<UsageCostFinalizedEvent> eventCaptor = ArgumentCaptor.forClass(UsageCostFinalizedEvent.class);
		verify(billingSignalSnapshotService).upsertUsageCost(eq("88"), eq("u1"), eq(null), eq(null), eventCaptor.capture());
		assertThat(eventCaptor.getValue().estimatedCostUsd()).isEqualByComparingTo("0.02");
	}

	@Test
	void usageCostFinalized_fallsBackToApiKeyIdInJsonBodyWhenHeadersAbsent() throws Exception {
		String body =
				"{\"schemaVersion\":1,\"eventId\":\"550e8400-e29b-41d4-a716-446655440002\","
						+ "\"estimatedCostUsd\":0.03,\"finalizedAt\":\"2026-05-10T10:00:00Z\",\"apiKeyId\":\"77\"}";
		Message message = MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8)).build();

		listener.onUsageCostFinalized(message);

		verify(billingSignalSnapshotService).upsertUsageCost(eq("77"), eq(null), eq(null), eq(null), any());
	}

	@Test
	void usageCostFinalized_numericKeyIdInJsonBody() throws Exception {
		String body =
				"{\"schemaVersion\":1,\"eventId\":\"550e8400-e29b-41d4-a716-446655440003\","
						+ "\"estimatedCostUsd\":0.01,\"finalizedAt\":\"2026-05-10T10:00:00Z\",\"teamApiKeyId\":901}";
		Message message = MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8)).build();

		listener.onUsageCostFinalized(message);

		verify(billingSignalSnapshotService).upsertUsageCost(eq("901"), eq(null), eq(null), eq(null), any());
	}

	@Test
	void usageCostFinalized_throwsWhenApiKeyIdMissing() {
		String body =
				"{\"schemaVersion\":1,\"eventId\":\"550e8400-e29b-41d4-a716-446655440004\","
						+ "\"estimatedCostUsd\":0.01,\"finalizedAt\":\"2026-05-10T10:00:00Z\"}";
		Message message = MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8)).build();

		assertThatThrownBy(() -> listener.onUsageCostFinalized(message))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("usage cost finalized handling failed");
	}

	@Test
	void billingCostCorrected_throwsWhenApiKeyIdMissing() {
		String body =
				"{\"schemaVersion\":1,\"occurredAt\":\"2026-05-10T10:00:00Z\",\"correctionEventId\":\"660e8400-e29b-41d4-a716-446655440000\","
						+ "\"userId\":\"u\",\"monthStartDate\":\"2026-05-01\",\"appliedDeltaCostUsd\":0.01}";
		Message message = MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8)).build();

		assertThatThrownBy(() -> listener.onBillingCostCorrected(message))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("billing cost corrected handling failed");
	}

	@Test
	void billingCostCorrected_usesPayloadApiKeyId() throws Exception {
		String body =
				"{\"schemaVersion\":1,\"occurredAt\":\"2026-05-10T10:00:00Z\","
						+ "\"correctionEventId\":\"660e8400-e29b-41d4-a716-446655440000\","
						+ "\"userId\":\"u@x.com\",\"apiKeyId\":\"55\",\"monthStartDate\":\"2026-05-01\","
						+ "\"appliedDeltaCostUsd\":0.01,\"aggDate\":\"2026-05-10\",\"provider\":\"OPENAI\","
						+ "\"model\":\"gpt-4o-mini\",\"optionalCorrectedTotalUsdForScope\":null,"
						+ "\"relatedUsageEventId\":\"770e8400-e29b-41d4-a716-446655440000\"}";
		Message message = MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8)).build();

		listener.onBillingCostCorrected(message);

		ArgumentCaptor<BillingCostCorrectedEvent> captor = ArgumentCaptor.forClass(BillingCostCorrectedEvent.class);
		verify(billingSignalSnapshotService).applyCostCorrection(eq("55"), eq("u@x.com"), eq(null), eq(null), captor.capture());
		assertThat(captor.getValue().appliedDeltaCostUsd()).isEqualByComparingTo("0.01");
	}
}
