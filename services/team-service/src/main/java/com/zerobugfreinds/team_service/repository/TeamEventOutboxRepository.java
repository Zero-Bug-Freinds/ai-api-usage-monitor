package com.zerobugfreinds.team_service.repository;

import com.zerobugfreinds.team_service.entity.TeamEventOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeamEventOutboxRepository extends JpaRepository<TeamEventOutbox, Long> {
    boolean existsByEventId(String eventId);

    Optional<TeamEventOutbox> findByEventId(String eventId);
}
