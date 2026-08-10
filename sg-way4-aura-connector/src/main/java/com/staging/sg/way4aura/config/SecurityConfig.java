package com.staging.sg.way4aura.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain way4Security(HttpSecurity http,
            @Value("${way4-aura.security.oauth2.audience:way4-aura-connector}") String audience) throws Exception {
        http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(requests -> requests
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/internal/way4-aura/**").access((authentication, context) -> {
                    var current=authentication.get(); boolean allowed=current instanceof JwtAuthenticationToken jwt
                            && jwt.getToken().getAudience().contains(audience)
                            && current.getAuthorities().stream().anyMatch(a -> "SCOPE_way4.generate".equals(a.getAuthority()));
                    return new AuthorizationDecision(allowed);
                }).anyRequest().denyAll()).oauth2ResourceServer(server -> server.jwt(jwt -> {}));
        return http.build();
    }
    @Bean JwtDecoder way4JwtDecoder(
            @Value("${way4-aura.security.oauth2.issuer-uri:http://127.0.0.1:8090/realms/futurpayment}") String issuer,
            @Value("${way4-aura.security.oauth2.jwk-set-uri:http://127.0.0.1:8090/realms/futurpayment/protocol/openid-connect/certs}") String jwk) {
        NimbusJwtDecoder decoder=NimbusJwtDecoder.withJwkSetUri(jwk).build();
        OAuth2TokenValidator<Jwt> validator=JwtValidators.createDefaultWithIssuer(issuer);
        decoder.setJwtValidator(validator); return decoder;
    }
}
