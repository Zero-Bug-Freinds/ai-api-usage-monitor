package com.eevee.usageservice.service.bff.team;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TeamServiceClientMaskTest {

    @Test
    void maskUserIdForLog_opaqueShort() {
        assertThat(TeamServiceClient.maskUserIdForLog("123456789")).isEqualTo("1234***");
    }

    @Test
    void maskUserIdForLog_emailLike() {
        String m = TeamServiceClient.maskUserIdForLog("alice@example.com");
        assertThat(m).contains("@");
        assertThat(m).doesNotContain("alice");
        assertThat(m).doesNotContain("example.com");
    }

    @Test
    void userIdKind() {
        assertThat(TeamServiceClient.userIdKind("a@b.com")).isEqualTo("EMAIL_LIKE");
        assertThat(TeamServiceClient.userIdKind("42")).isEqualTo("OPAQUE_ID");
    }
}
