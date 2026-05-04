package com.zerobugfreinds.ai_agent_service.controller;

import com.zerobugfreinds.ai_agent_service.service.DailyCumulativeTokenSnapshotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agents/daily-cumulative-tokens")
public class DailyCumulativeTokenSnapshotController {

	private final DailyCumulativeTokenSnapshotService dailyCumulativeTokenSnapshotService;

	public DailyCumulativeTokenSnapshotController(DailyCumulativeTokenSnapshotService dailyCumulativeTokenSnapshotService) {
		this.dailyCumulativeTokenSnapshotService = dailyCumulativeTokenSnapshotService;
	}

	@GetMapping
	public List<DailyCumulativeTokenSnapshotService.DailyCumulativeTokenSnapshot> list() {
		return dailyCumulativeTokenSnapshotService.findAll();
	}
}
