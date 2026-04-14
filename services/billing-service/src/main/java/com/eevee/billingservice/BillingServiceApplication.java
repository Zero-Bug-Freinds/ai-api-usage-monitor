package com.eevee.billingservice;

import com.eevee.billingservice.config.BillingProperties;
import com.eevee.billingservice.config.BillingRabbitProperties;
import com.eevee.billingservice.config.IdentityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        BillingRabbitProperties.class,
        BillingProperties.class,
        IdentityProperties.class
})
public class BillingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BillingServiceApplication.class, args);
    }
}
