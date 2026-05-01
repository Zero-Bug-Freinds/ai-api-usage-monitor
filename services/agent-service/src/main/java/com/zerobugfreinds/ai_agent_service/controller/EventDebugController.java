package com.zerobugfreinds.ai_agent_service.controller;

import com.zerobugfreinds.ai_agent_service.dto.EventDebugDto;
import com.zerobugfreinds.ai_agent_service.service.EventDebugService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agents/debug/events")
public class EventDebugController {

	private final EventDebugService eventDebugService;

	public EventDebugController(EventDebugService eventDebugService) {
		this.eventDebugService = eventDebugService;
	}

	@GetMapping
	public List<EventDebugDto> recentEvents(@RequestParam(name = "limit", defaultValue = "50") int limit) {
		return eventDebugService.recent(limit);
	}
}
