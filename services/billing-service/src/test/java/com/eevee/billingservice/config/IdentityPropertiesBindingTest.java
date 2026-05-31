package com.eevee.billingservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @EnableConfigurationProperties(IdentityProperties.class)
    static class TestConfiguration {
    }

    @Test
    void bindsBillingIdentityPrefixFromProperties() {
        contextRunner
                .withPropertyValues(
                        "billing.identity.enabled=true",
                        "billing.identity.base-url=http://localhost:8090",
                        "billing.identity.budget-path-template=/api/identity/v1/users/budget?email={userId}")
                .run(context -> {
                    IdentityProperties props = context.getBean(IdentityProperties.class);
                    assertThat(props.isEnabled()).isTrue();
                    assertThat(props.getBaseUrl()).isEqualTo("http://localhost:8090");
                    assertThat(props.getBudgetPathTemplate())
                            .isEqualTo("/api/identity/v1/users/budget?email={userId}");
                });
    }

    @Test
    void topLevelIdentityPrefixDoesNotBindToBillingIdentityProperties() {
        contextRunner
                .withPropertyValues(
                        "identity.identity.enabled=true",
                        "identity.identity.base-url=http://wrong:8080")
                .run(context -> {
                    IdentityProperties props = context.getBean(IdentityProperties.class);
                    assertThat(props.isEnabled()).isFalse();
                    assertThat(props.getBaseUrl()).isEmpty();
                });
    }
}
