package com.eevee.billingservice.service;

import com.eevee.billingservice.config.BillingRabbitProperties;
import com.eevee.billingservice.domain.BillingProcessedEventEntity;
import com.eevee.billingservice.domain.ProviderModelPriceEntity;
import com.eevee.billingservice.integration.IdentityBudgetClient;
import com.eevee.billingservice.repository.BillingProcessedEventRepository;
import com.eevee.billingservice.repository.ProviderModelPriceRepository;
import com.eevee.usage.events.TokenUsage;
import com.eevee.usage.events.UsageRecordedEvent;
import com.eevee.usage.events.AiProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class BillingRecordedService {

    private static final Logger log = LoggerFactory.getLogger(BillingRecordedService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Pattern OPENAI_DATED_SNAPSHOT_SUFFIX = Pattern.compile("^(?<base>.+)-\\d{4}-\\d{2}-\\d{2}$");

    private final BillingProcessedEventRepository processedEventRepository;
    private final ProviderModelPriceRepository priceRepository;
    private final BillingAggregationJdbc aggregationJdbc;
    private final UsageCostFinalizedEventPublisher costFinalizedPublisher;
    private final BudgetThresholdEventPublisher budgetThresholdPublisher;
    private final BillingProcessedEventLifecycle processedEventLifecycle;
    private final BillingRabbitProperties rabbitProperties;
    private final IdentityBudgetClient identityBudgetClient;

    public BillingRecordedService(
            BillingProcessedEventRepository processedEventRepository,
            ProviderModelPriceRepository priceRepository,
            BillingAggregationJdbc aggregationJdbc,
            UsageCostFinalizedEventPublisher costFinalizedPublisher,
            BudgetThresholdEventPublisher budgetThresholdPublisher,
            BillingProcessedEventLifecycle processedEventLifecycle,
            BillingRabbitProperties rabbitProperties,
            IdentityBudgetClient identityBudgetClient
    ) {
        this.processedEventRepository = processedEventRepository;
        this.priceRepository = priceRepository;
        this.aggregationJdbc = aggregationJdbc;
        this.costFinalizedPublisher = costFinalizedPublisher;
        this.budgetThresholdPublisher = budgetThresholdPublisher;
        this.processedEventLifecycle = processedEventLifecycle;
        this.rabbitProperties = rabbitProperties;
        this.identityBudgetClient = identityBudgetClient;
    }

    @Transactional
    public void process(UsageRecordedEvent event) {
        Optional<BillingProcessedEventEntity> existing = processedEventRepository.findById(event.eventId());
        if (existing.isPresent()) {
            handleAlreadyProcessed(event, existing.get());
            return;
        }

        Instant processedAt = Instant.now();
        Optional<BillableComputation> billable = resolveBillable(event);
        if (billable.isEmpty()) {
            processedEventRepository.save(new BillingProcessedEventEntity(event.eventId(), processedAt, false));
            return;
        }

        BillableComputation bc = billable.get();
        boolean costOutEnabled = rabbitProperties.getCostOut().isEnabled();
        boolean applicable = costOutEnabled;

        var monthlyTotalBefore = aggregationJdbc.findMonthlyTotalUsd(bc.monthStart(), bc.userId(), bc.apiKeyId());
        aggregationJdbc.upsertDaily(
                bc.aggDate(),
                bc.userId(),
                bc.apiKeyId(),
                event.provider(),
                bc.model(),
                bc.cost(),
                bc.promptTokens(),
                bc.completionTokens());
        aggregationJdbc.upsertMonthly(bc.monthStart(), bc.userId(), bc.apiKeyId(), bc.cost());
        aggregationJdbc.upsertSeen(bc.userId(), bc.apiKeyId(), event.provider(), bc.occurredAt());

        var monthlyTotalAfter = aggregationJdbc.findMonthlyTotalUsd(bc.monthStart(), bc.userId(), bc.apiKeyId());
        var monthlyBudgetUsd = identityBudgetClient.fetchMonthlyBudgetUsd(bc.userId()).orElse(null);

        processedEventRepository.save(new BillingProcessedEventEntity(event.eventId(), processedAt, applicable));

        if (costOutEnabled) {
            scheduleAfterCommit(() -> publishCostAndMark(event, bc.model(), bc.cost()));
        }

        if (rabbitProperties.getBudgetOut().isEnabled() && monthlyBudgetUsd != null) {
            scheduleAfterCommit(() -> budgetThresholdPublisher.publishIfCrossed(
                    bc.userId(),
                    bc.teamId(),
                    bc.apiKeyId(),
                    bc.monthStart(),
                    monthlyTotalBefore == null ? BigDecimal.ZERO : monthlyTotalBefore,
                    monthlyTotalAfter == null ? BigDecimal.ZERO : monthlyTotalAfter,
                    monthlyBudgetUsd
            ));
        }
    }

    private void handleAlreadyProcessed(UsageRecordedEvent event, BillingProcessedEventEntity row) {
        if (!row.isCostEventApplicable()) {
            return;
        }
        if (row.getCostEventPublishedAt() != null) {
            return;
        }
        if (!rabbitProperties.getCostOut().isEnabled()) {
            return;
        }
        Optional<BillableComputation> billable = resolveBillable(event);
        if (billable.isEmpty()) {
            log.warn(
                    "Cost event still pending but event no longer resolves as billable; skipping republish eventId={}",
                    event.eventId());
            return;
        }
        BillableComputation bc = billable.get();
        scheduleAfterCommit(() -> publishCostAndMark(event, bc.model(), bc.cost()));
    }

    private void publishCostAndMark(UsageRecordedEvent event, String model, BigDecimal costUsd) {
        costFinalizedPublisher.publish(event, model, costUsd);
        processedEventLifecycle.markCostEventPublished(event.eventId(), Instant.now());
    }

    private void scheduleAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Expected an active Spring transaction for billing event handling");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    /**
     * Billable path: successful request with user, key, provider, model, and KST-derived aggregation keys.
     */
    private Optional<BillableComputation> resolveBillable(UsageRecordedEvent event) {
        if (!Boolean.TRUE.equals(event.requestSuccessful())) {
            return Optional.empty();
        }
        String userId = event.userId();
        String teamId = event.teamId();
        String apiKeyId = event.apiKeyId();
        if (userId == null || userId.isBlank() || apiKeyId == null || apiKeyId.isBlank()) {
            log.debug("Skipping expenditure aggregation: missing userId or apiKeyId eventId={}", event.eventId());
            return Optional.empty();
        }
        if (event.provider() == null) {
            return Optional.empty();
        }

        String model = resolveModel(event);
        if (model == null || model.isBlank()) {
            return Optional.empty();
        }

        Instant occurredAt = event.occurredAt();
        LocalDate aggDate = occurredAt.atZone(KST).toLocalDate();
        LocalDate monthStart = aggDate.withDayOfMonth(1);

        ProviderModelPriceEntity price = priceRepository
                .findActivePrices(event.provider(), model, occurredAt, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElse(null);
        if (price == null) {
            price = resolveAliasedPrice(event.provider(), model, occurredAt);
        }

        BigDecimal cost = ExpenditureCostCalculator.compute(event.tokenUsage(), price);
        ExpenditureCostCalculator.NormalizedTokens tokens = ExpenditureCostCalculator.normalizeTokens(event.tokenUsage());

        return Optional.of(new BillableComputation(
                userId,
                teamId,
                apiKeyId,
                aggDate,
                monthStart,
                model,
                cost,
                tokens.promptTokens(),
                tokens.completionTokens(),
                occurredAt));
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

    /**
     * OpenAI models may include dated snapshot suffixes (ex: {@code gpt-5.4-mini-2026-03-17}).
     * Billing pricing is keyed by exact (provider, model), so we resolve against the base model
     * and persist a compatible alias price row for future events.
     */
    private ProviderModelPriceEntity resolveAliasedPrice(AiProvider provider, String model, Instant at) {
        if (provider != AiProvider.OPENAI || model == null || model.isBlank()) {
            return null;
        }
        var m = OPENAI_DATED_SNAPSHOT_SUFFIX.matcher(model);
        if (!m.matches()) {
            return null;
        }
        String base = m.group("base");
        if (base == null || base.isBlank() || base.equals(model)) {
            return null;
        }

        ProviderModelPriceEntity basePrice = priceRepository
                .findActivePrices(provider, base, at, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElse(null);
        if (basePrice == null) {
            return null;
        }

        Instant validFrom = basePrice.getValidFrom();
        Instant validTo = basePrice.getValidTo();
        boolean exists = priceRepository.existsByProviderAndModelAndValidFromAndValidTo(provider, model, validFrom, validTo);
        if (!exists) {
            priceRepository.save(new ProviderModelPriceEntity(
                    provider,
                    model,
                    validFrom,
                    validTo,
                    basePrice.getInputUsdPerMillionTokens(),
                    basePrice.getOutputUsdPerMillionTokens()
            ));
            log.info("Seeded OpenAI snapshot model price alias baseModel={} snapshotModel={}", base, model);
        }
        return basePrice;
    }

    private record BillableComputation(
            String userId,
            String teamId,
            String apiKeyId,
            LocalDate aggDate,
            LocalDate monthStart,
            String model,
            BigDecimal cost,
            long promptTokens,
            long completionTokens,
            Instant occurredAt
    ) {
    }
}
