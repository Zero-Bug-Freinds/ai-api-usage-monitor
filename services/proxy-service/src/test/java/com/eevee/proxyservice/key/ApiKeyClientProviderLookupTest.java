package com.eevee.proxyservice.key;

import com.eevee.usage.events.AiProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyClientProviderLookupTest {

    @Test
    void googleLookupUsesCanonicalGoogleSegmentOnly() {
        assertThat(ApiKeyClient.providerSegmentsForLookup(AiProvider.GOOGLE))
                .containsExactly("google");
    }

    @Test
    void openaiLookupUsesOnlyOpenaiSegment() {
        assertThat(ApiKeyClient.providerSegmentsForLookup(AiProvider.OPENAI))
                .containsExactly("openai");
    }
}
