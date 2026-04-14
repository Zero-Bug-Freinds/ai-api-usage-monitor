package com.eevee.billingservice.config;

import com.eevee.billingservice.domain.ProviderModelPriceEntity;
import com.eevee.billingservice.pricing.OfficialProviderModelPriceCatalog;
import com.eevee.billingservice.repository.ProviderModelPriceRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds per-million token prices from {@link OfficialProviderModelPriceCatalog} when the table is
 * empty (local/dev). Reference URLs and as-of date live on the catalog, not in the DB row shape.
 */
@Component
public class ProviderModelPriceSeed implements ApplicationRunner {

    private final ProviderModelPriceRepository priceRepository;
    private final boolean seedMissingEnabled;

    public ProviderModelPriceSeed(
            ProviderModelPriceRepository priceRepository,
            @Value("${billing.pricing.seed-missing:false}") boolean seedMissingEnabled
    ) {
        this.priceRepository = priceRepository;
        this.seedMissingEnabled = seedMissingEnabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedMissingEnabled && priceRepository.count() > 0) {
            return;
        }
        for (OfficialProviderModelPriceCatalog.CatalogRow row : OfficialProviderModelPriceCatalog.seedRows()) {
            if (seedMissingEnabled && priceRepository.existsByProviderAndModelAndValidFromAndValidTo(
                    row.provider(),
                    row.modelId(),
                    row.validFrom(),
                    null
            )) {
                continue;
            }
            priceRepository.save(new ProviderModelPriceEntity(
                    row.provider(),
                    row.modelId(),
                    row.validFrom(),
                    null,
                    row.inputUsdPerMillionTokens(),
                    row.outputUsdPerMillionTokens()
            ));
        }
    }
}
