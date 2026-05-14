package com.zerobugfreinds.identity_service.service;

import com.zerobugfreinds.identity_service.entity.ExternalApiKeyEntity;
import com.zerobugfreinds.identity_service.repository.ExternalApiKeyRepository;
import com.zerobugfreinds.identity_service.util.EncryptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 기존 행의 {@code api_key_fingerprint} 를 암호문 복호화 후 채운다. 운영에서는 속성으로 켠 뒤 1회 실행을 권장한다.
 */
@Service
public class ExternalApiKeyFingerprintBackfillService {

	private static final Logger log = LoggerFactory.getLogger(ExternalApiKeyFingerprintBackfillService.class);

	private final ExternalApiKeyRepository externalApiKeyRepository;
	private final EncryptionUtil encryptionUtil;

	public ExternalApiKeyFingerprintBackfillService(
			ExternalApiKeyRepository externalApiKeyRepository,
			EncryptionUtil encryptionUtil
	) {
		this.externalApiKeyRepository = externalApiKeyRepository;
		this.encryptionUtil = encryptionUtil;
	}

	@Transactional
	public int fillNextBatch() {
		List<ExternalApiKeyEntity> batch = externalApiKeyRepository.findTop100ByApiKeyFingerprintIsNullOrderByIdAsc();
		if (batch.isEmpty()) {
			return 0;
		}
		int updated = 0;
		for (ExternalApiKeyEntity entity : batch) {
			try {
				String plain = encryptionUtil.decryptAes256Gcm(entity.getEncryptedKey()).trim();
				String fp = encryptionUtil.sha256HexUtf8(plain);
				entity.assignApiKeyFingerprintForBackfill(fp);
				externalApiKeyRepository.saveAndFlush(entity);
				updated++;
			} catch (DataIntegrityViolationException ex) {
				log.warn(
						"external_api_key fingerprint backfill skip duplicate provider={} keyId={} message={}",
						entity.getProvider().name(),
						entity.getId(),
						ex.getMessage()
				);
			} catch (RuntimeException ex) {
				log.warn(
						"external_api_key fingerprint backfill skip keyId={} cause={}",
						entity.getId(),
						ex.toString()
				);
			}
		}
		return updated;
	}
}
