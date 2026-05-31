package com.zerobugfreinds.ai_agent_service.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobugfreinds.ai_agent_service.service.ApiKeyUsageDataCleanupService;
import com.zerobugfreinds.ai_agent_service.service.EventDebugService;
import com.zerobugfreinds.ai_agent_service.service.TeamApiKeySnapshotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TeamApiKeyStatusEventListenerTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Mock
	private TeamApiKeySnapshotService snapshotService;

	@Mock
	private ApiKeyUsageDataCleanupService apiKeyUsageDataCleanupService;

	@Mock
	private EventDebugService eventDebugService;

	private TeamApiKeyStatusEventListener listener;

	@BeforeEach
	void setUp() {
		listener = new TeamApiKeyStatusEventListener(objectMapper, snapshotService, apiKeyUsageDataCleanupService, eventDebugService);
	}

	@Test
	void deletedWithoutRetainLogs_purgesAllUsageProjectionsAndDeletesSnapshot() throws Exception {
		String json =
				"{\"eventType\":\"TEAM_API_KEY_STATUS_CHANGED\",\"teamId\":1,\"teamApiKeyId\":42,\"status\":\"DELETED\","
						+ "\"retainLogs\":false,\"occurredAt\":\"2026-05-01T12:00:00Z\",\"alias\":\"a\",\"provider\":\"OPENAI\"}";
		Message message = MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8)).build();

		listener.onMessage(message);

		verify(apiKeyUsageDataCleanupService).purgeByApiKeyId("42");
		verify(apiKeyUsageDataCleanupService, never()).purgeUsageProjectionsExcludingBillingSignals(any());
		verify(snapshotService).delete(1L, 42L);
		verify(snapshotService, never()).upsert(any());
	}

	@Test
	void deletedWithRetainLogsTrue_doesNotPurge() throws Exception {
		String json =
				"{\"eventType\":\"TEAM_API_KEY_STATUS_CHANGED\",\"teamId\":1,\"teamApiKeyId\":43,\"status\":\"DELETED\","
						+ "\"retainLogs\":true,\"occurredAt\":\"2026-05-01T12:00:00Z\"}";
		Message message = MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8)).build();

		listener.onMessage(message);

		verifyNoInteractions(apiKeyUsageDataCleanupService);
	}
}
