package com.eevee.billingservice.service;

import com.zerobugfreinds.identity.events.UserAccountDeletionRequestedEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAccountDeletionCleanupServiceTest {

    @Test
    void cleanup_deletesAggregatesForEmailAndNumericIdCandidates() {
        BillingAggregationJdbc jdbc = mock(BillingAggregationJdbc.class);
        when(jdbc.deleteAllPersonalAggregatesForUser("user@test.com")).thenReturn(new int[] {2, 1, 3});
        when(jdbc.deleteAllPersonalAggregatesForUser("42")).thenReturn(new int[] {0, 0, 0});

        UserAccountDeletionCleanupService service = new UserAccountDeletionCleanupService(jdbc);
        UserAccountDeletionRequestedEvent event = UserAccountDeletionRequestedEvent.of(42L, " user@test.com ");

        UserAccountDeletionCleanupService.CleanupResult result = service.cleanup(event);

        assertThat(result.deletedDailyRows()).isEqualTo(2);
        assertThat(result.deletedMonthlyRows()).isEqualTo(1);
        assertThat(result.deletedSeenRows()).isEqualTo(3);
        verify(jdbc).deleteAllPersonalAggregatesForUser("user@test.com");
        verify(jdbc).deleteAllPersonalAggregatesForUser("42");
    }

    @Test
    void resolveLookupCandidates_includesEmailAndIdentityId() {
        UserAccountDeletionRequestedEvent event = UserAccountDeletionRequestedEvent.of(7L, "a@b.com");

        assertThat(UserAccountDeletionCleanupService.resolveLookupCandidates(event))
                .containsExactly("a@b.com", "7");
    }

    @Test
    void cleanup_requiresEvent() {
        UserAccountDeletionCleanupService service =
                new UserAccountDeletionCleanupService(mock(BillingAggregationJdbc.class));

        assertThatThrownBy(() -> service.cleanup(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("event is required");
    }
}
