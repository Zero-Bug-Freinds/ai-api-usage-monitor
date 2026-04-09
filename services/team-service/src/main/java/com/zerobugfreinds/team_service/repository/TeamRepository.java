package com.zerobugfreinds.team_service.repository;

import com.zerobugfreinds.team_service.entity.TeamEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<TeamEntity, Long> {
}
