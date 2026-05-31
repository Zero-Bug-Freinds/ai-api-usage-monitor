package com.eevee.usageservice.service;

import com.eevee.usageservice.repository.UsageAccountDeletionJdbc;
import com.zerobugfreinds.identity.events.UserAccountDeletionRequestedEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAccountDeletionCleanupServiceTest {

    @Test
    void cleanup_deletesPersonalDataForLookupCandidates() {
        UsageAccountDeletionJdbc jdbc = mock(UsageAccountDeletionJdbc.class);
        when(jdbc.deletePersonalDataForUser("user@test.com")).thenReturn(new int[] {10, 2, 5, 1});
        when(jdbc.deletePersonalDataForUser("99")).thenReturn(new int[] {0, 0, 0, 0});

        UserAccountDeletionCleanupService service = new UserAccountDeletionCleanupService(jdbc);
        UserAccountDeletionRequestedEvent event = UserAccountDeletionRequestedEvent.of(99L, " user@test.com ");

        UserAccountDeletionCleanupService.CleanupResult result = service.cleanup(event);

        assertThat(result.deletedLogRows()).isEqualTo(10);
        assertThat(result.deletedMetadataRows()).isEqualTo(2);
        assertThat(result.deletedSummaryRows()).isEqualTo(5);
        assertThat(result.deletedCumulativeTokenRows()).isEqualTo(1);
        verify(jdbc).deletePersonalDataForUser("user@test.com");
        verify(jdbc).deletePersonalDataForUser("99");
    }

    @Test
    void resolveLookupCandidates_includesEmailAndIdentityId() {
        UserAccountDeletionRequestedEvent event = UserAccountDeletionRequestedEvent.of(3L, "x@y.z");

        assertThat(UserAccountDeletionCleanupService.resolveLookupCandidates(event))
                .containsExactly("x@y.z", "3");
    }

    @Test
    void cleanup_requiresEvent() {
        UserAccountDeletionCleanupService service =
                new UserAccountDeletionCleanupService(mock(UsageAccountDeletionJdbc.class));

        assertThatThrownBy(() -> service.cleanup(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("event is required");
    }
}
