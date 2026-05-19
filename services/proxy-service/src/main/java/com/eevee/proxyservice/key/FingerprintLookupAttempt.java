package com.eevee.proxyservice.key;

import com.eevee.proxyservice.key.dto.InternalFingerprintLookupResponse;

/**
 * Classified outcome of one fingerprint POST lookup call.
 */
public record FingerprintLookupAttempt(
        Kind kind,
        InternalFingerprintLookupResponse body,
        int httpStatus
) {
    public enum Kind {
        FOUND,
        NOT_FOUND,
        CONFLICT,
        BAD_REQUEST,
        GATEWAY_ERROR
    }

    public static FingerprintLookupAttempt found(InternalFingerprintLookupResponse body) {
        return new FingerprintLookupAttempt(Kind.FOUND, body, 200);
    }

    public static FingerprintLookupAttempt notFound() {
        return new FingerprintLookupAttempt(Kind.NOT_FOUND, null, 404);
    }

    public static FingerprintLookupAttempt conflict() {
        return new FingerprintLookupAttempt(Kind.CONFLICT, null, 409);
    }

    public static FingerprintLookupAttempt badRequest() {
        return new FingerprintLookupAttempt(Kind.BAD_REQUEST, null, 400);
    }

    public static FingerprintLookupAttempt gatewayError(int status) {
        return new FingerprintLookupAttempt(Kind.GATEWAY_ERROR, null, status);
    }
}
