package com.staging.sg.fraud.portal.api;

import com.staging.sg.fraud.portal.config.FraudPortalProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/fraud-portal/v1")
public class FraudPortalSessionController {
    private static final Set<String> ALLOWED_ROLES = Set.of(
            "FRAUD_ADMIN", "FRAUD_MAKER", "FRAUD_CHECKER", "FRAUD_PLATFORM_ADMIN");
    private final FraudPortalProperties properties;

    public FraudPortalSessionController(FraudPortalProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "component", "FRAUD_PORTAL_BFF", "mode", properties.mode().name());
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of("product", properties.publicProductCode(), "mode", properties.mode().name(),
                "loginUrl", "/oauth2/authorization/" + properties.clientRegistrationId());
    }

    @GetMapping("/me")
    public CurrentUser me(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof OidcUser oidc)) {
            throw new IllegalStateException("OIDC principal required");
        }
        String memberId = requiredClaim(oidc, "member_id");
        Set<String> roles = new LinkedHashSet<>();
        Object access = oidc.getClaim("realm_access");
        if (access instanceof Map<?, ?> map && map.get("roles") instanceof Collection<?> values) {
            values.stream().map(String::valueOf).filter(ALLOWED_ROLES::contains).forEach(roles::add);
        }
        return new CurrentUser(oidc.getPreferredUsername(), memberId, List.copyOf(roles), properties.mode().name());
    }

    private static String requiredClaim(OidcUser user, String name) {
        String value = user.getClaimAsString(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " claim required");
        return value.trim();
    }

    public record CurrentUser(String username, String memberId, List<String> roles, String mode) {}
}
