package com.eevee.billingservice.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Inserts idempotency rows with PostgreSQL {@code ON CONFLICT DO NOTHING} semantics.
 */
@Repository
public class BillingCostCorrectionClaimJdbc {

    private final JdbcTemplate jdbcTemplate;

    public BillingCostCorrectionClaimJdbc(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * @return {@code true} if this invocation inserted a new idempotency row (first time seeing the id).
     */
    public boolean tryClaim(UUID correctionEventId, Instant processedAt) {
        int rows = jdbcTemplate.update(
                """
                        INSERT INTO billing_cost_correction_processed (correction_event_id, processed_at)
                        VALUES (?, ?)
                        ON CONFLICT (correction_event_id) DO NOTHING
                        """,
                correctionEventId,
                Timestamp.from(processedAt)
        );
        return rows == 1;
    }
}
