package com.zerobugfreinds.team_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class IdentityUserLookupClient {
    private final HttpClient httpClient;
    private final String identityServiceBaseUrl;

    public IdentityUserLookupClient(
            @Value("${identity.service.url:http://host.docker.internal:8090}") String identityServiceBaseUrl
    ) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
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
}
