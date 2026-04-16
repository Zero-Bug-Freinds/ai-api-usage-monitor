package com.zerobugfreinds.team_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class IdentityUserLookupClient {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String identityServiceBaseUrl;

    public IdentityUserLookupClient(
            ObjectMapper objectMapper,
            @Value("${identity.service.url:http://host.docker.internal:8090}") String identityServiceBaseUrl
    ) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        this.objectMapper = objectMapper;
        this.identityServiceBaseUrl = identityServiceBaseUrl.replaceAll("/+$", "");
    }

    public boolean existsByEmail(String email) {
        URI uri = UriComponentsBuilder
                .fromUriString(identityServiceBaseUrl + "/internal/users/exists")
                .queryParam("email", email)
                .build(true)
                .toUri();

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(5))
                .GET()
                .header("Accept", "application/json")
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return false;
            }
            String body = response.body();
            return body != null && body.contains("\"data\":true");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException ex) {
            return false;
        }
    }

    public Set<String> findExistingUserIds(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Set.of();
        }
        URI uri = URI.create(identityServiceBaseUrl + "/internal/users/exists/user-ids");
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(new UserIdsExistenceRequest(userIds));
        } catch (IOException ex) {
            return Set.of();
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Set.of();
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode existingUserIds = root.path("data").path("existingUserIds");
            if (!existingUserIds.isArray()) {
                return Set.of();
            }
            Set<String> result = new LinkedHashSet<>();
            for (JsonNode existingUserId : existingUserIds) {
                if (existingUserId.isTextual()) {
                    result.add(existingUserId.asText());
                }
            }
            return result;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Set.of();
        } catch (IOException ex) {
            return Set.of();
        }
    }

    private record UserIdsExistenceRequest(
            List<String> userIds
    ) {
    }
}
