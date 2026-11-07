package com.staging.sg.fraud.portal.config;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "fraud.portal.mode=PLATFORM",
        "fraud.portal.upstream-base-url=https://fraud.example",
        "fraud.portal.client-registration-id=keycloak",
        "fraud.portal.oidc-end-session-uri=https://identity.example/logout",
        "spring.security.oauth2.client.registration.keycloak.provider=keycloak",
        "spring.security.oauth2.client.registration.keycloak.client-id=fraud-portal",
        "spring.security.oauth2.client.registration.keycloak.client-secret=test-only",
        "spring.security.oauth2.client.registration.keycloak.authorization-grant-type=authorization_code",
        "spring.security.oauth2.client.registration.keycloak.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
        "spring.security.oauth2.client.registration.keycloak.scope=openid,profile",
        "spring.security.oauth2.client.provider.keycloak.authorization-uri=https://identity.example/authorize",
        "spring.security.oauth2.client.provider.keycloak.token-uri=https://identity.example/token",
        "spring.security.oauth2.client.provider.keycloak.jwk-set-uri=https://identity.example/certs",
        "spring.security.oauth2.client.provider.keycloak.user-info-uri=https://identity.example/userinfo",
        "spring.security.oauth2.client.provider.keycloak.user-name-attribute=preferred_username"
})
@AutoConfigureMockMvc
class FraudPortalLogoutIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    @Test
    void logoutTerminatesBffSessionAndRedirectsToOidcEndSessionEndpoint() throws Exception {
        mockMvc.perform(post("/api/fraud-portal/v1/logout")
                        .secure(true)
                        .with(csrf())
                        .with(oidcLogin().clientRegistration(keycloakRegistration())
                                .idToken(token -> token.tokenValue("opaque-id-token")
                                        .claim("sub", "ouadie-admin")
                                        .claim("preferred_username", "ouadie-admin")
                                        .claim("member_id", "OUADIE_BANK")))
                        .cookie(new Cookie("JSESSIONID", "opaque-session"),
                                new Cookie("XSRF-TOKEN", "opaque-csrf")))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("https://identity.example/logout")))
                .andExpect(header().string("Location", containsString("id_token_hint=opaque-id-token")))
                .andExpect(header().string("Location", containsString("post_logout_redirect_uri=")))
                .andExpect(cookie().maxAge("JSESSIONID", 0))
                .andExpect(cookie().maxAge("XSRF-TOKEN", 0));
    }

    private static ClientRegistration keycloakRegistration() {
        return ClientRegistration.withRegistrationId("keycloak")
                .clientId("fraud-portal")
                .clientSecret("test-only")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile")
                .authorizationUri("https://identity.example/authorize")
                .tokenUri("https://identity.example/token")
                .jwkSetUri("https://identity.example/certs")
                .userInfoUri("https://identity.example/userinfo")
                .userNameAttributeName("preferred_username")
                .providerConfigurationMetadata(Map.of(
                        "end_session_endpoint", "https://identity.example/logout"))
                .clientName("Keycloak")
                .build();
    }
}
