package com.zerobugfreinds.ai_agent_service.controller;

import com.zerobugfreinds.ai_agent_service.dto.OptimizationRecommendationIssuedEvent;
import com.zerobugfreinds.ai_agent_service.dto.PolicyRecommendationRequest;
import com.zerobugfreinds.ai_agent_service.dto.PolicyRecommendationResponse;
import com.zerobugfreinds.ai_agent_service.dto.RecommendationAnalyzeRequest;
import com.zerobugfreinds.ai_agent_service.dto.RecommendationQueryResponse;
import com.zerobugfreinds.ai_agent_service.dto.RecommendationScopeType;
import com.zerobugfreinds.ai_agent_service.service.PolicyRecommendationAgentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agents/policy-recommendations")
public class AiPolicyAgentController {

	private final PolicyRecommendationAgentService policyRecommendationAgentService;

	public AiPolicyAgentController(PolicyRecommendationAgentService policyRecommendationAgentService) {
		this.policyRecommendationAgentService = policyRecommendationAgentService;
	}

	@PostMapping
	public ResponseEntity<PolicyRecommendationResponse> recommend(
			@Valid @RequestBody PolicyRecommendationRequest request
	) {
		PolicyRecommendationResponse response = policyRecommendationAgentService.recommend(request);
		return ResponseEntity.ok(response);
	}

	@PostMapping("/analyze")
	public ResponseEntity<OptimizationRecommendationIssuedEvent> analyze(
			@Valid @RequestBody RecommendationAnalyzeRequest request
	) {
		OptimizationRecommendationIssuedEvent response = policyRecommendationAgentService.analyzeAndStore(request);
		return ResponseEntity.ok(response);
	}

	@GetMapping("/{keyId}")
	public ResponseEntity<RecommendationQueryResponse> getRecommendation(
			@PathVariable String keyId,
			@RequestParam RecommendationScopeType scopeType,
			@RequestParam String scopeId
	) {
		RecommendationQueryResponse response = policyRecommendationAgentService.getRecommendation(scopeType, scopeId, keyId);
		return ResponseEntity.ok(response);
	}
}
