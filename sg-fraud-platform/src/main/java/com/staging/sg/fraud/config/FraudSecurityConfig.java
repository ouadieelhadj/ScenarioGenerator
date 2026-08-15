package com.staging.sg.fraud.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class FraudSecurityConfig {
    @Bean
    SecurityFilterChain fraudSecurityFilterChain(HttpSecurity http,
            @Value("${fraud.security.allowed-audiences:futurpayment-fraud}") String audienceCsv) throws Exception {
        Set<String> audiences = Set.of(audienceCsv.split(",")).stream().map(String::trim).collect(Collectors.toUnmodifiableSet());
        http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(requests -> requests
                .requestMatchers("/api/fraud/v1/health", "/api/fraud/v1/capabilities").permitAll()
                .requestMatchers("/api/fraud/v1/**").access((authentication, context) -> {
                    var current = authentication.get();
                    boolean allowed = current instanceof JwtAuthenticationToken jwt
                            && jwt.getToken().getAudience().stream().anyMatch(audiences::contains)
                            && current.getAuthorities().stream().anyMatch(a ->
                            a.getAuthority().equals("SCOPE_fraud.read") || a.getAuthority().equals("SCOPE_fraud.write"));
                    return new AuthorizationDecision(allowed);
                }).anyRequest().denyAll())
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> {}));
        return http.build();
    }

    @Bean
    JwtDecoder fraudJwtDecoder(@Value("${fraud.security.issuer-uri}") String issuer,
            @Value("${fraud.security.jwk-set-uri}") String jwkSetUri) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> defaults = JwtValidators.createDefaultWithIssuer(issuer);
        decoder.setJwtValidator(defaults);
        return decoder;
    }
}
