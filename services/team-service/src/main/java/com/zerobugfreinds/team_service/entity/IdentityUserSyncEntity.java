package com.zerobugfreinds.team_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "identity_user_sync")
public class IdentityUserSyncEntity {

    @Id
    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "last_event_type", nullable = false, length = 64)
    private String lastEventType;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IdentityUserSyncEntity() {
    }

    public static IdentityUserSyncEntity create(
            String userId,
            String email,
            String displayName,
            String lastEventType,
            Instant updatedAt
    ) {
        IdentityUserSyncEntity entity = new IdentityUserSyncEntity();
        entity.userId = userId;
        entity.email = email;
        entity.displayName = displayName;
        entity.lastEventType = lastEventType;
        entity.updatedAt = updatedAt;
        return entity;
    }

    public void apply(String email, String displayName, String lastEventType, Instant updatedAt) {
        this.email = email;
        this.displayName = displayName;
        this.lastEventType = lastEventType;
        this.updatedAt = updatedAt;
    }

    public String getUserId() {
        return userId;
    }
}
