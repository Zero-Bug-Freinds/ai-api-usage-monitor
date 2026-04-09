package com.zerobugfreinds.team_service.entity;

import com.zerobugfreinds.team_service.domain.TeamMemberRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
		name = "team_members",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_team_members_team_user",
				columnNames = {"team_id", "user_id"}
		)
)
public class TeamMemberEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "team_id", nullable = false)
	private Long teamId;

	@Column(name = "user_id", nullable = false, length = 255)
	private String userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, length = 32)
	private TeamMemberRole role;

	@Column(name = "joined_at", nullable = false)
	private Instant joinedAt;

	protected TeamMemberEntity() {
	}

	public static TeamMemberEntity of(Long teamId, String userId, TeamMemberRole role) {
		TeamMemberEntity e = new TeamMemberEntity();
		e.teamId = teamId;
		e.userId = userId;
		e.role = role;
		e.joinedAt = Instant.now();
		return e;
	}

	public Long getTeamId() {
		return teamId;
	}

	public String getUserId() {
		return userId;
	}
}
