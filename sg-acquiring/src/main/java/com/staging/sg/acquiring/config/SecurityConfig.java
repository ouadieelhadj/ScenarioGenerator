package com.staging.sg.acquiring.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {
    @Bean
    SecurityFilterChain acquiringSecurityFilterChain(HttpSecurity http,
            @Value("${acquiring.security.oauth2.audience:futurpayment-acquiring}") String audience) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/api/internal/acquiring/v2/**")
                        .access((authentication, context) -> {
                            var current = authentication.get();
                            boolean allowed = current instanceof JwtAuthenticationToken jwt
                                    && jwt.getToken().getAudience().contains(audience)
                                    && current.getAuthorities().stream().anyMatch(authority ->
                                    "SCOPE_merchant.provision".equals(authority.getAuthority()));
                            return new AuthorizationDecision(allowed);
                        })
                        .anyRequest().permitAll())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt -> {}));
        return http.build();
    }

    @Bean
    JwtDecoder acquiringJwtDecoder(
            @Value("${acquiring.security.oauth2.issuer-uri:http://127.0.0.1:8090/realms/futurpayment}") String issuer,
            @Value("${acquiring.security.oauth2.jwk-set-uri:http://127.0.0.1:8090/realms/futurpayment/protocol/openid-connect/certs}") String jwkSetUri,
            @Value("${acquiring.security.oauth2.audience:futurpayment-acquiring}") String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> defaults = JwtValidators.createDefaultWithIssuer(issuer);
        decoder.setJwtValidator(defaults);
        return decoder;
    }
}
