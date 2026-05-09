package com.zerobugfreinds.team_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobugfreinds.team_service.entity.IdentityUserSyncEntity;
import com.zerobugfreinds.team_service.repository.IdentityUserSyncRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdentityUserSyncServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void resolveMembershipLookupCandidates_numericId_identityEmail_mapsToEmailCandidate() {
        IdentityUserSyncRepository repo = mock(IdentityUserSyncRepository.class);
        IdentityUserLookupClient lookup = mock(IdentityUserLookupClient.class);
        when(repo.findById("99")).thenReturn(Optional.empty());
        when(lookup.findEmailByUserId("99")).thenReturn("dpsk1515@naver.com");

        IdentityUserSyncService service = new IdentityUserSyncService(objectMapper, repo, lookup);

        Set<String> candidates = service.resolveMembershipLookupCandidates("99");
        assertThat(candidates).contains("99", "dpsk1515@naver.com");
        verify(lookup).findEmailByUserId("99");
    }

    @Test
    void resolveMembershipLookupCandidates_numericId_prefersSyncTableEmailThenIdentity() {
        IdentityUserSyncRepository repo = mock(IdentityUserSyncRepository.class);
        IdentityUserLookupClient lookup = mock(IdentityUserLookupClient.class);
        IdentityUserSyncEntity syncRow = IdentityUserSyncEntity.create(
                "99",
                "from-sync@naver.com",
                "n",
                "USER_SYNC",
                Instant.parse("2025-01-01T00:00:00Z")
        );
        when(repo.findById("99")).thenReturn(Optional.of(syncRow));
        when(lookup.findEmailByUserId("99")).thenReturn("from-identity@naver.com");

        IdentityUserSyncService service = new IdentityUserSyncService(objectMapper, repo, lookup);

        Set<String> candidates = service.resolveMembershipLookupCandidates("99");
        assertThat(candidates).contains("99", "from-sync@naver.com", "from-identity@naver.com");
    }

    @Test
    void resolveMembershipLookupCandidates_email_findsLinkedNumericUserIdInSync() {
        IdentityUserSyncRepository repo = mock(IdentityUserSyncRepository.class);
        IdentityUserLookupClient lookup = mock(IdentityUserLookupClient.class);
        IdentityUserSyncEntity syncRow = IdentityUserSyncEntity.create(
                "100",
                "member@test.com",
                "n",
                "USER_SYNC",
                Instant.parse("2025-01-01T00:00:00Z")
        );
        when(repo.findByEmailIgnoreCase("member@test.com")).thenReturn(Optional.of(syncRow));

        IdentityUserSyncService service = new IdentityUserSyncService(objectMapper, repo, lookup);

        Set<String> candidates = service.resolveMembershipLookupCandidates("Member@Test.Com");
        assertThat(candidates).contains("Member@Test.Com", "member@test.com", "100");
    }
}
