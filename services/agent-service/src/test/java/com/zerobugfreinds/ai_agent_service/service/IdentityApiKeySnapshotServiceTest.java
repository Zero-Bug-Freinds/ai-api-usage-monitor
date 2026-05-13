package com.zerobugfreinds.ai_agent_service.service;

import com.zerobugfreinds.ai_agent_service.entity.IdentityApiKeySnapshotEntity;
import com.zerobugfreinds.ai_agent_service.repository.IdentityApiKeySnapshotRepository;
import com.zerobugfreinds.identity.events.ExternalApiKeyDeletedEvent;
import com.zerobugfreinds.identity.events.IdentityExternalApiKeyEventTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityApiKeySnapshotServiceTest {

	@Mock
	private IdentityApiKeySnapshotRepository snapshotRepository;

	@InjectMocks
	private IdentityApiKeySnapshotService snapshotService;

	@Test
	void applyDeleted_withRetainLogsTrue_keepsSnapshotAsDeleted() {
		ExternalApiKeyDeletedEvent event = new ExternalApiKeyDeletedEvent(
				IdentityExternalApiKeyEventTypes.EXTERNAL_API_KEY_DELETED,
				"user-1",
				101L,
				Instant.parse("2026-05-13T07:00:00Z"),
				true,
				"openai",
				"renamed"
		);
		IdentityApiKeySnapshotEntity current = new IdentityApiKeySnapshotEntity(
				"user-1",
				101L,
				"old-alias",
				"old-provider",
				"PERSONAL",
				"ACTIVE",
				new BigDecimal("12.34"),
				"abc123",
				Instant.parse("2026-05-12T00:00:00Z")
		);
		when(snapshotRepository.findByUserIdAndKeyId("user-1", 101L)).thenReturn(Optional.of(current));

		snapshotService.applyDeleted(event);

		ArgumentCaptor<IdentityApiKeySnapshotEntity> captor = ArgumentCaptor.forClass(IdentityApiKeySnapshotEntity.class);
		verify(snapshotRepository).save(captor.capture());
		verify(snapshotRepository, never()).deleteByUserIdAndKeyId("user-1", 101L);

		IdentityApiKeySnapshotEntity saved = captor.getValue();
		assertThat(saved.getUserId()).isEqualTo("user-1");
		assertThat(saved.getKeyId()).isEqualTo(101L);
		assertThat(saved.getStatus()).isEqualTo("DELETED");
		assertThat(saved.getAlias()).isEqualTo("renamed");
		assertThat(saved.getProvider()).isEqualTo("openai");
		assertThat(saved.getVisibility()).isEqualTo("PERSONAL");
		assertThat(saved.getMonthlyBudgetUsd()).isEqualByComparingTo("12.34");
		assertThat(saved.getKeyHash()).isEqualTo("abc123");
		assertThat(saved.getUpdatedAt()).isEqualTo(Instant.parse("2026-05-13T07:00:00Z"));
	}

	@Test
	void applyDeleted_withRetainLogsFalse_keepsDeletedSnapshot() {
		ExternalApiKeyDeletedEvent event = new ExternalApiKeyDeletedEvent(
				IdentityExternalApiKeyEventTypes.EXTERNAL_API_KEY_DELETED,
				"user-2",
				202L,
				Instant.parse("2026-05-13T08:00:00Z"),
				false,
				"anthropic",
				"to-delete"
		);
		when(snapshotRepository.findByUserIdAndKeyId("user-2", 202L)).thenReturn(Optional.empty());

		snapshotService.applyDeleted(event);

		ArgumentCaptor<IdentityApiKeySnapshotEntity> captor = ArgumentCaptor.forClass(IdentityApiKeySnapshotEntity.class);
		verify(snapshotRepository, never()).deleteByUserIdAndKeyId("user-2", 202L);
		verify(snapshotRepository).save(captor.capture());

		IdentityApiKeySnapshotEntity saved = captor.getValue();
		assertThat(saved.getUserId()).isEqualTo("user-2");
		assertThat(saved.getKeyId()).isEqualTo(202L);
		assertThat(saved.getStatus()).isEqualTo("DELETED");
		assertThat(saved.getAlias()).isEqualTo("to-delete");
		assertThat(saved.getProvider()).isEqualTo("anthropic");
	}
}
