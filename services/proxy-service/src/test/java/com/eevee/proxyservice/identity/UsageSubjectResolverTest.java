package com.eevee.proxyservice.identity;

import com.eevee.proxyservice.config.ProxyProperties;
import com.eevee.proxyservice.key.FingerprintOwnerLookup;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UsageSubjectResolverTest {

    @Test
    void personalOpaqueOwner_resolvesToNormalizedEmail() {
        IdentityUsageSubjectClient client = mock(IdentityUsageSubjectClient.class);
        when(client.resolveEmailFromOpaqueOwner("u_7")).thenReturn(Optional.of("owner@example.com"));

        UsageSubjectResolver resolver = resolver(client, true);
        FingerprintOwnerLookup owner = new FingerprintOwnerLookup(
                "PERSONAL", "u_7", null, "99", "alias", "managed", "ACTIVE"
        );

        assertThat(resolver.resolveForFingerprintOwner(owner, null)).isEqualTo("owner@example.com");
    }

    @Test
    void personalEmailOwner_onlyNormalizesCase() {
        IdentityUsageSubjectClient client = mock(IdentityUsageSubjectClient.class);
        UsageSubjectResolver resolver = resolver(client, true);
        FingerprintOwnerLookup owner = new FingerprintOwnerLookup(
                "PERSONAL", "User@Mail.COM", null, "99", "alias", "managed", "ACTIVE"
        );

        assertThat(resolver.resolveForFingerprintOwner(owner, null)).isEqualTo("user@mail.com");
    }

    @Test
    void teamOwner_usesGatewayFallbackEmail() {
        IdentityUsageSubjectClient client = mock(IdentityUsageSubjectClient.class);
        UsageSubjectResolver resolver = resolver(client, true);
        FingerprintOwnerLookup owner = new FingerprintOwnerLookup(
                "TEAM", null, "42", "77", "team-key", "team", "ACTIVE"
        );

        assertThat(resolver.resolveForFingerprintOwner(owner, "Member@Team.COM")).isEqualTo("member@team.com");
    }

    @Test
    void teamOwner_usesLookupUserIdWhenGatewayAbsent() {
        IdentityUsageSubjectClient client = mock(IdentityUsageSubjectClient.class);
        UsageSubjectResolver resolver = resolver(client, true);
        FingerprintOwnerLookup owner = new FingerprintOwnerLookup(
                "TEAM", "Registrar@Example.com", "42", "77", "team-key", "team", "ACTIVE"
        );

        assertThat(resolver.resolveForFingerprintOwner(owner, null)).isEqualTo("registrar@example.com");
    }

    @Test
    void teamOwner_gatewayPreferredOverLookupUserId() {
        IdentityUsageSubjectClient client = mock(IdentityUsageSubjectClient.class);
        UsageSubjectResolver resolver = resolver(client, true);
        FingerprintOwnerLookup owner = new FingerprintOwnerLookup(
                "TEAM", "Registrar@Example.com", "42", "77", "team-key", "team", "ACTIVE"
        );

        assertThat(resolver.resolveForFingerprintOwner(owner, "Caller@Team.com")).isEqualTo("caller@team.com");
    }

    @Test
    void personalOpaqueOwner_identityMiss_throws502() {
        IdentityUsageSubjectClient client = mock(IdentityUsageSubjectClient.class);
        when(client.resolveEmailFromOpaqueOwner(anyString())).thenReturn(Optional.empty());

        UsageSubjectResolver resolver = resolver(client, true);
        FingerprintOwnerLookup owner = new FingerprintOwnerLookup(
                "PERSONAL", "u_99", null, "1", "alias", "managed", "ACTIVE"
        );

        assertThatThrownBy(() -> resolver.resolveForFingerprintOwner(owner, "fallback@example.com"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(502));
    }

    private static UsageSubjectResolver resolver(IdentityUsageSubjectClient client, boolean enabled) {
        ProxyProperties props = new ProxyProperties();
        props.getUsageSubject().setEnabled(enabled);
        props.getUsageSubject().setCacheTtl("PT1S");
        return new UsageSubjectResolver(client, props);
    }
}
