package com.staging.sg.fraud.portal.api;

import com.staging.sg.fraud.portal.config.FraudPortalProperties;
import com.staging.sg.fraud.portal.service.FraudPortalUpstreamClient;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@RestController
public class FraudPortalProxyController {
    private static final String PREFIX = "/api/fraud-portal/v1/upstream";
    private final FraudPortalUpstreamClient upstream;
    private final OAuth2AuthorizedClientManager clients;
    private final FraudPortalProperties properties;

    public FraudPortalProxyController(FraudPortalUpstreamClient upstream, OAuth2AuthorizedClientManager clients,
            FraudPortalProperties properties) {
        this.upstream = upstream;
        this.clients = clients;
        this.properties = properties;
    }

    @RequestMapping(PREFIX + "/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request, @RequestHeader HttpHeaders headers,
            @RequestBody(required = false) byte[] body, OAuth2AuthenticationToken authentication) {
        String expectedRegistration = properties.clientRegistrationId();
        if (!expectedRegistration.equals(authentication.getAuthorizedClientRegistrationId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unexpected identity provider");
        }
        OAuth2AuthorizedClient client = clients.authorize(OAuth2AuthorizeRequest
                .withClientRegistrationId(expectedRegistration)
                .principal(authentication)
                .build());
        if (client == null || client.getAccessToken() == null
                || (client.getAccessToken().getExpiresAt() != null
                && !client.getAccessToken().getExpiresAt().isAfter(Instant.now()))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Keycloak session has no valid access token; authentication must be renewed");
        }
        String relative = request.getRequestURI().substring(PREFIX.length());
        return upstream.forward(relative, request.getQueryString(), HttpMethod.valueOf(request.getMethod()),
                headers, body, client.getAccessToken().getTokenValue());
    }
}
