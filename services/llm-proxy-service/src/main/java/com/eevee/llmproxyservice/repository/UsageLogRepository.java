package com.eevee.llmproxyservice.repository;

import com.eevee.llmproxyservice.domain.UsageLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsageLogRepository extends JpaRepository<UsageLog, Long> {
}
