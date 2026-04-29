package com.zerobugfreinds.ai_agent_service.controller;

import com.zerobugfreinds.ai_agent_service.service.BillingSignalSnapshotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agents/billing-signals")
public class BillingSignalSnapshotController {

	private final BillingSignalSnapshotService billingSignalSnapshotService;

	public BillingSignalSnapshotController(BillingSignalSnapshotService billingSignalSnapshotService) {
		this.billingSignalSnapshotService = billingSignalSnapshotService;
	}

	@GetMapping
	public List<BillingSignalSnapshotService.BillingKeySignal> list(
			@RequestParam(name = "teamId", required = false) String teamId
	) {
		if (teamId == null || teamId.isBlank()) {
			return billingSignalSnapshotService.findAll();
		}
		return billingSignalSnapshotService.findByTeamId(teamId.trim());
	}
}
