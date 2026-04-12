package com.zerobugfreinds.identity_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 플랫폼 사용자 엔티티.
 * DB 테이블·컬럼명은 snake_case 로 매핑한다.
 */
@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "email", nullable = false, unique = true, length = 255)
	private String email;

	@Column(name = "password", nullable = false, length = 255)
	private String password;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, length = 20)
	private Role role;

	protected User() {
	}

	public User(String email, String password, String name, Role role) {
		this.email = email;
		this.password = password;
		this.name = name;
		this.role = role;
	}

	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getPassword() {
		return password;
	}

	public String getName() {
		return name;
	}

	public Role getRole() {
		return role;
	}

	/**
	 * BCrypt 등으로 인코딩된 비밀번호로 갱신한다 (비밀번호 재설정 등).
	 */
	public void setPassword(String password) {
		this.password = password;
	}
}
