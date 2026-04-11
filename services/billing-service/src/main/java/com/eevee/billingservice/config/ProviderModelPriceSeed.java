package com.eevee.billingservice.config;

import com.eevee.billingservice.domain.ProviderModelPriceEntity;
import com.eevee.billingservice.repository.ProviderModelPriceRepository;
import com.eevee.usage.events.AiProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Seeds representative per-million token prices when the table is empty (local/dev).
 */
@Component
public class ProviderModelPriceSeed implements ApplicationRunner {

    private final ProviderModelPriceRepository priceRepository;

    public ProviderModelPriceSeed(ProviderModelPriceRepository priceRepository) {
        this.priceRepository = priceRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (priceRepository.count() > 0) {
            return;
        }
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        priceRepository.save(new ProviderModelPriceEntity(
                AiProvider.OPENAI,
                "gpt-4o-mini",
                start,
                null,
                new BigDecimal("0.15"),
                new BigDecimal("0.60")
        ));
        priceRepository.save(new ProviderModelPriceEntity(
                AiProvider.ANTHROPIC,
                "claude-3-5-sonnet-20241022",
                start,
                null,
                new BigDecimal("3.00"),
                new BigDecimal("15.00")
        ));
        priceRepository.save(new ProviderModelPriceEntity(
                AiProvider.GOOGLE,
                "gemini-1.5-flash",
                start,
                null,
                new BigDecimal("0.075"),
                new BigDecimal("0.30")
        ));
    }
}
