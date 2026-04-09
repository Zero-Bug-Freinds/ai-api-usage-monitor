package com.zerobugfreinds.team_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "teams")
public class TeamEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "created_by", nullable = false, length = 255)
	private String createdBy;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected TeamEntity() {
	}

	public static TeamEntity create(String name, String createdBy) {
		TeamEntity e = new TeamEntity();
		e.name = name;
		e.createdBy = createdBy;
		e.createdAt = Instant.now();
		return e;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
