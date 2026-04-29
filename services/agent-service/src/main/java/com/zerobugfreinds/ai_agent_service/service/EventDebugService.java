package com.zerobugfreinds.ai_agent_service.service;

import com.zerobugfreinds.ai_agent_service.dto.EventDebugDto;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
public class EventDebugService {

	private static final int MAX_EVENT_HISTORY = 50;

	private final ConcurrentLinkedDeque<EventDebugDto> events = new ConcurrentLinkedDeque<>();

	public void record(String eventType, Map<String, String> headers, String payload) {
		Map<String, String> safeHeaders = headers == null ? Map.of() : new LinkedHashMap<>(headers);
		String safeType = eventType == null || eventType.isBlank() ? "UNKNOWN" : eventType;
		String safePayload = payload == null ? "" : payload;

		events.addFirst(new EventDebugDto(
				LocalDateTime.now(),
				safeType,
				safeHeaders,
				safePayload
		));
		trimToMaxSize(MAX_EVENT_HISTORY);
	}

	public List<EventDebugDto> recent(int limit) {
		int safeLimit = Math.max(1, limit);
		List<EventDebugDto> snapshots = new ArrayList<>(safeLimit);
		int count = 0;
		for (EventDebugDto event : events) {
			snapshots.add(event);
			count += 1;
			if (count >= safeLimit) {
				break;
			}
		}
		return snapshots;
	}

	private void trimToMaxSize(int maxSize) {
		while (events.size() > maxSize) {
			events.pollLast();
		}
	}
}
