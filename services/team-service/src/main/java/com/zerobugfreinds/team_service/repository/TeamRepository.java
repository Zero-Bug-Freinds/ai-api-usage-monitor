package com.zerobugfreinds.team_service.repository;

import com.zerobugfreinds.team_service.entity.TeamEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TeamRepository extends JpaRepository<TeamEntity, Long> {
	Page<TeamEntity> findByNameContainingIgnoreCase(String keyword, Pageable pageable);
}
