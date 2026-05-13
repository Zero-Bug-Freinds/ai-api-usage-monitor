package com.zerobugfreinds.identity_service.repository;

import com.zerobugfreinds.identity_service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 사용자 영속성 계층.
 * 이메일은 소문자로 저장·조회한다({@link com.zerobugfreinds.identity_service.service.UserService#normalizeEmail}).
 */
public interface UserRepository extends JpaRepository<User, Long> {

	/**
	 * 회원가입 시 이메일 중복 여부 확인 (이메일은 정규화된 소문자).
	 */
	boolean existsByEmail(String email);

	/**
	 * 로그인 등: 정규화된 소문자 이메일로 조회.
	 */
	Optional<User> findByEmail(String email);
}
