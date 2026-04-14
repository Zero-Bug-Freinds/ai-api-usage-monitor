package com.zerobugfreinds.identity_service.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zerobugfreinds.identity.events.UserAccountDeletionAcknowledgedEvent;
import com.zerobugfreinds.identity_service.service.AccountDeletionCoordinationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * billing·usage·team 이 보내는 삭제 완료 ACK 를 소비한다.
 */
@Component
public class UserAccountDeletionAckListener {

	private static final Logger log = LoggerFactory.getLogger(UserAccountDeletionAckListener.class);

	private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());

	private final AccountDeletionCoordinationService coordinationService;

	public UserAccountDeletionAckListener(AccountDeletionCoordinationService coordinationService) {
		this.coordinationService = coordinationService;
	}

	@RabbitListener(queues = "${identity.account-deletion-ack.queue:identity.account-deletion.ack.queue}")
	public void onAck(String body) {
		try {
			UserAccountDeletionAcknowledgedEvent ack = JSON.readValue(body, UserAccountDeletionAcknowledgedEvent.class);
			coordinationService.applyAck(ack);
		} catch (Exception e) {
			log.error("Invalid UserAccountDeletionAcknowledgedEvent payload: {}", e.getMessage());
			throw new AmqpRejectAndDontRequeueException(e);
		}
	}
}
