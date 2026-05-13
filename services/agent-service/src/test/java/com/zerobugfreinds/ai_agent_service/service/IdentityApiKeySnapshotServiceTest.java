package com.zerobugfreinds.ai_agent_service.service;

import com.zerobugfreinds.identity.events.ExternalApiKeyDeletedEvent;
import com.zerobugfreinds.identity.events.ExternalApiKeyStatus;
import com.zerobugfreinds.identity.events.ExternalApiKeyStatusChangedEvent;
import com.zerobugfreinds.identity.events.IdentityExternalApiKeyEventTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityApiKeySnapshotServiceTest {

	private IdentityApiKeySnapshotService snapshotService;

	@BeforeEach
	void setUp() {
		snapshotService = new IdentityApiKeySnapshotService();
	}

	@Test
	void delete_removesKeyFromProjection() {
		snapshotService.upsertStatus(
				ExternalApiKeyStatusChangedEvent.of(42L, "k", 7L, "OPENAI", ExternalApiKeyStatus.ACTIVE)
		);
		assertThat(snapshotService.findByUserId(7L)).hasSize(1);

		snapshotService.delete(
				ExternalApiKeyDeletedEvent.of(
						7L,
						42L,
						Instant.parse("2026-01-01T00:00:00Z"),
						false,
						"OPENAI",
						"k"
				)
		);

		assertThat(snapshotService.findByUserId(7L)).isEmpty();
	}

	@Test
	void delete_isNoOpWhenIdentifiersMissing() {
		snapshotService.upsertStatus(
				ExternalApiKeyStatusChangedEvent.of(1L, "a", 2L, "p", ExternalApiKeyStatus.ACTIVE)
		);
		snapshotService.delete(
				new ExternalApiKeyDeletedEvent(
						IdentityExternalApiKeyEventTypes.EXTERNAL_API_KEY_DELETED,
						null,
						1L,
						Instant.now(),
						false,
						"p",
						"a"
				)
		);
		assertThat(snapshotService.findByUserId(2L)).hasSize(1);
	}
}
