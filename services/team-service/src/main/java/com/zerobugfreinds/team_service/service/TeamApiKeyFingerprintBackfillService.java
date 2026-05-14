package com.zerobugfreinds.team_service.service;

import com.zerobugfreinds.team_service.entity.TeamApiKeyEntity;
import com.zerobugfreinds.team_service.repository.TeamApiKeyRepository;
import com.zerobugfreinds.team_service.util.EncryptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TeamApiKeyFingerprintBackfillService {

    private static final Logger log = LoggerFactory.getLogger(TeamApiKeyFingerprintBackfillService.class);

    private final TeamApiKeyRepository teamApiKeyRepository;
    private final EncryptionUtil encryptionUtil;

    public TeamApiKeyFingerprintBackfillService(
            TeamApiKeyRepository teamApiKeyRepository,
            EncryptionUtil encryptionUtil
    ) {
        this.teamApiKeyRepository = teamApiKeyRepository;
        this.encryptionUtil = encryptionUtil;
    }

    @Transactional
    public int fillNextBatch() {
        List<TeamApiKeyEntity> batch = teamApiKeyRepository.findTop100ByApiKeyFingerprintIsNullOrderByIdAsc();
        if (batch.isEmpty()) {
            return 0;
        }
        int updated = 0;
        for (TeamApiKeyEntity entity : batch) {
            try {
                String plain = encryptionUtil.decryptAes256Gcm(entity.getEncryptedKey()).trim();
                String fp = encryptionUtil.sha256HexRawApiKeyUtf8(plain);
                entity.assignApiKeyFingerprintForBackfill(fp);
                teamApiKeyRepository.saveAndFlush(entity);
                updated++;
            } catch (DataIntegrityViolationException ex) {
                log.warn(
                        "team_api_key fingerprint backfill skip duplicate provider={} keyId={} message={}",
                        entity.getProvider().name(),
                        entity.getId(),
                        ex.getMessage()
                );
            } catch (RuntimeException ex) {
                log.warn(
                        "team_api_key fingerprint backfill skip keyId={} cause={}",
                        entity.getId(),
                        ex.toString()
                );
            }
        }
        return updated;
    }
}
