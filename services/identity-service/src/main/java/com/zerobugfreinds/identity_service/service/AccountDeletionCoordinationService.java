package com.zerobugfreinds.identity_service.service;

import com.zerobugfreinds.identity.events.UserAccountDeletionAcknowledgedEvent;
import com.zerobugfreinds.identity_service.entity.AccountDeletionPendingEntity;
import com.zerobugfreinds.identity_service.entity.User;
import com.zerobugfreinds.identity_service.repository.AccountDeletionPendingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

/**
 * 탈퇴 대기 행 등록과, billing·usage·team ACK 수신 후 최종 로컬 삭제를 조율한다.
 */
@Service
public class AccountDeletionCoordinationService {

	private static final Logger log = LoggerFactory.getLogger(AccountDeletionCoordinationService.class);

	private final AccountDeletionPendingRepository pendingRepository;
	private final IdentityAccountLocalDeletionService identityAccountLocalDeletionService;

	public AccountDeletionCoordinationService(
			AccountDeletionPendingRepository pendingRepository,
			IdentityAccountLocalDeletionService identityAccountLocalDeletionService
	) {
		this.pendingRepository = pendingRepository;
		this.identityAccountLocalDeletionService = identityAccountLocalDeletionService;
	}

	/**
	 * 삭제 요청 이벤트 발행 직전에 호출한다. 동일 사용자 재요청 시 ACK 를 초기화하고 다시 기다린다.
	 */
	@Transactional
	public void registerDeletionRequested(User user) {
		AccountDeletionPendingEntity pending = pendingRepository.findById(user.getId())
				.orElseGet(() -> AccountDeletionPendingEntity.create(user.getId(), user.getEmail()));
		pending.setUserEmail(user.getEmail());
		pending.setAckBilling(false);
		pending.setAckUsage(false);
		pending.setAckTeam(false);
		pendingRepository.save(pending);
	}

	@Transactional
	public void applyAck(UserAccountDeletionAcknowledgedEvent ack) {
		Optional<AccountDeletionPendingEntity> locked = pendingRepository.findLockedByUserId(ack.identityUserId());
		if (locked.isEmpty()) {
			log.warn("account_deletion_pending not found for identityUserId={}", ack.identityUserId());
			return;
		}
		AccountDeletionPendingEntity pending = locked.get();
		if (!pending.getUserEmail().equalsIgnoreCase(ack.userEmail())) {
			log.warn(
					"account deletion ack email mismatch identityUserId={} expectedEmail={} ackEmail={}",
					ack.identityUserId(),
					pending.getUserEmail(),
					ack.userEmail()
			);
			return;
		}
		String src = ack.source().toLowerCase(Locale.ROOT);
		switch (src) {
			case UserAccountDeletionAcknowledgedEvent.SOURCE_BILLING -> pending.setAckBilling(true);
			case UserAccountDeletionAcknowledgedEvent.SOURCE_USAGE -> pending.setAckUsage(true);
			case UserAccountDeletionAcknowledgedEvent.SOURCE_TEAM -> pending.setAckTeam(true);
			default -> {
				log.warn("Unknown account deletion ack source={} identityUserId={}", ack.source(), ack.identityUserId());
				return;
			}
		}
		pendingRepository.save(pending);
		if (pending.allAcknowledged()) {
			identityAccountLocalDeletionService.deleteAllDataForUser(pending.getUserId());
		}
	}
}
