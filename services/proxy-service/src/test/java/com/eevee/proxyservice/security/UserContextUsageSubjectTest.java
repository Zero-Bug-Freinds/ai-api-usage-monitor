package com.eevee.proxyservice.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserContextUsageSubjectTest {

    @Test
    void usageEventUserId_normalizesEmailFromGatewayHeader() {
        UserContext ctx = new UserContext(
                "User@Example.COM",
                "3",
                null,
                null,
                "c",
                null,
                null,
                null,
                null,
                null
        );
        assertThat(ctx.usageEventUserId()).isEqualTo("user@example.com");
        assertThat(ctx.keyLookupUserId()).isEqualTo("3");
    }

    @Test
    void usageEventUserId_fallsBackToExtUserIdWhenGatewayUserMissing() {
        UserContext ctx = new UserContext(
                null,
                null,
                null,
                null,
                "c",
                null,
                null,
                "ext-subject@team.io",
                null,
                null
        );
        assertThat(ctx.usageEventUserId()).isEqualTo("ext-subject@team.io");
    }
}
