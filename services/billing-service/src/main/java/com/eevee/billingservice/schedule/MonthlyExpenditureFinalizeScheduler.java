package com.eevee.billingservice.schedule;

import com.eevee.billingservice.repository.MonthlyExpenditureAggRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * Marks the previous calendar month (Asia/Seoul) as finalized for monthly aggregates.
 */
@Component
public class MonthlyExpenditureFinalizeScheduler {

    private static final Logger log = LoggerFactory.getLogger(MonthlyExpenditureFinalizeScheduler.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MonthlyExpenditureAggRepository monthlyRepository;

    public MonthlyExpenditureFinalizeScheduler(MonthlyExpenditureAggRepository monthlyRepository) {
        this.monthlyRepository = monthlyRepository;
    }

    @Scheduled(cron = "0 0 0 1 * ?", zone = "Asia/Seoul")
    @Transactional
    public void finalizePreviousMonth() {
        YearMonth previous = YearMonth.from(LocalDate.now(KST)).minusMonths(1);
        LocalDate monthStart = previous.atDay(1);
        Instant now = Instant.now();
        int updated = monthlyRepository.finalizeMonth(monthStart, now);
        log.info("Monthly expenditure finalize month={} rowsUpdated={}", monthStart, updated);
    }
}
