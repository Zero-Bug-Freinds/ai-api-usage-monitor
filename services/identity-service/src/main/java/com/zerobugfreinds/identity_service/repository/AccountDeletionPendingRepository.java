package com.zerobugfreinds.identity_service.repository;

import com.zerobugfreinds.identity_service.entity.AccountDeletionPendingEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountDeletionPendingRepository extends JpaRepository<AccountDeletionPendingEntity, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT p FROM AccountDeletionPendingEntity p WHERE p.userId = :userId")
	Optional<AccountDeletionPendingEntity> findLockedByUserId(@Param("userId") Long userId);
}
