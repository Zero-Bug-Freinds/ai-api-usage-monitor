package com.zerobugfreinds.team_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zerobugfreinds.identity.events.IdentityUserSyncEvent;
import com.zerobugfreinds.identity.events.IdentityUserSyncEventTypes;
import com.zerobugfreinds.team_service.entity.IdentityUserSyncEntity;
import com.zerobugfreinds.team_service.repository.IdentityUserSyncRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityUserSyncServiceTest {

    @Mock
    private IdentityUserSyncRepository identityUserSyncRepository;

    @Mock
    private IdentityUserLookupClient identityUserLookupClient;

    private IdentityUserSyncService identityUserSyncService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        identityUserSyncService = new IdentityUserSyncService(
                objectMapper,
                identityUserSyncRepository,
                identityUserLookupClient
        );
    }

    @Test
    void syncUser_acceptsJsonFromIdentityUserSyncEvent() throws Exception {
        IdentityUserSyncEvent outgoing = IdentityUserSyncEvent.of(
                IdentityUserSyncEventTypes.USER_REGISTERED,
                5L,
                "sync@test.com",
                "Sync User",
                Instant.parse("2026-01-02T03:04:05Z")
        );
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String json = mapper.writeValueAsString(outgoing);

        identityUserSyncService.syncUser(json);

        verify(identityUserSyncRepository).save(any(IdentityUserSyncEntity.class));
    }

    @Test
    void resolveMembershipLookupCandidates_blank_returnsEmpty() {
        assertThat(identityUserSyncService.resolveMembershipLookupCandidates(null)).isEmpty();
        assertThat(identityUserSyncService.resolveMembershipLookupCandidates("   ")).isEmpty();
    }

    @Test
    void resolveMembershipLookupCandidates_numericWithSyncRow_addsEmailFromSync() {
        IdentityUserSyncEntity sync = IdentityUserSyncEntity.create(
                "42",
                "member@test.com",
                "Member",
                "USER_UPDATED",
                Instant.now()
        );
        when(identityUserSyncRepository.findById("42")).thenReturn(java.util.Optional.of(sync));

        Set<String> candidates = identityUserSyncService.resolveMembershipLookupCandidates("42");

        assertThat(candidates).containsExactlyInAnyOrder("42", "member@test.com");
        verify(identityUserLookupClient).addResolvedPrincipalIdentifiers(eq("42"), any());
    }

    @Test
    void resolveMembershipLookupCandidates_emailWithSyncRow_addsNumericUserIdFromSync() {
        IdentityUserSyncEntity sync = IdentityUserSyncEntity.create(
                "99",
                "user@example.com",
                "U",
                "USER_UPDATED",
                Instant.now()
        );
        when(identityUserSyncRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(java.util.Optional.of(sync));

        Set<String> candidates = identityUserSyncService.resolveMembershipLookupCandidates("User@Example.com");

        assertThat(candidates).containsExactlyInAnyOrder("User@Example.com", "user@example.com", "99");
        verify(identityUserLookupClient).addResolvedPrincipalIdentifiers(eq("User@Example.com"), any());
    }

    @Test
    void resolveMembershipLookupCandidates_identityPrincipalAugmentsCandidates() {
        when(identityUserSyncRepository.findById("7")).thenReturn(java.util.Optional.empty());
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Set<String> set = invocation.getArgument(1);
            set.add("7");
            set.add("resolved@naver.com");
            return null;
        }).when(identityUserLookupClient).addResolvedPrincipalIdentifiers(eq("7"), any());

        Set<String> candidates = identityUserSyncService.resolveMembershipLookupCandidates("7");

        assertThat(candidates).containsExactlyInAnyOrder("7", "resolved@naver.com");
        verify(identityUserLookupClient).addResolvedPrincipalIdentifiers(eq("7"), any());
    }
}
