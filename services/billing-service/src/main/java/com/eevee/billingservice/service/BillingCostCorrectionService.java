package com.eevee.billingservice.service;

import com.eevee.billingservice.config.BillingRabbitProperties;
import com.eevee.billingservice.events.BillingCostCorrectionAmqp;
import com.eevee.billingservice.repository.BillingCostCorrectionClaimJdbc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Applies cost corrections with DB idempotency and optional outbound {@link com.eevee.billingservice.events.BillingCostCorrectedEvent}.
 * <p>
 * <strong>Finalized month policy:</strong> if {@code monthly_expenditure_agg.is_finalized} is true for the correction's
 * natural month key, the correction is acknowledged idempotently but <em>does not</em> change aggregates and does not emit
 * {@code BillingCostCorrectedEvent}. Extending corrections into finalized months requires an explicit product decision
 * (unfinalize, separate adjustment ledger, etc.).
 */
@Service
public class BillingCostCorrectionService {

    private static final Logger log = LoggerFactory.getLogger(BillingCostCorrectionService.class);

    private final BillingCostCorrectionClaimJdbc claimJdbc;
    private final BillingAggregationJdbc aggregationJdbc;
    private final BillingCostCorrectedEventPublisher correctedEventPublisher;
    private final BillingRabbitProperties rabbitProperties;

    public BillingCostCorrectionService(
            BillingCostCorrectionClaimJdbc claimJdbc,
            BillingAggregationJdbc aggregationJdbc,
            BillingCostCorrectedEventPublisher correctedEventPublisher,
            BillingRabbitProperties rabbitProperties
    ) {
        this.claimJdbc = claimJdbc;
        this.aggregationJdbc = aggregationJdbc;
        this.correctedEventPublisher = correctedEventPublisher;
        this.rabbitProperties = rabbitProperties;
    }

    @Transactional
    public void process(BillingCostCorrectionAmqp cmd) {
        if (cmd.schemaVersion() != BillingCostCorrectionAmqp.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported schemaVersion=" + cmd.schemaVersion());
        }
        validateMonthKey(cmd.monthStartDate());
        validateMonthMatchesAgg(cmd);

        Instant processedAt = Instant.now();
        if (!claimJdbc.tryClaim(cmd.correctionEventId(), processedAt)) {
            return;
        }

        if (aggregationJdbc.isMonthlyFinalized(cmd.monthStartDate(), cmd.userId(), cmd.apiKeyId())) {
            log.info(
                    "Skipping cost correction for finalized month monthStart={} userId={} apiKeyId={} correctionEventId={}",
                    cmd.monthStartDate(),
                    cmd.userId(),
                    cmd.apiKeyId(),
                    cmd.correctionEventId());
            return;
        }

        if (!wouldRemainNonNegative(cmd)) {
            log.warn(
                    "Skipping cost correction that would drive totals negative correctionEventId={} userId={} apiKeyId={}",
                    cmd.correctionEventId(),
                    cmd.userId(),
                    cmd.apiKeyId());
            return;
        }

        boolean hasDaily = cmd.aggDate() != null && cmd.provider() != null && cmd.model() != null && !cmd.model().isBlank();
        long promptDelta = cmd.promptTokenDelta() != null ? cmd.promptTokenDelta() : 0L;
        long completionDelta = cmd.completionTokenDelta() != null ? cmd.completionTokenDelta() : 0L;
        if (hasDaily) {
            aggregationJdbc.upsertDaily(
                    cmd.aggDate(),
                    cmd.userId(),
                    cmd.apiKeyId(),
                    cmd.provider(),
                    cmd.model(),
                    cmd.deltaCostUsd(),
                    promptDelta,
                    completionDelta
            );
        }
        aggregationJdbc.upsertMonthly(cmd.monthStartDate(), cmd.userId(), cmd.apiKeyId(), cmd.deltaCostUsd());

        if (rabbitProperties.getCorrectionOut().isEnabled()) {
            Instant occurredAt = Instant.now();
            scheduleAfterCommit(() -> correctedEventPublisher.publish(cmd, occurredAt));
        }
    }

    private static void validateMonthKey(LocalDate monthStartDate) {
        if (monthStartDate.getDayOfMonth() != 1) {
            throw new IllegalArgumentException("monthStartDate must be the first day of a calendar month");
        }
    }

    private static void validateMonthMatchesAgg(BillingCostCorrectionAmqp cmd) {
        if (cmd.aggDate() == null) {
            return;
        }
        LocalDate aggMonthStart = cmd.aggDate().withDayOfMonth(1);
        if (!aggMonthStart.equals(cmd.monthStartDate())) {
            throw new IllegalArgumentException("aggDate month must match monthStartDate");
        }
    }

    private boolean wouldRemainNonNegative(BillingCostCorrectionAmqp cmd) {
        BigDecimal monthlyBefore = aggregationJdbc.findMonthlyTotalUsd(cmd.monthStartDate(), cmd.userId(), cmd.apiKeyId());
        BigDecimal monthlyBase = monthlyBefore == null ? BigDecimal.ZERO : monthlyBefore;
        if (monthlyBase.add(cmd.deltaCostUsd()).compareTo(BigDecimal.ZERO) < 0) {
            return false;
        }

        if (cmd.aggDate() != null && cmd.provider() != null && cmd.model() != null && !cmd.model().isBlank()) {
            BigDecimal dailyBefore = aggregationJdbc.findDailyTotalCostUsd(
                    cmd.aggDate(),
                    cmd.userId(),
                    cmd.apiKeyId(),
                    cmd.provider().name(),
                    cmd.model()
            );
            BigDecimal dailyBase = dailyBefore == null ? BigDecimal.ZERO : dailyBefore;
            if (dailyBase.add(cmd.deltaCostUsd()).compareTo(BigDecimal.ZERO) < 0) {
                return false;
            }
        }
        return true;
    }

    private void scheduleAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Expected an active Spring transaction for billing cost correction handling");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
