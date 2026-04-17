package com.zerobugfreinds.team_service.repository;

import com.zerobugfreinds.team_service.entity.IdentityUserSyncEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityUserSyncRepository extends JpaRepository<IdentityUserSyncEntity, String> {
    boolean existsByEmailIgnoreCase(String email);
}
