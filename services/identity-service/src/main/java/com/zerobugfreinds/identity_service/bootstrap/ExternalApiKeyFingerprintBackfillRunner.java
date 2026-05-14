package com.zerobugfreinds.identity_service.bootstrap;

import com.zerobugfreinds.identity_service.service.ExternalApiKeyFingerprintBackfillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * {@code api.internal.fingerprint-backfill.enabled=true} 일 때 기동 직후 fingerprint 백필을 반복 실행한다.
 */
@Component
@Order(5000)
@ConditionalOnProperty(name = "api.internal.fingerprint-backfill.enabled", havingValue = "true")
public class ExternalApiKeyFingerprintBackfillRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(ExternalApiKeyFingerprintBackfillRunner.class);

	private final ExternalApiKeyFingerprintBackfillService backfillService;

	public ExternalApiKeyFingerprintBackfillRunner(ExternalApiKeyFingerprintBackfillService backfillService) {
		this.backfillService = backfillService;
	}

	@Override
	public void run(ApplicationArguments args) {
		int total = 0;
		while (true) {
			int n = backfillService.fillNextBatch();
			total += n;
			if (n == 0) {
				break;
			}
		}
		log.info("external_api_key fingerprint backfill runner finished updatedApprox={}", total);
	}
}
