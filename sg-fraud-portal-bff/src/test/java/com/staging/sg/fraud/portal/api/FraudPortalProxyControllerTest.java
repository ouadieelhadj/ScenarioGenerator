package com.staging.sg.fraud.portal.api;

import com.staging.sg.fraud.portal.config.FraudPortalProperties;
import com.staging.sg.fraud.portal.service.FraudPortalUpstreamClient;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FraudPortalProxyControllerTest {
    private static final String REGISTRATION = "keycloak";

    @Test
    void obtainsCurrentOrRefreshedTokenAndRelaysItToBatchApi() {
        FraudPortalUpstreamClient upstream = mock(FraudPortalUpstreamClient.class);
        OAuth2AuthorizedClientManager clients = mock(OAuth2AuthorizedClientManager.class);
        FraudPortalProperties properties = new FraudPortalProperties(FraudPortalProperties.Mode.PLATFORM,
                URI.create("https://fraud.example"), REGISTRATION);
        FraudPortalProxyController controller = new FraudPortalProxyController(upstream, clients, properties);
        OAuth2AuthenticationToken authentication = authentication();
        OAuth2AuthorizedClient authorized = authorizedClient(authentication, "renewed-access-token",
                Instant.now().plusSeconds(300));
        when(clients.authorize(any(OAuth2AuthorizeRequest.class))).thenReturn(authorized);
        when(upstream.forward(eq("/batch-jobs"), isNull(), eq(HttpMethod.GET), any(HttpHeaders.class),
                isNull(), eq("renewed-access-token")))
                .thenReturn(ResponseEntity.ok("[]".getBytes()));

        HttpServletRequest request = request("/api/fraud-portal/v1/upstream/batch-jobs");
        ResponseEntity<byte[]> response = controller.proxy(request, new HttpHeaders(), null, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(clients).authorize(argThat(authorize -> REGISTRATION.equals(authorize.getClientRegistrationId())
                && authorize.getPrincipal().getName().equals("tresor-admin")));
        verify(upstream).forward(eq("/batch-jobs"), isNull(), eq(HttpMethod.GET), any(HttpHeaders.class),
                isNull(), eq("renewed-access-token"));
    }

    private OAuth2AuthenticationToken authentication() {
        DefaultOAuth2User principal = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_FRAUD_ADMIN")),
                Map.of("sub", "tresor-admin", "preferred_username", "tresor-admin", "member_id", "TRESOR_BANK"),
                "preferred_username");
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), REGISTRATION);
    }

    private OAuth2AuthorizedClient authorizedClient(OAuth2AuthenticationToken authentication, String token,
            Instant expiresAt) {
        ClientRegistration registration = ClientRegistration.withRegistrationId(REGISTRATION)
                .clientId("fraud-portal")
                .clientSecret("not-a-real-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://fraud.example/login/oauth2/code/keycloak")
                .authorizationUri("https://identity.example/auth")
                .tokenUri("https://identity.example/token")
                .userInfoUri("https://identity.example/userinfo")
                .userNameAttributeName("preferred_username")
                .clientName("Keycloak")
                .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, token,
                Instant.now().minusSeconds(30), expiresAt);
        return new OAuth2AuthorizedClient(registration, authentication.getName(), accessToken);
    }

    private HttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }
}
