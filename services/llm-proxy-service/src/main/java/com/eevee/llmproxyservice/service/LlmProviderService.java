package com.eevee.llmproxyservice.service;

import org.springframework.http.ResponseEntity;

public interface LlmProviderService {

    ResponseEntity<String> forwardAndLog(String payload, String apiKey, String uriPath);
}
