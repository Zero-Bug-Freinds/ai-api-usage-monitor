package com.eevee.proxygateway;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(ProxyGatewayApplicationTests.RabbitTestConfig.class)
class ProxyGatewayApplicationTests {

    @TestConfiguration
    static class RabbitTestConfig {

        @Bean
        @Primary
        RabbitTemplate rabbitTemplate() {
            return Mockito.mock(RabbitTemplate.class);
        }
    }

    @Test
    void contextLoads() {
    }
}
