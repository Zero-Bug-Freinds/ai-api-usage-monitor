package com.eevee.usageservice;

import com.eevee.usageservice.config.UsageRabbitProperties;
import com.eevee.usageservice.config.UsageServiceProperties;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableRabbit
@EnableScheduling
@EnableConfigurationProperties({UsageRabbitProperties.class, UsageServiceProperties.class})
public class UsageServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UsageServiceApplication.class, args);
    }
}