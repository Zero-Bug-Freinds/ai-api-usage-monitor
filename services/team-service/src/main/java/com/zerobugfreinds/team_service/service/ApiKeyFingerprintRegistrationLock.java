package com.zerobugfreinds.team_service.service;

import com.zerobugfreinds.team_service.exception.ApiKeyRegistrationLockBusyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

@Component
public class ApiKeyFingerprintRegistrationLock {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyFingerprintRegistrationLock.class);

    private static final String REDIS_KEY_PREFIX = "api-key:register:v1:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final int MAX_SPINS = 80;
    private static final long SPIN_MS = 50L;

    private final StringRedisTemplate redisTemplate;
    private final boolean lockEnabled;

    public ApiKeyFingerprintRegistrationLock(
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider,
            @Value("${api.internal.registration-lock.enabled:false}") boolean lockEnabled
    ) {
        this.redisTemplate = stringRedisTemplateProvider.getIfAvailable();
        this.lockEnabled = lockEnabled && this.redisTemplate != null;
        if (lockEnabled && this.redisTemplate == null) {
            log.warn("api.internal.registration-lock.enabled=true 이지만 StringRedisTemplate 빈이 없어 락을 사용하지 않습니다");
        }
    }

    public <T> T runWithLock(String fingerprintHex, Supplier<T> action) {
        if (!lockEnabled) {
            return action.get();
        }
        Objects.requireNonNull(fingerprintHex, "fingerprintHex");
        String key = REDIS_KEY_PREFIX + fingerprintHex.trim().toLowerCase(Locale.ROOT);
        acquire(key);
        boolean txSyncRegistered = false;
        try {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                txSyncRegistered = true;
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        redisTemplate.delete(key);
                    }
                });
            }
            return action.get();
        } finally {
            if (!txSyncRegistered) {
                redisTemplate.delete(key);
            }
        }
    }

    public void runWithLock(String fingerprintHex, Runnable action) {
        runWithLock(fingerprintHex, () -> {
            action.run();
            return null;
        });
    }

    private void acquire(String key) {
        for (int attempt = 0; attempt < MAX_SPINS; attempt++) {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_TTL);
            if (Boolean.TRUE.equals(acquired)) {
                return;
            }
            try {
                Thread.sleep(SPIN_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ApiKeyRegistrationLockBusyException("API 키 등록 락 대기가 중단되었습니다");
            }
        }
        throw new ApiKeyRegistrationLockBusyException(
                "동시에 동일 API 키가 등록되고 있습니다. 잠시 후 다시 시도해 주세요"
        );
    }
}
