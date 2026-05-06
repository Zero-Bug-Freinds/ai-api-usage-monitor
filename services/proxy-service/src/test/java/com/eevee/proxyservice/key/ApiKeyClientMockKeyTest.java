package com.eevee.proxyservice.key;

import com.eevee.proxyservice.config.ProxyProperties;
import com.eevee.usage.events.AiProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyClientMockKeyTest {

    @Test
    void openaiUsesMockKeyOpenaiWhenSet() {
        ProxyProperties props = baseProps();
        props.getKeyService().setMockKeyOpenai("sk-specific");
        props.getKeyService().setMockKeyGoogle("AIza-google");
        props.getKeyService().setMockKey("legacy");

        ApiKeyClient client = new ApiKeyClient(props);
        ApiKeyClient.ResolvedApiKey resolved = client.resolveApiKey("user-1", null, AiProvider.OPENAI).block();
        assertThat(resolved).isNotNull();
        assertThat(resolved.plainKey()).isEqualTo("sk-specific");
        assertThat(resolved.keyId()).isNull();
        assertThat(resolved.keySource()).isEqualTo("mock");
    }

    @Test
    void googleUsesMockKeyGoogleWhenSet() {
        ProxyProperties props = baseProps();
        props.getKeyService().setMockKeyOpenai("sk-specific");
        props.getKeyService().setMockKeyGoogle("AIza-google");
        props.getKeyService().setMockKey("legacy");

        ApiKeyClient client = new ApiKeyClient(props);
        ApiKeyClient.ResolvedApiKey resolved = client.resolveApiKey("user-1", null, AiProvider.GOOGLE).block();
        assertThat(resolved).isNotNull();
        assertThat(resolved.plainKey()).isEqualTo("AIza-google");
        assertThat(resolved.keySource()).isEqualTo("mock");
    }

    @Test
    void fallsBackToLegacyMockKeyWhenProviderSpecificBlank() {
        ProxyProperties props = baseProps();
        props.getKeyService().setMockKeyOpenai("");
        props.getKeyService().setMockKeyGoogle("");
        props.getKeyService().setMockKey("legacy-only");

        ApiKeyClient client = new ApiKeyClient(props);
        ApiKeyClient.ResolvedApiKey openai = client.resolveApiKey("user-1", null, AiProvider.OPENAI).block();
        ApiKeyClient.ResolvedApiKey google = client.resolveApiKey("user-1", null, AiProvider.GOOGLE).block();
        assertThat(openai).isNotNull();
        assertThat(openai.plainKey()).isEqualTo("legacy-only");
        assertThat(google).isNotNull();
        assertThat(google.plainKey()).isEqualTo("legacy-only");
    }

    private static ProxyProperties baseProps() {
        ProxyProperties props = new ProxyProperties();
        props.getKeyService().setBaseUrl("http://localhost:0");
        props.getKeyService().setCacheTtl("PT1S");
        return props;
    }
}
