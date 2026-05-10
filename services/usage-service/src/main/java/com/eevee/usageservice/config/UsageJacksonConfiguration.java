package com.eevee.usageservice.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Aligns JSON handling with {@code proxy-service} ({@code JacksonConfiguration}: ObjectMapper + JSR-310 modules).
 * Spring Boot 4 does not register a {@link ObjectMapper} bean by default for this stack; the listener requires one.
 * <p>
 * Unknown JSON properties are ignored so upstream event payloads (e.g. identity-service adding {@code keyHash})
 * do not fail deserialization. Matches {@code spring.jackson.deserialization.fail-on-unknown-properties: false}.
 */
@Configuration
public class UsageJacksonConfiguration {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }
}
