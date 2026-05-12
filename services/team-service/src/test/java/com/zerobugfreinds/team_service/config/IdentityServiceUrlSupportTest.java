package com.zerobugfreinds.team_service.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityServiceUrlSupportTest {

	@Test
	void primaryBaseUrl_stripsTrailingSlashes() {
		IdentityServiceUrlSupport support = new IdentityServiceUrlSupport("http://identity-service:8090/", true);
		assertThat(support.primaryBaseUrl()).isEqualTo("http://identity-service:8090");
	}

	@Test
	void apiKeyLookup_fallbackDisabled_singleCandidate() {
		IdentityServiceUrlSupport support = new IdentityServiceUrlSupport("http://identity-service:8090", false);
		assertThat(support.apiKeyLookupBaseUrls()).containsExactly("http://identity-service:8090");
	}

	@Test
	void apiKeyLookup_hostDockerInternalWithFallback_addsDevCandidates() {
		IdentityServiceUrlSupport support = new IdentityServiceUrlSupport("http://host.docker.internal:8090", true);
		assertThat(support.apiKeyLookupBaseUrls())
				.containsExactly(
						"http://host.docker.internal:8090",
						"http://localhost:8090",
						"http://identity-service:8090"
				);
	}
}
