package com.zerobugfreinds.ai_agent_service.service;

import com.zerobugfreinds.ai_agent_service.entity.IdentityApiKeySnapshotEntity;
import com.zerobugfreinds.ai_agent_service.repository.IdentityApiKeySnapshotRepository;
import com.zerobugfreinds.identity.events.ExternalApiKeyDeletedEvent;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
	void handleExternalApiKeyDeleted_retainLogsFalse_removesProjection() {
		ExternalApiKeyDeletedEvent event = ExternalApiKeyDeletedEvent.of(
				"user@example.com",
				42L,
				Instant.parse("2026-01-01T00:00:00Z"),
				false,
				"OPENAI",
				"k"
		);

		snapshotService.handleExternalApiKeyDeleted(event);

		verify(snapshotRepository).deleteByUserIdAndKeyId("user@example.com", 42L);
		verify(snapshotRepository, never()).save(any());
	}

	@Test
	void handleExternalApiKeyDeleted_retainLogsTrue_marksDeletedAndKeepsBudget() {
		IdentityApiKeySnapshotEntity current = new IdentityApiKeySnapshotEntity(
				"user@example.com",
				42L,
				"oldAlias",
				"OPENAI",
				"USER",
				"ACTIVE",
				new BigDecimal("12.50"),
				"abc-hash",
				Instant.EPOCH
		);
		when(snapshotRepository.findByUserIdAndKeyId("user@example.com", 42L)).thenReturn(Optional.of(current));

		ExternalApiKeyDeletedEvent event = ExternalApiKeyDeletedEvent.of(
				"user@example.com",
				42L,
				Instant.parse("2026-05-01T12:00:00Z"),
				true,
				"OPENAI",
				"atDelete"
		);

		snapshotService.handleExternalApiKeyDeleted(event);

		verify(snapshotRepository, never()).deleteByUserIdAndKeyId(any(), any());
		ArgumentCaptor<IdentityApiKeySnapshotEntity> captor = ArgumentCaptor.forClass(IdentityApiKeySnapshotEntity.class);
		verify(snapshotRepository).save(captor.capture());
		IdentityApiKeySnapshotEntity saved = captor.getValue();
		assertThat(saved.getStatus()).isEqualTo("DELETED");
		assertThat(saved.getAlias()).isEqualTo("atDelete");
		assertThat(saved.getProvider()).isEqualTo("OPENAI");
		assertThat(saved.getVisibility()).isEqualTo("USER");
		assertThat(saved.getMonthlyBudgetUsd()).isEqualByComparingTo(new BigDecimal("12.50"));
		assertThat(saved.getKeyHash()).isEqualTo("abc-hash");
		assertThat(saved.getUpdatedAt()).isEqualTo(Instant.parse("2026-05-01T12:00:00Z"));
	}

	@Test
	void handleExternalApiKeyDeleted_requiresUserIdAndKeyId() {
		ExternalApiKeyDeletedEvent event = ExternalApiKeyDeletedEvent.of(
				"",
				1L,
				Instant.now(),
				true,
				"p",
				"a"
		);

		assertThatThrownBy(() -> snapshotService.handleExternalApiKeyDeleted(event))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("userId");
	}
}
