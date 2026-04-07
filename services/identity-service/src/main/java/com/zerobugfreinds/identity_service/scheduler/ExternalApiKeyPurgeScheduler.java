package com.zerobugfreinds.identity_service.scheduler;

import com.zerobugfreinds.identity_service.service.ExternalApiKeyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 유예 기간이 지난 외부 API 키 행을 주기적으로 제거한다.
 */
@Component
public class ExternalApiKeyPurgeScheduler {

	private static final Logger log = LoggerFactory.getLogger(ExternalApiKeyPurgeScheduler.class);

	private final ExternalApiKeyService externalApiKeyService;

	public ExternalApiKeyPurgeScheduler(ExternalApiKeyService externalApiKeyService) {
		this.externalApiKeyService = externalApiKeyService;
	}

	@Scheduled(fixedDelayString = "${identity.external-api-key.purge-fixed-delay-ms:3600000}", initialDelayString = "${identity.external-api-key.purge-initial-delay-ms:60000}")
	public void purgeExpiredKeys() {
		int removed = externalApiKeyService.purgeExpiredKeys();
		if (removed > 0) {
			log.info("external_api_key purge completed removedRows={}", removed);
		}
	}
}
