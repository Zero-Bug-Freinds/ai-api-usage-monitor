package com.eevee.proxyservice.relay;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyRelayServiceResponseHeadersTest {

    @Test
    void stripsContentEncodingAndLengthWhenRebuffering() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_ENCODING, "gzip");
        headers.add(HttpHeaders.CONTENT_LENGTH, "999");
        byte[] bytes = "{\"ok\":true}".getBytes();

        headers.remove(HttpHeaders.CONTENT_ENCODING);
        headers.remove(HttpHeaders.CONTENT_LENGTH);
        headers.setContentLength(bytes.length);

        assertThat(headers.getFirst(HttpHeaders.CONTENT_ENCODING)).isNull();
        assertThat(headers.getContentLength()).isEqualTo(bytes.length);
    }
}
