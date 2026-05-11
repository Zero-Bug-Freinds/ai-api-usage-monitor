package com.eevee.proxyservice.key;

import com.eevee.proxyservice.config.ProxyProperties;
import com.eevee.usage.events.AiProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiKeyClientReverseLookupTest {

    @Test
    void reverseLookupUsesRawApiKeyWhenNoSelectorProvided() {
        ProxyProperties props = baseProperties();
        ProxyProperties.ReverseLookupMock mock = new ProxyProperties.ReverseLookupMock();
        mock.setRawKey("sk-ext-raw");
        mock.setProvider("openai");
        mock.setKeyId("key-ext-1");
        mock.setStatus("ACTIVE");
        props.getKeyService().getReverseLookupMocks().add(mock);

        ApiKeyClient client = new ApiKeyClient(props);
        ApiKeyClient.ResolvedApiKey resolved = client.resolveApiKey(null, null, AiProvider.OPENAI, null, null, "sk-ext-raw")
                .block();

        assertThat(resolved).isNotNull();
        assertThat(resolved.keyId()).isEqualTo("key-ext-1");
        assertThat(resolved.plainKey()).isEqualTo("sk-ext-raw");
        assertThat(resolved.keySource()).isEqualTo("reverse_lookup");
    }

    @Test
    void reverseLookupRejectsInactiveMock() {
        ProxyProperties props = baseProperties();
        ProxyProperties.ReverseLookupMock mock = new ProxyProperties.ReverseLookupMock();
        mock.setRawKey("sk-ext-raw");
        mock.setProvider("openai");
        mock.setKeyId("key-ext-1");
        mock.setStatus("INACTIVE");
        props.getKeyService().getReverseLookupMocks().add(mock);

        ApiKeyClient client = new ApiKeyClient(props);
        assertThatThrownBy(() -> client.resolveApiKey(null, null, AiProvider.OPENAI, null, null, "sk-ext-raw").block())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void duplicateRawKeyAcrossPersonalAndTeamIsRejectedAtStartup() {
        ProxyProperties props = baseProperties();

        ProxyProperties.ReverseLookupMock personal = new ProxyProperties.ReverseLookupMock();
        personal.setRawKey("sk-dup");
        personal.setProvider("openai");
        personal.setKeyId("key-user");
        props.getKeyService().getReverseLookupMocks().add(personal);

        ProxyProperties.ReverseLookupMock team = new ProxyProperties.ReverseLookupMock();
        team.setRawKey("sk-dup");
        team.setProvider("openai");
        team.setKeyId("key-team");
        team.setTeamId("team-1");
        props.getKeyService().getReverseLookupMocks().add(team);

        assertThatThrownBy(() -> new ApiKeyClient(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate raw key");
    }

    @Test
    void selectorPathStaysInternalEvenWhenRawApiKeyExists() {
        ProxyProperties props = baseProperties();
        ProxyProperties.ReverseLookupMock mock = new ProxyProperties.ReverseLookupMock();
        mock.setRawKey("sk-ext-raw");
        mock.setProvider("openai");
        mock.setKeyId("key-ext-1");
        mock.setStatus("ACTIVE");
        props.getKeyService().getReverseLookupMocks().add(mock);

        ApiKeyClient client = new ApiKeyClient(props);
        assertThatThrownBy(() -> client.resolveApiKey(null, null, AiProvider.OPENAI, null, "alias", "sk-ext-raw").block())
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private static ProxyProperties baseProperties() {
        ProxyProperties props = new ProxyProperties();
        props.getKeyService().setCacheTtl("PT1M");
        return props;
    }
}
