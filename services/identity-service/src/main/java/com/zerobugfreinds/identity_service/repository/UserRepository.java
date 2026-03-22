package com.zerobugfreinds.identity_service.repository;

import com.zerobugfreinds.identity_service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 사용자 영속성 계층.
 */
public interface UserRepository extends JpaRepository<User, Long> {

	/**
	 * 회원가입 시 이메일 중복 여부 확인.
	 *
	 * @param email 이메일
	 * @return 이미 존재하면 true
	 */
	boolean existsByEmail(String email);
}
