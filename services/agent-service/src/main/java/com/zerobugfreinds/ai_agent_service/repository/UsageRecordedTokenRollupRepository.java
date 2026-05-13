package com.zerobugfreinds.ai_agent_service.repository;

import com.zerobugfreinds.ai_agent_service.entity.UsageRecordedTokenRollupEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface UsageRecordedTokenRollupRepository extends JpaRepository<
		UsageRecordedTokenRollupEntity,
		UsageRecordedTokenRollupEntity.UsageRecordedTokenRollupId> {

	Optional<UsageRecordedTokenRollupEntity> findByKeyIdAndScopeTypeAndScopeIdAndDay(
			String keyId,
			String scopeType,
			String scopeId,
			LocalDate day
	);

	List<UsageRecordedTokenRollupEntity> findByKeyIdAndScopeTypeAndScopeIdAndDayBetween(
			String keyId,
			String scopeType,
			String scopeId,
			LocalDate from,
			LocalDate to
	);

	long deleteByKeyId(String keyId);
}
