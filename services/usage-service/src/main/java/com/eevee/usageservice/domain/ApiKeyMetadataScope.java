package com.eevee.usageservice.domain;

/**
 * Distinguishes identity-managed keys from team-shared keys in {@link ApiKeyMetadataEntity}.
 */
public enum ApiKeyMetadataScope {
    PERSONAL,
    TEAM
}
