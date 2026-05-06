package com.zerobugfreinds.ai_agent_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class BillingSignalReconciliationService {

	private static final Logger log = LoggerFactory.getLogger(BillingSignalReconciliationService.class);
	private static final ZoneId BILLING_AGG_ZONE = ZoneId.of("Asia/Seoul");

	private final IdentityApiKeySnapshotService identityApiKeySnapshotService;
	private final BillingSignalSnapshotService billingSignalSnapshotService;
	private final ObjectMapper objectMapper;
	private final RestClient restClient;
	private final String gatewaySharedSecret;
	private final boolean enabled;

	public BillingSignalReconciliationService(
			IdentityApiKeySnapshotService identityApiKeySnapshotService,
			BillingSignalSnapshotService billingSignalSnapshotService,
			ObjectMapper objectMapper,
			@Value("${ai-agent.billing-reconcile.base-url:http://billing-service:8095}") String billingBaseUrl,
			@Value("${ai-agent.billing-reconcile.gateway-shared-secret:local-dev-gateway-shared-secret-do-not-use-in-prod}") String gatewaySharedSecret,
			@Value("${ai-agent.billing-reconcile.enabled:true}") boolean enabled
	) {
		this.identityApiKeySnapshotService = identityApiKeySnapshotService;
		this.billingSignalSnapshotService = billingSignalSnapshotService;
		this.objectMapper = objectMapper;
		this.restClient = RestClient.builder().baseUrl(trimTrailingSlash(billingBaseUrl)).build();
		this.gatewaySharedSecret = gatewaySharedSecret;
		this.enabled = enabled;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void hydrateOnStartup() {
		if (!enabled) {
			return;
		}
		reconcileOnce("startup");
	}

	@Scheduled(
			fixedDelayString = "${ai-agent.billing-reconcile.fixed-delay-ms:180000}",
			initialDelayString = "${ai-agent.billing-reconcile.initial-delay-ms:60000}"
	)
	public void reconcileOnSchedule() {
		if (!enabled) {
			return;
		}
		reconcileOnce("scheduled");
	}

	private void reconcileOnce(String reason) {
		List<IdentityApiKeySnapshotService.ApiKeySnapshot> keys = identityApiKeySnapshotService.findAll();
		if (keys.isEmpty()) {
			return;
		}
		LocalDate todayKst = LocalDate.now(BILLING_AGG_ZONE);
		LocalDate from = todayKst.withDayOfMonth(1);
		LocalDate to = todayKst;

		int hydrated = 0;
		for (IdentityApiKeySnapshotService.ApiKeySnapshot key : keys) {
			Long keyId = key.keyId();
			Long userId = key.userId();
			if (keyId == null || keyId <= 0 || userId == null || userId <= 0) {
				continue;
			}
			try {
				BigDecimal totalCost = fetchMonthlyCostUsd(String.valueOf(userId), String.valueOf(keyId), key.provider(), from, to);
				if (totalCost == null) {
					continue;
				}
				billingSignalSnapshotService.upsertReconciledCost(
						String.valueOf(keyId),
						String.valueOf(userId),
						null,
						"API_KEY",
						totalCost,
						normalizeProvider(key.provider())
				);
				hydrated += 1;
			} catch (Exception ex) {
				log.debug("Billing reconciliation skipped for keyId={} userId={} reason={}", keyId, userId, ex.getMessage());
			}
		}
		log.info("Billing reconciliation ({}) completed. hydratedKeys={}", reason, hydrated);
	}

	private BigDecimal fetchMonthlyCostUsd(String userId, String apiKeyId, String provider, LocalDate from, LocalDate to) throws Exception {
		String normalizedProvider = normalizeProvider(provider);
		if (normalizedProvider == null || normalizedProvider.isBlank()) {
			return null;
		}
		String path = "/api/v1/expenditure/summary"
				+ "?apiKeyId=" + URLEncoder.encode(apiKeyId, StandardCharsets.UTF_8)
				+ "&provider=" + URLEncoder.encode(normalizedProvider, StandardCharsets.UTF_8)
				+ "&from=" + URLEncoder.encode(from.toString(), StandardCharsets.UTF_8)
				+ "&to=" + URLEncoder.encode(to.toString(), StandardCharsets.UTF_8);
		String responseBody = restClient.get()
				.uri(path)
				.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
				.header("X-User-Id", userId)
				.header("X-Gateway-Auth", gatewaySharedSecret)
				.retrieve()
				.body(String.class);
		if (responseBody == null || responseBody.isBlank()) {
			return null;
		}
		JsonNode root = objectMapper.readTree(responseBody);
		JsonNode total = root.get("totalCostUsd");
		if (total == null || total.isNull()) {
			return null;
		}
		return new BigDecimal(total.asText("0"));
	}

	private static String normalizeProvider(String provider) {
		if (provider == null || provider.isBlank()) {
			return null;
		}
		return provider.trim().toUpperCase();
	}

	private static String trimTrailingSlash(String value) {
		if (value == null || value.isBlank()) {
			return "http://billing-service:8095";
		}
		String trimmed = value.trim();
		if (trimmed.endsWith("/")) {
			return trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed;
	}
}
