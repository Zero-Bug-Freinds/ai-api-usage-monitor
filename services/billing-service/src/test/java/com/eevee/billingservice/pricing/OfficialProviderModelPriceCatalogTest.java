package com.eevee.billingservice.pricing;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class OfficialProviderModelPriceCatalogTest {

    @Test
    void seedRows_include20260530GapModels() {
        Set<String> modelIds = OfficialProviderModelPriceCatalog.seedRows().stream()
                .map(OfficialProviderModelPriceCatalog.CatalogRow::modelId)
                .collect(Collectors.toSet());

        assertThat(OfficialProviderModelPriceCatalog.DOCUMENTED_AS_OF).isEqualTo("2026-05-30");
        assertThat(OfficialProviderModelPriceCatalog.seedRows()).hasSize(70);
        assertThat(modelIds).contains(
                "gemini-3.5-flash",
                "gemini-3.1-flash-lite",
                "gemini-3-flash",
                "gemini-3-pro",
                "gemini-2.0-flash-lite",
                "claude-opus-4-8",
                "claude-opus-4-5",
                "claude-opus-4-20250514",
                "claude-opus-4-0",
                "claude-3-5-sonnet-20241022"
        );
    }
}
