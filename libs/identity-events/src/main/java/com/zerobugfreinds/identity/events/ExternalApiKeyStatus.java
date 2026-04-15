package com.zerobugfreinds.identity.events;

/**
 * External API key lifecycle status used for cross-service synchronization.
 */
public enum ExternalApiKeyStatus {
	ACTIVE,
	DELETION_REQUESTED,
	DELETED
}
