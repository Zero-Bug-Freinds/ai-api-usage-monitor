package com.zerobugfreinds.identity_service.service;

import com.zerobugfreinds.identity.events.IdentityUserSyncEvent;
import com.zerobugfreinds.identity.events.IdentityUserSyncEventTypes;
import com.zerobugfreinds.identity_service.entity.User;
import com.zerobugfreinds.identity_service.mq.IdentityUserSyncEventPublisher;
import com.zerobugfreinds.identity_service.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.util.StringUtils;

import java.time.Instant;

/**
 * 기존 사용자에 대한 {@link IdentityUserSyncEvent} 재발행(백필). team-service 가 MQ 로 {@code identity_user_sync} 를 채운다.
 */
@Service
public class IdentityUserSyncReplayService {

    private static final int PAGE_SIZE = 500;

    private final UserRepository userRepository;
    private final IdentityUserSyncEventPublisher identityUserSyncEventPublisher;

    public IdentityUserSyncReplayService(
            UserRepository userRepository,
            IdentityUserSyncEventPublisher identityUserSyncEventPublisher
    ) {
        this.userRepository = userRepository;
        this.identityUserSyncEventPublisher = identityUserSyncEventPublisher;
    }

    /**
     * 모든 사용자에 대해 즉시 동기화 이벤트를 발행한다. 멱등하게 team 측에서 upsert 된다.
     *
     * @return 발행한 이벤트 건수
     */
    @Transactional(readOnly = true)
    public int publishSyncEventsForAllUsers() {
        int published = 0;
        int pageIndex = 0;
        Page<User> page;
        do {
            page = userRepository.findAll(PageRequest.of(pageIndex, PAGE_SIZE));
            for (User user : page.getContent()) {
                if (user.getId() == null || !StringUtils.hasText(user.getEmail())) {
                    continue;
                }
                String principalSub = user.getEmail().trim().toLowerCase();
                identityUserSyncEventPublisher.publishImmediately(
                        IdentityUserSyncEvent.of(
                                IdentityUserSyncEventTypes.USER_SYNC_BACKFILL,
                                principalSub,
                                user.getEmail(),
                                user.getName(),
                                Instant.now()
                        )
                );
                published++;
            }
            pageIndex++;
        } while (page.hasNext());
        return published;
    }
}
