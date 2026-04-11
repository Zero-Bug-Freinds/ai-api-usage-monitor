package com.eevee.billingservice.domain;

import com.eevee.usage.events.AiProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "provider_model_price")
public class ProviderModelPriceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private AiProvider provider;

    @Column(name = "model", nullable = false, length = 512)
    private String model;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @Column(name = "input_usd_per_million_tokens", nullable = false, precision = 24, scale = 10)
    private BigDecimal inputUsdPerMillionTokens;

    @Column(name = "output_usd_per_million_tokens", nullable = false, precision = 24, scale = 10)
    private BigDecimal outputUsdPerMillionTokens;

    protected ProviderModelPriceEntity() {
    }

    public ProviderModelPriceEntity(
            AiProvider provider,
            String model,
            Instant validFrom,
            Instant validTo,
            BigDecimal inputUsdPerMillionTokens,
            BigDecimal outputUsdPerMillionTokens
    ) {
        this.provider = provider;
        this.model = model;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.inputUsdPerMillionTokens = inputUsdPerMillionTokens;
        this.outputUsdPerMillionTokens = outputUsdPerMillionTokens;
    }

    public Long getId() {
        return id;
    }

    public AiProvider getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidTo() {
        return validTo;
    }

    public BigDecimal getInputUsdPerMillionTokens() {
        return inputUsdPerMillionTokens;
    }

    public BigDecimal getOutputUsdPerMillionTokens() {
        return outputUsdPerMillionTokens;
    }
}
