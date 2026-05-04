package com.zerobugfreinds.ai_agent_service.controller;

import com.zerobugfreinds.ai_agent_service.service.UsagePredictionSignalSnapshotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agents/usage-prediction-signals")
public class UsagePredictionSignalSnapshotController {

	private final UsagePredictionSignalSnapshotService usagePredictionSignalSnapshotService;

	public UsagePredictionSignalSnapshotController(
			UsagePredictionSignalSnapshotService usagePredictionSignalSnapshotService
	) {
		this.usagePredictionSignalSnapshotService = usagePredictionSignalSnapshotService;
	}

	@GetMapping
	public List<UsagePredictionSignalSnapshotService.UsagePredictionSignalSnapshot> list() {
		return usagePredictionSignalSnapshotService.findAll();
	}
}
