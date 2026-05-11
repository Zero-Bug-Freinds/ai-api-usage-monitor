package com.zerobugfreinds.ai_agent_service.repository;

import com.zerobugfreinds.ai_agent_service.entity.BudgetForecastSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetForecastSnapshotRepository extends JpaRepository<BudgetForecastSnapshotEntity, Long> {
}
