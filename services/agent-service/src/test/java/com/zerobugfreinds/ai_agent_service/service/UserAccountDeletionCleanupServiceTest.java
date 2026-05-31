package com.zerobugfreinds.ai_agent_service.service;

import com.zerobugfreinds.ai_agent_service.repository.AgentAccountDeletionJdbc;
import com.zerobugfreinds.identity.events.UserAccountDeletionRequestedEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAccountDeletionCleanupServiceTest {

	@Test
	void cleanup_purgesForEachLookupCandidate() {
		AgentAccountDeletionJdbc jdbc = mock(AgentAccountDeletionJdbc.class);
		when(jdbc.deleteAllProjectionsForUser("user@test.com")).thenReturn(new int[] {1, 2, 3, 4, 5, 6, 7, 8});
		when(jdbc.deleteAllProjectionsForUser("42")).thenReturn(new int[] {0, 0, 0, 0, 0, 0, 0, 0});

		UserAccountDeletionCleanupService service = new UserAccountDeletionCleanupService(jdbc);
		UserAccountDeletionRequestedEvent event = UserAccountDeletionRequestedEvent.of(42L, "user@test.com");

		UserAccountDeletionCleanupService.CleanupResult result = service.cleanup(event);

		verify(jdbc).deleteAllProjectionsForUser("user@test.com");
		verify(jdbc).deleteAllProjectionsForUser("42");
		assertThat(result.deletedIdentityApiKeyRows()).isEqualTo(1);
		assertThat(result.deletedBillingSignalRows()).isEqualTo(2);
	}

	@Test
	void resolveLookupCandidates_includesEmailAndNumericId() {
		UserAccountDeletionRequestedEvent event = UserAccountDeletionRequestedEvent.of(7L, "a@b.com");
		assertThat(UserAccountDeletionCleanupService.resolveLookupCandidates(event))
				.containsExactly("a@b.com", "7");
	}
}
