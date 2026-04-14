package com.zerobugfreinds.identity_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 탈퇴 요청 후 다른 서비스 ACK 를 모을 때까지 identity 사용자 행을 유지한다.
 */
@Entity
@Table(name = "account_deletion_pending")
public class AccountDeletionPendingEntity {

	@Id
	@Column(name = "user_id")
	private Long userId;

	@Column(name = "user_email", nullable = false, length = 255)
	private String userEmail;

	@Column(name = "ack_billing", nullable = false)
	private boolean ackBilling;

	@Column(name = "ack_usage", nullable = false)
	private boolean ackUsage;

	@Column(name = "ack_team", nullable = false)
	private boolean ackTeam;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected AccountDeletionPendingEntity() {
	}

	public static AccountDeletionPendingEntity create(Long userId, String userEmail) {
		AccountDeletionPendingEntity e = new AccountDeletionPendingEntity();
		e.userId = userId;
		e.userEmail = userEmail.trim();
		e.createdAt = Instant.now();
		return e;
	}

	public Long getUserId() {
		return userId;
	}

	public String getUserEmail() {
		return userEmail;
	}

	public void setUserEmail(String userEmail) {
		this.userEmail = userEmail != null ? userEmail.trim() : "";
	}

	public boolean isAckBilling() {
		return ackBilling;
	}

	public void setAckBilling(boolean ackBilling) {
		this.ackBilling = ackBilling;
	}

	public boolean isAckUsage() {
		return ackUsage;
	}

	public void setAckUsage(boolean ackUsage) {
		this.ackUsage = ackUsage;
	}

	public boolean isAckTeam() {
		return ackTeam;
	}

	public void setAckTeam(boolean ackTeam) {
		this.ackTeam = ackTeam;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public boolean allAcknowledged() {
		return ackBilling && ackUsage && ackTeam;
	}
}
