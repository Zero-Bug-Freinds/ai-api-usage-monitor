package com.eevee.usageservice.api.dto;

/**
 * Time-bucket metrics for latency and success/error rates from {@code usage_recorded_log.latency_ms}.
 */
public record LatencyStabilityPoint(
        String bucketLabel,
        long requestCount,
        double successRate,
        double errorRate,
        long totalTokens,
        Double avgLatencyMs,
        Long minLatencyMs,
        Long maxLatencyMs,
        Double p95LatencyMs,
        Double p99LatencyMs,
        Double latencyPerTokenMs,
        String topModel,
        String topModelProvider
) {
}
