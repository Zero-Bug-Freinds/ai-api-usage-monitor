package com.eevee.apigateway.filter;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory nonce replay guard for ext AI ingress.
 * A Redis-backed implementation can replace this later without changing filter contract.
 */
public class ExtAiNonceReplayGuard {

    private final ConcurrentHashMap<String, Long> seen = new ConcurrentHashMap<>();
    private final long ttlSeconds;

    public ExtAiNonceReplayGuard(long ttlSeconds) {
        this.ttlSeconds = Math.max(60, ttlSeconds);
    }

    public boolean tryAcquire(String key, Instant now) {
        long nowEpochSeconds = now.getEpochSecond();
        evictExpired(nowEpochSeconds);
        Long previous = seen.putIfAbsent(key, nowEpochSeconds + ttlSeconds);
        return previous == null;
    }

    private void evictExpired(long nowEpochSeconds) {
        Iterator<Map.Entry<String, Long>> it = seen.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> entry = it.next();
            if (entry.getValue() <= nowEpochSeconds) {
                it.remove();
            }
        }
    }
}
