package com.zerobugfreinds.ai_agent_service.mq;

import com.eevee.usage.events.UsagePredictionSignalsEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobugfreinds.ai_agent_service.service.EventDebugService;
import com.zerobugfreinds.ai_agent_service.service.UsagePredictionSignalSnapshotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(
		prefix = "ai-agent.rabbit.usage-prediction",
		name = "enabled",
		havingValue = "true",
		matchIfMissing = true
)
public class UsagePredictionSignalsEventListener {

	private static final Logger log = LoggerFactory.getLogger(UsagePredictionSignalsEventListener.class);

	private final ObjectMapper objectMapper;
	private final UsagePredictionSignalSnapshotService usagePredictionSignalSnapshotService;
	private final EventDebugService eventDebugService;

	public UsagePredictionSignalsEventListener(
			ObjectMapper objectMapper,
			UsagePredictionSignalSnapshotService usagePredictionSignalSnapshotService,
			EventDebugService eventDebugService
	) {
		this.objectMapper = objectMapper;
		this.usagePredictionSignalSnapshotService = usagePredictionSignalSnapshotService;
		this.eventDebugService = eventDebugService;
	}

	@RabbitListener(queues = "${ai-agent.rabbit.usage-prediction.queue}")
	public void onUsagePredictionSignals(Message message) {
		try {
			String body = new String(message.getBody(), StandardCharsets.UTF_8);
			UsagePredictionSignalsEvent event = objectMapper.readValue(body, UsagePredictionSignalsEvent.class);
			Map<String, String> headers = toStringHeaders(message);
			eventDebugService.record("UsagePredictionSignalsEvent", headers, body);
			usagePredictionSignalSnapshotService.upsert(event);
		} catch (Exception ex) {
			log.error("Failed to handle UsagePredictionSignalsEvent", ex);
			throw new IllegalStateException("usage prediction signals handling failed", ex);
		}
	}

	private static Map<String, String> toStringHeaders(Message message) {
		Map<String, String> headers = new LinkedHashMap<>();
		message.getMessageProperties().getHeaders().forEach((key, value) -> headers.put(key, String.valueOf(value)));
		return headers;
	}
}
