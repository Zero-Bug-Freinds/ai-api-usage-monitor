package com.zerobugfreinds.team_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 팀 이벤트 발행 이력(outbox) 저장 엔티티.
 */
@Entity
@Table(
        name = "team_event_outbox",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_team_event_outbox_event_id", columnNames = "event_id")
        }
)
public class TeamEventOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "published", nullable = false, columnDefinition = "boolean default false")
    private boolean published = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TeamEventOutbox() {
    }

    public static TeamEventOutbox create(
            String eventId,
            Long aggregateId,
            String eventType,
            String payload
    ) {
        TeamEventOutbox entity = new TeamEventOutbox();
        entity.eventId = eventId;
        entity.aggregateId = aggregateId;
        entity.eventType = eventType;
        entity.payload = payload;
        entity.published = false;
        entity.createdAt = Instant.now();
        return entity;
    }

    public void markPublished() {
        this.published = true;
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public Long getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public boolean isPublished() {
        return published;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
