package com.eevee.usageservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class UsageTimeConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
