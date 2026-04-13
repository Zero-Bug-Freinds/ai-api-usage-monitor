package com.zerobugfreinds.team_service.entity;

import com.zerobugfreinds.team_service.domain.TeamInvitationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "team_invitations")
public class TeamInvitationEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "team_id", nullable = false)
	private Long teamId;

	@Column(name = "inviter_id", nullable = false, length = 255)
	private String inviterId;

	@Column(name = "invitee_id", nullable = false, length = 255)
	private String inviteeId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 32)
	private TeamInvitationStatus status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "responded_at")
	private Instant respondedAt;

	protected TeamInvitationEntity() {
	}

	public static TeamInvitationEntity create(Long teamId, String inviterId, String inviteeId) {
		TeamInvitationEntity e = new TeamInvitationEntity();
		e.teamId = teamId;
		e.inviterId = inviterId;
		e.inviteeId = inviteeId;
		e.status = TeamInvitationStatus.PENDING;
		e.createdAt = Instant.now();
		return e;
	}

	public void accept() {
		this.status = TeamInvitationStatus.ACCEPTED;
		this.respondedAt = Instant.now();
	}

	public void reject() {
		this.status = TeamInvitationStatus.REJECTED;
		this.respondedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Long getTeamId() {
		return teamId;
	}

	public String getInviterId() {
		return inviterId;
	}

	public String getInviteeId() {
		return inviteeId;
	}

	public TeamInvitationStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getRespondedAt() {
		return respondedAt;
	}
}
