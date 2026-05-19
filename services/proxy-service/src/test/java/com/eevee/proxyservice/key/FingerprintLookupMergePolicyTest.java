package com.eevee.proxyservice.key;

import com.eevee.proxyservice.key.dto.InternalFingerprintLookupResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FingerprintLookupMergePolicyTest {

    @Test
    void merge_usesIdentityWhenOnlyIdentityFound() {
        FingerprintLookupAttempt identity = FingerprintLookupAttempt.found(personal("u_1", "k1"));
        FingerprintLookupAttempt team = FingerprintLookupAttempt.notFound();

        FingerprintOwnerLookup owner = FingerprintLookupMergePolicy.merge(identity, team, "9f86d081");

        assertThat(owner.isPersonal()).isTrue();
        assertThat(owner.keyId()).isEqualTo("k1");
    }

    @Test
    void merge_returns404WhenBothNotFound() {
        assertThatThrownBy(() -> FingerprintLookupMergePolicy.merge(
                FingerprintLookupAttempt.notFound(),
                FingerprintLookupAttempt.notFound(),
                "9f86d081"
        ))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void merge_returns502WhenOneNotFoundAndOtherGatewayError() {
        assertThatThrownBy(() -> FingerprintLookupMergePolicy.merge(
                FingerprintLookupAttempt.notFound(),
                FingerprintLookupAttempt.gatewayError(503),
                "9f86d081"
        ))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void merge_returns409WhenBothFound() {
        assertThatThrownBy(() -> FingerprintLookupMergePolicy.merge(
                FingerprintLookupAttempt.found(personal("u_1", "k1")),
                FingerprintLookupAttempt.found(team(42L, "k2")),
                "9f86d081"
        ))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void merge_treatsDeletionRequestedAsNotFound() {
        InternalFingerprintLookupResponse inactive = new InternalFingerprintLookupResponse(
                true,
                "PERSONAL",
                "u_1",
                null,
                "k1",
                "alias",
                "DELETION_REQUESTED",
                "managed"
        );
        assertThatThrownBy(() -> FingerprintLookupMergePolicy.merge(
                FingerprintLookupAttempt.found(inactive),
                FingerprintLookupAttempt.notFound(),
                "9f86d081"
        ))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    private static InternalFingerprintLookupResponse personal(String userId, String keyId) {
        return new InternalFingerprintLookupResponse(
                true, "PERSONAL", userId, null, keyId, "alias", "ACTIVE", "managed"
        );
    }

    private static InternalFingerprintLookupResponse team(Long teamId, String keyId) {
        return new InternalFingerprintLookupResponse(
                true, "TEAM", null, teamId, keyId, "alias", "ACTIVE", "team"
        );
    }
}
