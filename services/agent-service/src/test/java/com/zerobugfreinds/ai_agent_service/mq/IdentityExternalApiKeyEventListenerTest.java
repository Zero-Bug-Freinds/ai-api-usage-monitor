package com.zerobugfreinds.ai_agent_service.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobugfreinds.ai_agent_service.service.ApiKeyUsageDataCleanupService;
import com.zerobugfreinds.ai_agent_service.service.EventDebugService;
import com.zerobugfreinds.ai_agent_service.service.IdentityApiKeySnapshotService;
import com.zerobugfreinds.identity.events.ExternalApiKeyDeletedEvent;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IdentityExternalApiKeyEventListenerTest {

	@Mock
	private IdentityApiKeySnapshotService snapshotService;

	@Mock
	private ApiKeyUsageDataCleanupService apiKeyUsageDataCleanupService;

	@Mock
	private EventDebugService eventDebugService;

	private IdentityExternalApiKeyEventListener listener;

	@BeforeEach
	void setUp() {
		listener = new IdentityExternalApiKeyEventListener(
				new ObjectMapper(),
				snapshotService,
				apiKeyUsageDataCleanupService,
				eventDebugService
		);
	}

	@Test
	void deletedEvent_withoutRetainLogsField_defaultsToTrue() {
		String payload = """
				{
				  "eventType": "EXTERNAL_API_KEY_DELETED",
				  "userId": "user-1",
				  "apiKeyId": 101,
				  "occurredAt": "2026-05-13T07:00:00Z",
				  "provider": "openai",
				  "alias": "deleted-key"
				}
				""";
		Message message = MessageBuilder
				.withBody(payload.getBytes(StandardCharsets.UTF_8))
				.build();

		listener.onMessage(message);

		ArgumentCaptor<ExternalApiKeyDeletedEvent> captor = ArgumentCaptor.forClass(ExternalApiKeyDeletedEvent.class);
		verify(snapshotService).applyDeleted(captor.capture());
		verify(apiKeyUsageDataCleanupService, never()).purgeByApiKeyId("101");
		assertThat(captor.getValue().retainLogs()).isTrue();
	}
}
