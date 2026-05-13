package com.eevee.billingservice.service;

import com.eevee.billingservice.config.BillingRabbitProperties;
import com.eevee.billingservice.domain.BillingProcessedEventEntity;
import com.eevee.billingservice.domain.ProviderModelPriceEntity;
import com.eevee.billingservice.integration.IdentityBudgetClient;
import com.eevee.billingservice.repository.BillingTeamApiKeyRepository;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
public class BillingRecordedService {

    private static final Logger log = LoggerFactory.getLogger(BillingRecordedService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Pattern OPENAI_DATED_SNAPSHOT_SUFFIX = Pattern.compile("^(?<base>.+)-\\d{4}-\\d{2}-\\d{2}$");
    /** Cap distinct (provider, model) keys that emit a one-time WARN; further misses are DEBUG-only. */
    private static final int MISSING_PRICE_WARN_KEY_CAP = 256;

    private final ConcurrentHashMap<String, Boolean> missingPriceWarnOnceKeys = new ConcurrentHashMap<>();

    private final BillingProcessedEventRepository processedEventRepository;
    private final ProviderModelPriceRepository priceRepository;
    private final BillingAggregationJdbc aggregationJdbc;
    private final TeamApiKeyAggregationJdbc teamApiKeyAggregationJdbc;
    private final UsageCostFinalizedEventPublisher costFinalizedPublisher;
    private final BudgetThresholdEventPublisher budgetThresholdPublisher;
    private final BillingProcessedEventLifecycle processedEventLifecycle;
    private final BillingRabbitProperties rabbitProperties;
    private final IdentityBudgetClient identityBudgetClient;
    private final BillingTeamApiKeyRepository teamApiKeyRepository;
    private final TeamApiKeyBudgetThresholdEventPublisher teamApiKeyBudgetThresholdEventPublisher;

    public BillingRecordedService(
            BillingProcessedEventRepository processedEventRepository,
            ProviderModelPriceRepository priceRepository,
            BillingAggregationJdbc aggregationJdbc,
            TeamApiKeyAggregationJdbc teamApiKeyAggregationJdbc,
            UsageCostFinalizedEventPublisher costFinalizedPublisher,
            BudgetThresholdEventPublisher budgetThresholdPublisher,
            BillingProcessedEventLifecycle processedEventLifecycle,
            BillingRabbitProperties rabbitProperties,
            IdentityBudgetClient identityBudgetClient,
            BillingTeamApiKeyRepository teamApiKeyRepository,
            TeamApiKeyBudgetThresholdEventPublisher teamApiKeyBudgetThresholdEventPublisher
    ) {
        this.processedEventRepository = processedEventRepository;
        this.priceRepository = priceRepository;
        this.aggregationJdbc = aggregationJdbc;
        this.teamApiKeyAggregationJdbc = teamApiKeyAggregationJdbc;
        this.costFinalizedPublisher = costFinalizedPublisher;
        this.budgetThresholdPublisher = budgetThresholdPublisher;
        this.processedEventLifecycle = processedEventLifecycle;
        this.rabbitProperties = rabbitProperties;
        this.identityBudgetClient = identityBudgetClient;
        this.teamApiKeyRepository = teamApiKeyRepository;
        this.teamApiKeyBudgetThresholdEventPublisher = teamApiKeyBudgetThresholdEventPublisher;
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
        boolean teamSourced = "team".equalsIgnoreCase(event.apiKeySource());

        // Team-key spend is tracked only in team_* aggregate tables so personal dashboards and Identity
        // per-key monthly budgets are not inflated by team usage.
        if (!teamSourced) {
            var monthlyTotalBefore = aggregationJdbc.sumDailyCostUsdForKstCalendarMonthAndProvider(
                    bc.monthStart(), bc.userId(), bc.apiKeyId(), bc.provider());
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

            var monthlyTotalAfter = aggregationJdbc.sumDailyCostUsdForKstCalendarMonthAndProvider(
                    bc.monthStart(), bc.userId(), bc.apiKeyId(), bc.provider());
            var keyBudget = identityBudgetClient
                    .fetchMonthlyBudgetKeyRow(bc.userId(), bc.provider(), bc.apiKeyId())
                    .orElse(null);
            var monthlyBudgetUsd = keyBudget != null ? keyBudget.monthlyBudgetUsd() : null;
            var apiKeyAlias = keyBudget != null ? keyBudget.alias() : null;

            boolean keyBudgetPositive = monthlyBudgetUsd != null && monthlyBudgetUsd.compareTo(BigDecimal.ZERO) > 0;
            if (rabbitProperties.getBudgetOut().isEnabled() && keyBudgetPositive) {
                scheduleAfterCommit(() -> budgetThresholdPublisher.publishIfCrossed(
                        bc.userId(),
                        bc.teamId(),
                        bc.apiKeyId(),
                        apiKeyAlias,
                        bc.monthStart(),
                        monthlyTotalBefore == null ? BigDecimal.ZERO : monthlyTotalBefore,
                        monthlyTotalAfter == null ? BigDecimal.ZERO : monthlyTotalAfter,
                        monthlyBudgetUsd
                ));
            }
        }

        processedEventRepository.save(new BillingProcessedEventEntity(event.eventId(), processedAt, applicable));

        if (costOutEnabled) {
            scheduleAfterCommit(() -> publishCostAndMark(event, bc.model(), bc.cost()));
        }

        // Team API key spend aggregation + team budget threshold events ("registered team keys only").
        maybeProcessTeamKeyBudgetThreshold(event, bc);
    }

    private void maybeProcessTeamKeyBudgetThreshold(UsageRecordedEvent event, BillableComputation bc) {
        if (!"team".equalsIgnoreCase(event.apiKeySource())) {
            return;
        }
        String teamIdRaw = bc.teamId();
        String teamApiKeyIdRaw = event.teamApiKeyId();
        if (teamIdRaw == null || teamIdRaw.isBlank() || teamApiKeyIdRaw == null || teamApiKeyIdRaw.isBlank()) {
            return;
        }
        long teamId;
        long teamApiKeyId;
        try {
            teamId = Long.parseLong(teamIdRaw.trim());
            teamApiKeyId = Long.parseLong(teamApiKeyIdRaw.trim());
        } catch (NumberFormatException ignored) {
            return;
        }

        BigDecimal keyMonthlyBefore = teamApiKeyAggregationJdbc.sumMonthlyCostUsdForTeamApiKey(bc.monthStart(), teamApiKeyId);

        teamApiKeyAggregationJdbc.upsertDaily(bc.aggDate(), teamApiKeyId, bc.cost());
        teamApiKeyAggregationJdbc.upsertMonthly(bc.monthStart(), teamApiKeyId, bc.cost());

        BigDecimal keyMonthlyAfter = teamApiKeyAggregationJdbc.sumMonthlyCostUsdForTeamApiKey(bc.monthStart(), teamApiKeyId);

        var keyRow = teamApiKeyRepository.findById(teamApiKeyId).orElse(null);
        BigDecimal keyMonthlyBudgetUsd = keyRow != null ? keyRow.getMonthlyBudgetUsd() : null;
        String provider = keyRow != null ? keyRow.getProvider() : null;
        String apiKeyAlias = keyRow != null ? keyRow.getAlias() : null;

        boolean keyBudgetPositive = keyMonthlyBudgetUsd != null && keyMonthlyBudgetUsd.compareTo(BigDecimal.ZERO) > 0;
        if (!rabbitProperties.getTeamBudgetOut().isEnabled()) {
            return;
        }
        if (!keyBudgetPositive) {
            return;
        }
        scheduleAfterCommit(() -> teamApiKeyBudgetThresholdEventPublisher.publishIfCrossed(
                bc.userId(),
                teamId,
                teamApiKeyId,
                provider,
                apiKeyAlias,
                bc.monthStart(),
                keyMonthlyBefore,
                keyMonthlyAfter,
                keyMonthlyBudgetUsd
        ));
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
        if (price == null) {
            warnMissingModelPriceOnce(event.provider(), model, event.eventId());
        }

        BigDecimal cost = ExpenditureCostCalculator.compute(event.tokenUsage(), price);
        ExpenditureCostCalculator.NormalizedTokens tokens = ExpenditureCostCalculator.normalizeTokens(event.tokenUsage());

        return Optional.of(new BillableComputation(
                userId,
                teamId,
                apiKeyId,
                event.provider(),
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

    private void warnMissingModelPriceOnce(AiProvider provider, String model, UUID eventId) {
        String key = provider.name() + "\0" + model;
        if (missingPriceWarnOnceKeys.size() < MISSING_PRICE_WARN_KEY_CAP
                && missingPriceWarnOnceKeys.putIfAbsent(key, Boolean.TRUE) == null) {
            log.warn(
                    "Missing provider_model_price; cost counted as zero until catalog/seed covers it. provider={} model={} exampleEventId={}",
                    provider,
                    model,
                    eventId);
        } else {
            log.debug("Missing provider_model_price; cost zero. provider={} model={} eventId={}", provider, model, eventId);
        }
    }

    /**
     * Resolves catalog prices when usage records a variant string (dated snapshots, API-specific suffixes).
     * Seeds an alias {@link ProviderModelPriceEntity} row so later exact lookups succeed.
     */
    private ProviderModelPriceEntity resolveAliasedPrice(AiProvider provider, String model, Instant at) {
        if (model == null || model.isBlank()) {
            return null;
        }
        if (provider == AiProvider.OPENAI) {
            return resolveOpenAiDatedSnapshotAlias(model, at);
        }
        if (provider == AiProvider.GOOGLE) {
            return resolveCatalogAliasFromCandidates(provider, model, googlePricingAliasCandidates(model), at);
        }
        if (provider == AiProvider.ANTHROPIC) {
            return resolveCatalogAliasFromCandidates(provider, model, anthropicPricingAliasCandidates(model), at);
        }
        return null;
    }

    /**
     * OpenAI models may include dated snapshot suffixes (ex: {@code gpt-5.4-mini-2026-03-17}).
     */
    private ProviderModelPriceEntity resolveOpenAiDatedSnapshotAlias(String model, Instant at) {
        var m = OPENAI_DATED_SNAPSHOT_SUFFIX.matcher(model);
        if (!m.matches()) {
            return null;
        }
        String base = m.group("base");
        if (base == null || base.isBlank() || base.equals(model)) {
            return null;
        }

        ProviderModelPriceEntity basePrice = priceRepository
                .findActivePrices(AiProvider.OPENAI, base, at, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElse(null);
        if (basePrice == null) {
            return null;
        }

        seedProviderModelAliasIfMissing(AiProvider.OPENAI, model, base, basePrice);
        return basePrice;
    }

    /**
     * Gemini usage often reports {@code models/...} paths or {@code -001} style API versions while the seed
     * catalog keys the undecorated id (see proxy / usage samples in this repo).
     */
    private static List<String> googlePricingAliasCandidates(String model) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        String t = model.trim();
        if (t.startsWith("models/")) {
            out.add(t.substring("models/".length()).trim());
        }
        out.add(t.replaceFirst("-\\d{3}$", ""));
        if (t.startsWith("models/")) {
            String inner = t.substring("models/".length()).trim();
            out.add(inner.replaceFirst("-\\d{3}$", ""));
            var innerDated = OPENAI_DATED_SNAPSHOT_SUFFIX.matcher(inner);
            if (innerDated.matches()) {
                out.add(innerDated.group("base"));
            }
        }
        var dated = OPENAI_DATED_SNAPSHOT_SUFFIX.matcher(t);
        if (dated.matches()) {
            out.add(dated.group("base"));
        }
        return new ArrayList<>(out);
    }

    /** Anthropic dated releases use an {@code -YYYYMMDD} suffix on the public model id. */
    private static List<String> anthropicPricingAliasCandidates(String model) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        String t = model.trim();
        out.add(t.replaceFirst("-\\d{8}$", ""));
        return new ArrayList<>(out);
    }

    private ProviderModelPriceEntity resolveCatalogAliasFromCandidates(
            AiProvider provider, String requestedModel, List<String> candidates, Instant at) {
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank() || candidate.equals(requestedModel)) {
                continue;
            }
            ProviderModelPriceEntity basePrice = priceRepository
                    .findActivePrices(provider, candidate, at, PageRequest.of(0, 1))
                    .stream()
                    .findFirst()
                    .orElse(null);
            if (basePrice != null) {
                seedProviderModelAliasIfMissing(provider, requestedModel, candidate, basePrice);
                return basePrice;
            }
        }
        return null;
    }

    private void seedProviderModelAliasIfMissing(
            AiProvider provider, String aliasModel, String catalogModel, ProviderModelPriceEntity basePrice) {
        Instant validFrom = basePrice.getValidFrom();
        Instant validTo = basePrice.getValidTo();
        boolean exists = priceRepository.existsByProviderAndModelAndValidFromAndValidTo(provider, aliasModel, validFrom, validTo);
        if (!exists) {
            priceRepository.save(new ProviderModelPriceEntity(
                    provider,
                    aliasModel,
                    validFrom,
                    validTo,
                    basePrice.getInputUsdPerMillionTokens(),
                    basePrice.getOutputUsdPerMillionTokens()
            ));
            log.info("Seeded model price alias provider={} catalogModel={} aliasModel={}", provider, catalogModel, aliasModel);
        }
    }

    private record BillableComputation(
            String userId,
            String teamId,
            String apiKeyId,
            AiProvider provider,
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
