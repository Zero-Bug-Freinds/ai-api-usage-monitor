package com.eevee.proxyservice.key;

/**
 * Owner metadata from a successful fingerprint reverse lookup (before plain-key hydration).
 */
public record FingerprintOwnerLookup(
        String ownerType,
        String userId,
        String teamId,
        String keyId,
        String alias,
        String keySource,
        String status
) {
    public boolean isPersonal() {
        return "PERSONAL".equalsIgnoreCase(ownerType);
    }

    public boolean isTeam() {
        return "TEAM".equalsIgnoreCase(ownerType);
    }
}
