package com.zerobugfreinds.identity_service.repository;

import com.zerobugfreinds.identity_service.entity.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {
    void deleteAllByUserId(Long userId);
}
