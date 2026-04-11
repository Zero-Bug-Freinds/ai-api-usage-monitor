package com.eevee.billingservice.service;

import com.eevee.billingservice.domain.BillingProcessedEventEntity;
import com.eevee.billingservice.domain.ProviderModelPriceEntity;
import com.eevee.billingservice.repository.BillingProcessedEventRepository;
import com.eevee.billingservice.repository.ProviderModelPriceRepository;
import com.eevee.usage.events.TokenUsage;
import com.eevee.usage.events.UsageRecordedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class BillingRecordedService {

    private static final Logger log = LoggerFactory.getLogger(BillingRecordedService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final BillingProcessedEventRepository processedEventRepository;
    private final ProviderModelPriceRepository priceRepository;
    private final BillingAggregationJdbc aggregationJdbc;

    public BillingRecordedService(
            BillingProcessedEventRepository processedEventRepository,
            ProviderModelPriceRepository priceRepository,
            BillingAggregationJdbc aggregationJdbc
    ) {
        this.processedEventRepository = processedEventRepository;
        this.priceRepository = priceRepository;
        this.aggregationJdbc = aggregationJdbc;
    }

    @Transactional
    public void process(UsageRecordedEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            return;
        }
        Instant processedAt = Instant.now();
        if (!Boolean.TRUE.equals(event.requestSuccessful())) {
            processedEventRepository.save(new BillingProcessedEventEntity(event.eventId(), processedAt));
            return;
        }
        String userId = event.userId();
        String apiKeyId = event.apiKeyId();
        if (userId == null || userId.isBlank() || apiKeyId == null || apiKeyId.isBlank()) {
            log.debug("Skipping expenditure aggregation: missing userId or apiKeyId eventId={}", event.eventId());
            processedEventRepository.save(new BillingProcessedEventEntity(event.eventId(), processedAt));
            return;
        }
        if (event.provider() == null) {
            processedEventRepository.save(new BillingProcessedEventEntity(event.eventId(), processedAt));
            return;
        }

        String model = resolveModel(event);
        if (model == null || model.isBlank()) {
            processedEventRepository.save(new BillingProcessedEventEntity(event.eventId(), processedAt));
            return;
        }

        Instant occurredAt = event.occurredAt();
        LocalDate aggDate = occurredAt.atZone(KST).toLocalDate();
        LocalDate monthStart = aggDate.withDayOfMonth(1);

        ProviderModelPriceEntity price = priceRepository
                .findActivePrices(event.provider(), model, occurredAt, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElse(null);

        BigDecimal cost = ExpenditureCostCalculator.compute(event.tokenUsage(), price);
        ExpenditureCostCalculator.NormalizedTokens tokens = ExpenditureCostCalculator.normalizeTokens(event.tokenUsage());

        aggregationJdbc.upsertDaily(aggDate, userId, apiKeyId, event.provider(), model, cost, tokens.promptTokens(), tokens.completionTokens());
        aggregationJdbc.upsertMonthly(monthStart, userId, apiKeyId, cost);
        aggregationJdbc.upsertSeen(userId, apiKeyId, event.provider(), occurredAt);

        processedEventRepository.save(new BillingProcessedEventEntity(event.eventId(), processedAt));
    }

    private static String resolveModel(UsageRecordedEvent event) {
        if (event.model() != null && !event.model().isBlank()) {
            return event.model().trim();
        }
        TokenUsage tu = event.tokenUsage();
        if (tu != null && tu.model() != null && !tu.model().isBlank()) {
            return tu.model().trim();
        }
        return null;
    }
}
