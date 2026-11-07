package com.staging.sg.fraud.portal.api;

import com.staging.sg.fraud.portal.config.FraudPortalProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FraudPortalSessionControllerTest {
    @Test
    void exposesPlatformAdministratorRoleWithoutLeakingUnknownRoles() {
        FraudPortalProperties properties = new FraudPortalProperties(FraudPortalProperties.Mode.PLATFORM,
                URI.create("https://fraud.example"), "keycloak");
        FraudPortalSessionController controller = new FraudPortalSessionController(properties);
        OidcIdToken token = new OidcIdToken("opaque", Instant.now(), Instant.now().plusSeconds(300), Map.of(
                "sub", "platform-admin", "preferred_username", "platform-admin", "member_id", "FUTURPAYMENT",
                "realm_access", Map.of("roles", List.of("FRAUD_PLATFORM_ADMIN", "UNRELATED_ROLE"))));
        DefaultOidcUser user = new DefaultOidcUser(List.of(), token, "preferred_username");
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(user, List.of(), "keycloak");

        FraudPortalSessionController.CurrentUser current = controller.me(authentication);

        assertThat(current.memberId()).isEqualTo("FUTURPAYMENT");
        assertThat(current.roles()).containsExactly("FRAUD_PLATFORM_ADMIN");
        assertThat(current.mode()).isEqualTo("PLATFORM");
    }
}
