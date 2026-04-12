package com.zerobugfreinds.identity_service.repository;

import com.zerobugfreinds.identity_service.entity.PasswordResetToken;
import com.zerobugfreinds.identity_service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 비밀번호 재설정 토큰 영속성.
 */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

	Optional<PasswordResetToken> findByTokenHash(String tokenHash);

	void deleteByUser(User user);
}
