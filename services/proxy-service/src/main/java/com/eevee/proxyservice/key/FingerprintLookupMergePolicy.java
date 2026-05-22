package com.eevee.proxyservice.key;

import com.eevee.proxyservice.key.dto.InternalFingerprintLookupResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Objects;

/**
 * Merges parallel identity + team fingerprint POST outcomes per product policy.
 */
public final class FingerprintLookupMergePolicy {

    private static final Logger log = LoggerFactory.getLogger(FingerprintLookupMergePolicy.class);

    private FingerprintLookupMergePolicy() {
    }

    public static FingerprintOwnerLookup merge(
            FingerprintLookupAttempt identity,
            FingerprintLookupAttempt team,
            String fingerprintPrefix
    ) {
        FingerprintLookupAttempt identityNorm = normalizeFound(identity);
        FingerprintLookupAttempt teamNorm = normalizeFound(team);

        boolean identityFound = identityNorm.kind() == FingerprintLookupAttempt.Kind.FOUND;
        boolean teamFound = teamNorm.kind() == FingerprintLookupAttempt.Kind.FOUND;

        if (identityFound && teamFound) {
            log.warn(
                    "[AUDIT] fingerprint_lookup_duplicate_policy fingerprintPrefix={} identityKeyId={} teamKeyId={}",
                    fingerprintPrefix,
                    mask(identityNorm.body().keyId()),
                    mask(teamNorm.body().keyId())
            );
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "동일한 API 키가 개인·팀에 중복 등록되어 있습니다"
            );
        }
        if (identityFound) {
            return toOwner(identityNorm.body());
        }
        if (teamFound) {
            return toOwner(teamNorm.body());
        }

        if (identityNorm.kind() == FingerprintLookupAttempt.Kind.CONFLICT
                || teamNorm.kind() == FingerprintLookupAttempt.Kind.CONFLICT) {
            log.warn("[AUDIT] fingerprint_lookup_duplicate_policy fingerprintPrefix={}", fingerprintPrefix);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "동일한 API 키 fingerprint 가 중복됩니다");
        }
        if (identityNorm.kind() == FingerprintLookupAttempt.Kind.BAD_REQUEST
                || teamNorm.kind() == FingerprintLookupAttempt.Kind.BAD_REQUEST) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fingerprint lookup request invalid");
        }

        boolean identityNotFound = identityNorm.kind() == FingerprintLookupAttempt.Kind.NOT_FOUND;
        boolean teamNotFound = teamNorm.kind() == FingerprintLookupAttempt.Kind.NOT_FOUND;
        if (identityNotFound && teamNotFound) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않은 API key 입니다");
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "fingerprint lookup partially failed"
        );
    }

    private static FingerprintLookupAttempt normalizeFound(FingerprintLookupAttempt attempt) {
        if (attempt.kind() != FingerprintLookupAttempt.Kind.FOUND || attempt.body() == null) {
            return attempt;
        }
        if (!isRelayActiveStatus(attempt.body().status())) {
            return FingerprintLookupAttempt.notFound();
        }
        return attempt;
    }

    static boolean isRelayActiveStatus(String status) {
        if (status == null || status.isBlank()) {
            return true;
        }
        return Objects.equals(status.trim().toUpperCase(Locale.ROOT), "ACTIVE");
    }

    private static FingerprintOwnerLookup toOwner(InternalFingerprintLookupResponse body) {
        String teamId = body.teamId() != null ? String.valueOf(body.teamId()) : null;
        return new FingerprintOwnerLookup(
                body.ownerType(),
                body.userId(),
                teamId,
                body.keyId(),
                body.alias(),
                body.keySource(),
                body.status()
        );
    }

    private static String mask(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        int keep = Math.min(4, value.length());
        return value.substring(0, keep) + "***";
    }
}
