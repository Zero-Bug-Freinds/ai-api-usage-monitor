package com.eevee.apigateway.web;

import com.eevee.apigateway.config.GatewayProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class WebEdgeAuthController {

    private static final String HDR_TRUST = "X-Web-Edge-Auth";
    private static final String HDR_AUTH_SUBJECT = "X-Auth-Subject";
    private static final String HDR_AUTH_USER_ID = "X-Auth-UserId";
    private static final String HDR_AUTH_TEAM_ID = "X-Auth-TeamId";
    private static final String HDR_AUTH_SCOPE_TYPE = "X-Auth-Scope-Type";

    private final GatewayProperties gatewayProperties;
    private final ReactiveJwtDecoder jwtDecoder;

    public WebEdgeAuthController(
            GatewayProperties gatewayProperties,
            ReactiveJwtDecoder jwtDecoder
    ) {
        this.gatewayProperties = gatewayProperties;
        this.jwtDecoder = jwtDecoder;
    }

    @GetMapping("/internal/web-edge/auth/resolve")
    public Mono<ResponseEntity<Void>> resolve(
            @RequestHeader(value = HDR_TRUST, required = false) String trustHeader,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        if (!isTrusted(trustHeader) || !StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        String token = authorization.substring(7).trim();
        if (!StringUtils.hasText(token)) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }

        return jwtDecoder.decode(token)
                .flatMap(this::toResponse)
                .onErrorResume(ex -> Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()));
    }

    private Mono<ResponseEntity<Void>> toResponse(Jwt jwt) {
        String subject = jwt.getSubject();
        String userId = jwt.getClaimAsString("userId");
        if (!StringUtils.hasText(subject) || !StringUtils.hasText(userId)) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.add(HDR_AUTH_SUBJECT, subject);
        headers.add(HDR_AUTH_USER_ID, userId);

        String teamId = jwt.getClaimAsString("team_id");
        if (StringUtils.hasText(teamId)) {
            headers.add(HDR_AUTH_TEAM_ID, teamId);
        }
        String scopeType = jwt.getClaimAsString("scope_type");
        if (StringUtils.hasText(scopeType)) {
            headers.add(HDR_AUTH_SCOPE_TYPE, scopeType.toUpperCase());
        }
        return Mono.just(new ResponseEntity<>(headers, HttpStatus.NO_CONTENT));
    }

    private boolean isTrusted(String trustHeader) {
        return StringUtils.hasText(trustHeader) && trustHeader.equals(gatewayProperties.getSharedSecret());
    }
}
