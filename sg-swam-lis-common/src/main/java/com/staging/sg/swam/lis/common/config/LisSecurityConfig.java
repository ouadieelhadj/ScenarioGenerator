package com.staging.sg.swam.lis.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security boundary for clearing services. The clearing API is intended for
 * an internal, network-restricted operational zone; authentication can be
 * enforced by the gateway without Spring Boot's generated interactive login.
 */
@Configuration
public class LisSecurityConfig {

    @Bean
    SecurityFilterChain lisSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/clearing/**").permitAll()
                        .anyRequest().denyAll())
                .build();
    }
}
